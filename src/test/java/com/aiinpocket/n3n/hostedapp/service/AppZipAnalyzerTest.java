package com.aiinpocket.n3n.hostedapp.service;

import com.aiinpocket.n3n.hostedapp.config.HostedAppProperties;
import com.aiinpocket.n3n.hostedapp.dto.AppManifest;
import com.aiinpocket.n3n.hostedapp.dto.ParamSpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * zip 分析：compose 參數萃取（${VAR} / ${VAR:-def} / ${VAR:?} / 空值）、
 * 秘密參數啟發式、compose 安全驗證（privileged / bind mount / host network /
 * 服務數上限）、Dockerfile ENV/EXPOSE/ARG、zip-slip / zip-bomb 防禦。
 */
class AppZipAnalyzerTest {

    private AppZipAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        HostedAppProperties properties = Mockito.mock(HostedAppProperties.class);
        when(properties.maxZipBytes()).thenReturn(10_000L);
        when(properties.getMaxZipMb()).thenReturn(100);
        analyzer = new AppZipAnalyzer(
                new AppZipReader(properties),
                new ComposeManifestParser(),
                new DockerfileManifestParser());
    }

    private byte[] zipOf(Map<String, String> files) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    private ParamSpec param(AppManifest manifest, String name) {
        return manifest.params().stream()
                .filter(p -> p.name().equals(name))
                .findFirst().orElseThrow(() -> new AssertionError("param not found: " + name));
    }

    // ---------- compose ----------

    @Test
    @DisplayName("compose：${VAR} / ${VAR:?} / 空值 為必填，${VAR:-def} 帶預設值")
    void composeParamExtraction() {
        AppManifest manifest = analyzer.analyze(zipOf(Map.of("docker-compose.yml", """
                services:
                  web:
                    image: nginx:1.25
                    ports: ["8080:80"]
                    environment:
                      API_URL: ${API_URL}
                      DB_PASSWORD: ${DB_PASSWORD:?must set}
                      LOG_LEVEL: ${LOG_LEVEL:-info}
                      EMPTY_ONE:
                """)));

        assertThat(manifest.type()).isEqualTo(AppManifest.TYPE_COMPOSE);
        assertThat(param(manifest, "API_URL").required()).isTrue();
        assertThat(param(manifest, "DB_PASSWORD").required()).isTrue();
        assertThat(param(manifest, "LOG_LEVEL").required()).isFalse();
        assertThat(param(manifest, "LOG_LEVEL").defaultValue()).isEqualTo("info");
        assertThat(param(manifest, "EMPTY_ONE").required()).isTrue();
        // web 服務與容器內埠（host 側 8080 忽略，取容器埠 80）
        assertThat(manifest.webService()).isEqualTo("web");
        assertThat(manifest.internalPort()).isEqualTo(80);
    }

    @Test
    @DisplayName("compose：environment 清單形式（KEY=value / KEY）同樣萃取參數")
    void composeListEnvironment() {
        AppManifest manifest = analyzer.analyze(zipOf(Map.of("compose.yml", """
                services:
                  app:
                    image: node:20
                    ports: ["3000"]
                    environment:
                      - PORT=3000
                      - API_TOKEN
                """)));

        assertThat(param(manifest, "API_TOKEN").required()).isTrue();
        assertThat(manifest.params()).extracting(ParamSpec::name).doesNotContain("PORT");
    }

    @Test
    @DisplayName("秘密啟發式：名稱含 PASS/SECRET/TOKEN/KEY 標記為 secret")
    void secretHeuristic() {
        AppManifest manifest = analyzer.analyze(zipOf(Map.of("docker-compose.yml", """
                services:
                  app:
                    image: app:1
                    environment:
                      DB_PASSWORD: ${DB_PASSWORD}
                      JWT_SECRET: ${JWT_SECRET}
                      API_TOKEN: ${API_TOKEN}
                      LICENSE_KEY: ${LICENSE_KEY}
                      SITE_NAME: ${SITE_NAME}
                """)));

        assertThat(param(manifest, "DB_PASSWORD").secret()).isTrue();
        assertThat(param(manifest, "JWT_SECRET").secret()).isTrue();
        assertThat(param(manifest, "API_TOKEN").secret()).isTrue();
        assertThat(param(manifest, "LICENSE_KEY").secret()).isTrue();
        assertThat(param(manifest, "SITE_NAME").secret()).isFalse();
    }

    @Test
    @DisplayName("compose 安全驗證：privileged / bind mount / network_mode / cap_add 一律拒絕")
    void composeSecurityRejections() {
        Map<String, String> cases = Map.of(
                "privileged", """
                        services:
                          bad: {image: x, privileged: true}
                        """,
                "bind mount", """
                        services:
                          bad:
                            image: x
                            volumes: ["/var/run/docker.sock:/var/run/docker.sock"]
                        """,
                "relative bind", """
                        services:
                          bad:
                            image: x
                            volumes: ["./data:/data"]
                        """,
                "long-syntax bind", """
                        services:
                          bad:
                            image: x
                            volumes:
                              - {type: bind, source: /etc, target: /host-etc}
                        """,
                "host network", """
                        services:
                          bad: {image: x, network_mode: host}
                        """,
                "cap_add", """
                        services:
                          bad:
                            image: x
                            cap_add: [SYS_ADMIN]
                        """,
                "devices", """
                        services:
                          bad:
                            image: x
                            devices: ["/dev/kvm:/dev/kvm"]
                        """,
                "pid host", """
                        services:
                          bad: {image: x, pid: host}
                        """);

        for (Map.Entry<String, String> entry : cases.entrySet()) {
            byte[] zip = zipOf(Map.of("docker-compose.yml", entry.getValue()));
            assertThatThrownBy(() -> analyzer.analyze(zip))
                    .as("case: " + entry.getKey())
                    .isInstanceOf(IllegalArgumentException.class);
        }
        // named volume 是允許的
        AppManifest ok = analyzer.analyze(zipOf(Map.of("docker-compose.yml", """
                services:
                  db:
                    image: postgres:16
                    volumes: ["pgdata:/var/lib/postgresql/data"]
                """)));
        assertThat(ok.services()).hasSize(1);
    }

    @Test
    @DisplayName("compose：超過 4 個服務拒絕")
    void composeTooManyServices() {
        byte[] zip = zipOf(Map.of("docker-compose.yml", """
                services:
                  s1: {image: a}
                  s2: {image: b}
                  s3: {image: c}
                  s4: {image: d}
                  s5: {image: e}
                """));
        assertThatThrownBy(() -> analyzer.analyze(zip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("服務數量");
    }

    @Test
    @DisplayName("compose：build 服務須在 context 內有 Dockerfile")
    void composeBuildContextValidated() {
        byte[] missing = zipOf(Map.of("docker-compose.yml", """
                services:
                  app: {build: ./backend}
                """));
        assertThatThrownBy(() -> analyzer.analyze(missing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dockerfile");

        AppManifest ok = analyzer.analyze(zipOf(Map.of(
                "docker-compose.yml", """
                        services:
                          app:
                            build: ./backend
                            ports: ["8000"]
                        """,
                "backend/Dockerfile", "FROM python:3.12\nEXPOSE 8000\n")));
        assertThat(ok.services().get(0).build()).isEqualTo("backend");
    }

    // ---------- Dockerfile ----------

    @Test
    @DisplayName("Dockerfile：ENV 為含預設參數、ARG 無預設即必填、EXPOSE 決定內埠")
    void dockerfileParsing() {
        AppManifest manifest = analyzer.analyze(zipOf(Map.of("Dockerfile", """
                FROM node:20-alpine
                ARG BUILD_MODE
                ARG BASE_PATH=/app
                ENV PORT=3000 LOG_LEVEL=info
                ENV APP_SECRET ""
                EXPOSE 3000/tcp 9229
                CMD ["node", "server.js"]
                """)));

        assertThat(manifest.type()).isEqualTo(AppManifest.TYPE_DOCKERFILE);
        assertThat(manifest.webService()).isEqualTo("app");
        assertThat(manifest.internalPort()).isEqualTo(3000);
        assertThat(param(manifest, "BUILD_MODE").required()).isTrue();
        assertThat(param(manifest, "BASE_PATH").required()).isFalse();
        assertThat(param(manifest, "BASE_PATH").defaultValue()).isEqualTo("/app");
        assertThat(param(manifest, "PORT").defaultValue()).isEqualTo("3000");
        assertThat(param(manifest, "LOG_LEVEL").defaultValue()).isEqualTo("info");
        assertThat(param(manifest, "APP_SECRET").secret()).isTrue();
    }

    @Test
    @DisplayName("Dockerfile：無 EXPOSE 時內埠預設 8080")
    void dockerfileDefaultPort() {
        AppManifest manifest = analyzer.analyze(zipOf(Map.of(
                "Dockerfile", "FROM alpine\nCMD [\"sh\"]\n")));
        assertThat(manifest.internalPort()).isEqualTo(8080);
    }

    // ---------- 型態偵測與 zip 防禦 ----------

    @Test
    @DisplayName("無 compose 也無 Dockerfile：拒絕並指引改用小站台")
    void staticZipRejected() {
        byte[] zip = zipOf(Map.of("index.html", "<html></html>"));
        assertThatThrownBy(() -> analyzer.analyze(zip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("小站台");
    }

    @Test
    @DisplayName("zip-slip：.. 段 / 絕對路徑 / 反斜線一律拒絕")
    void zipSlipRejected() {
        for (String evil : List.of("../evil", "/abs", "a\\b", "a/../../x", "./sneaky")) {
            byte[] zip = zipOf(new LinkedHashMap<>(Map.of(evil, "x")));
            assertThatThrownBy(() -> analyzer.analyze(zip))
                    .as("entry: " + evil)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("zip bomb：解壓後超過上限（zip 上限 x 擴張倍數）即中止")
    void zipBombRejected() {
        // maxZipBytes=10000，擴張上限 50000；壓縮率極高的重複字元會超量
        byte[] zip = zipOf(Map.of(
                "Dockerfile", "FROM alpine\n",
                "big.txt", "a".repeat(60_000)));
        assertThatThrownBy(() -> analyzer.analyze(zip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("zip bomb");
    }

    @Test
    @DisplayName("zip 檔本身超過上限即拒絕")
    void oversizedZipRejected() {
        byte[] fake = new byte[10_001];
        assertThatThrownBy(() -> analyzer.analyze(fake))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("過大");
    }
}
