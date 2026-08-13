package com.aiinpocket.n3n.hostedapp.service;

import com.aiinpocket.n3n.hostedapp.dto.AppManifest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Hosted App zip 分析入口：安全解包（AppZipReader）後偵測應用型態並
 * 委派對應 parser 產生 AppManifest。
 *
 * 偵測順序：根目錄 compose 檔（docker-compose.yml/yaml、compose.yml/yaml）
 * → 根目錄 Dockerfile → 皆無則拒絕（純靜態網站請走「小站台」）。
 */
@Service
@RequiredArgsConstructor
public class AppZipAnalyzer {

    static final List<String> COMPOSE_FILES = List.of(
            "docker-compose.yml", "docker-compose.yaml", "compose.yml", "compose.yaml");
    static final String DOCKERFILE = "Dockerfile";

    private final AppZipReader zipReader;
    private final ComposeManifestParser composeParser;
    private final DockerfileManifestParser dockerfileParser;

    /** 解析 zip → manifest（不落地任何檔案） */
    public AppManifest analyze(byte[] zipBytes) {
        Map<String, byte[]> files = zipReader.read(zipBytes);
        return analyze(files);
    }

    /** 已解包內容的分析（部署時重用，避免重複解壓驗證邏輯分岔） */
    public AppManifest analyze(Map<String, byte[]> files) {
        for (String composeFile : COMPOSE_FILES) {
            byte[] content = files.get(composeFile);
            if (content != null) {
                return composeParser.parse(new String(content, StandardCharsets.UTF_8), files);
            }
        }
        byte[] dockerfile = files.get(DOCKERFILE);
        if (dockerfile != null) {
            return dockerfileParser.parse(new String(dockerfile, StandardCharsets.UTF_8));
        }
        throw new IllegalArgumentException(
                "zip 根目錄找不到 docker-compose.yml 或 Dockerfile。"
                        + "若這是純靜態網站，請改用「小站台」功能上傳。");
    }
}
