package com.aiinpocket.n3n.site.service;

import com.aiinpocket.n3n.site.dto.SiteFileUpsertEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * zip 上傳解析：合法解包、zip-slip 防護、zip bomb（大小/數量上限）、
 * 副檔名白名單。
 */
class SiteZipServiceTest {

    private SiteZipService service;

    @BeforeEach
    void setUp() {
        service = new SiteZipService();
        ReflectionTestUtils.setField(service, "maxFilesPerSite", 5);
        ReflectionTestUtils.setField(service, "maxFileBytes", 100L);
        ReflectionTestUtils.setField(service, "maxSiteBytes", 300L);
    }

    private InputStream zipOf(Map<String, byte[]> files) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Map.Entry<String, byte[]> file : files.entrySet()) {
                zip.putNextEntry(new ZipEntry(file.getKey()));
                zip.write(file.getValue());
                zip.closeEntry();
            }
        }
        return new ByteArrayInputStream(out.toByteArray());
    }

    private static byte[] bytes(String content) {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("合法 zip：解出全部檔案，內容以 base64 攜帶，目錄項目略過")
    void validZipParsed() throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("index.html", bytes("<html>hi</html>"));
        files.put("assets/", new byte[0]); // 目錄
        files.put("assets/style.css", bytes("body{}"));

        List<SiteFileUpsertEntry> entries = service.parse(zipOf(files));

        assertThat(entries).extracting(SiteFileUpsertEntry::getPath)
                .containsExactly("index.html", "assets/style.css");
        assertThat(new String(
                Base64.getDecoder().decode(entries.get(0).getContentBase64()),
                StandardCharsets.UTF_8)).isEqualTo("<html>hi</html>");
    }

    @Test
    @DisplayName("zip-slip：.. 段、絕對路徑、反斜線一律拒絕")
    void zipSlipRejected() throws IOException {
        List<String> evil = List.of(
                "../evil.html",
                "a/../../evil.html",
                "/absolute.html",
                "a\\b.html",
                "./sneaky.html"
        );
        for (String name : evil) {
            InputStream zip = zipOf(Map.of(name, bytes("x")));
            assertThatThrownBy(() -> service.parse(zip))
                    .as("entry: " + name)
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    @DisplayName("單檔超過上限即中止（實際位元組計數，不信任宣告值）")
    void oversizedEntryRejected() throws IOException {
        InputStream zip = zipOf(Map.of("big.txt", bytes("x".repeat(101))));

        assertThatThrownBy(() -> service.parse(zip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("too large");
    }

    @Test
    @DisplayName("全部檔案合計超過全站上限即中止")
    void totalSizeLimitEnforced() throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("a.txt", bytes("x".repeat(100)));
        files.put("b.txt", bytes("y".repeat(100)));
        files.put("c.txt", bytes("z".repeat(100)));
        files.put("d.txt", bytes("w".repeat(10)));

        assertThatThrownBy(() -> service.parse(zipOf(files)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("site size limit");
    }

    @Test
    @DisplayName("檔案數超過上限即中止")
    void entryCountLimitEnforced() throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        for (int i = 0; i < 6; i++) {
            files.put("f" + i + ".txt", bytes("x"));
        }

        assertThatThrownBy(() -> service.parse(zipOf(files)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Too many files");
    }

    @Test
    @DisplayName("副檔名白名單：不支援的類型拒絕")
    void badExtensionRejected() throws IOException {
        for (String name : List.of("run.sh", "app.exe", "page.php")) {
            InputStream zip = zipOf(Map.of(name, bytes("x")));
            assertThatThrownBy(() -> service.parse(zip))
                    .as("entry: " + name)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("extension");
        }
    }

    @Test
    @DisplayName("空 zip 拒絕")
    void emptyZipRejected() throws IOException {
        InputStream zip = zipOf(Map.of());

        assertThatThrownBy(() -> service.parse(zip))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no site files");
    }
}
