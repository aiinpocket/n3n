package com.aiinpocket.n3n.ai.generation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 佔位假值與 enum 修正的判斷邏輯測試。
 *
 * <p>這些規則寧可漏判也不能誤判：誤判會刪掉使用者真正要的設定，
 * 漏判只是回到原本「執行時才發現」的行為。
 */
class GeneratedFlowSanitizerTest {

    private GeneratedFlowSanitizer sanitizer;

    @BeforeEach
    void setUp() {
        sanitizer = new GeneratedFlowSanitizer(null);
    }

    @Test
    @DisplayName("YOUR_SPREADSHEET_ID 這類全大寫底線佔位會被認出來")
    void isPlaceholder_screamingSnakeCase() {
        assertThat(sanitizer.isPlaceholder("spreadsheetId", "YOUR_SPREADSHEET_ID")).isTrue();
        assertThat(sanitizer.isPlaceholder("apiKey", "SOME_API_KEY")).isTrue();
    }

    @Test
    @DisplayName("角括號包住的佔位會被認出來")
    void isPlaceholder_bracketed() {
        assertThat(sanitizer.isPlaceholder("to", "<your-email>")).isTrue();
    }

    @Test
    @DisplayName("example.com 是保留網域，不可能是真正要打的目標")
    void isPlaceholder_exampleDomain() {
        assertThat(sanitizer.isPlaceholder("url", "https://example.com/api")).isTrue();
    }

    @Test
    @DisplayName("選擇器寫成中文描述一定是假值")
    void isPlaceholder_chineseSelector() {
        assertThat(sanitizer.isPlaceholder("selector", "財報連結選擇器")).isTrue();
    }

    @Test
    @DisplayName("真正的 CSS 選擇器不會被誤判")
    void isPlaceholder_realSelectorKept() {
        assertThat(sanitizer.isPlaceholder("selector", "tr.athing")).isFalse();
        assertThat(sanitizer.isPlaceholder("url", "https://news.ycombinator.com")).isFalse();
    }

    @Test
    @DisplayName("中文內容出現在非技術欄位不算佔位")
    void isPlaceholder_chineseContentInNormalFieldKept() {
        assertThat(sanitizer.isPlaceholder("subject", "台積電財報分析")).isFalse();
    }

    @Test
    @DisplayName("表達式是有效值，絕不能被當成佔位清掉")
    void isPlaceholder_expressionKept() {
        assertThat(sanitizer.isPlaceholder("content", "{{ $json.news }}")).isFalse();
    }

    @Test
    @DisplayName("大小寫或底線差異可以唯一對應時直接修正")
    void correctEnumValue_uniqueCaseInsensitiveMatch() {
        Map<String, Object> schema = Map.of("enum", List.of("extractText", "convertToMarkdown"));
        assertThat(sanitizer.correctEnumValue(schema, "extracttext")).isEqualTo("extractText");
        assertThat(sanitizer.correctEnumValue(schema, "convert_to_markdown")).isEqualTo("convertToMarkdown");
    }

    @Test
    @DisplayName("只有一個候選以該值開頭時修正")
    void correctEnumValue_uniquePrefixMatch() {
        Map<String, Object> schema = Map.of("enum", List.of("extractLinks", "sanitize"));
        assertThat(sanitizer.correctEnumValue(schema, "extract")).isEqualTo("extractLinks");
    }

    @Test
    @DisplayName("多個候選時不亂猜，交給實跑與 AI 修復處理")
    void correctEnumValue_ambiguousLeftAlone() {
        Map<String, Object> schema = Map.of("enum",
            List.of("extractText", "extractLinks", "extractBySelector"));
        assertThat(sanitizer.correctEnumValue(schema, "extract")).isNull();
    }

    @Test
    @DisplayName("值本來就合法時不做任何更動")
    void correctEnumValue_validValueUntouched() {
        Map<String, Object> schema = Map.of("enum", List.of("extractText", "sanitize"));
        assertThat(sanitizer.correctEnumValue(schema, "extractText")).isNull();
    }

    @Test
    @DisplayName("沒有 enum 的欄位不受影響")
    void correctEnumValue_noEnum() {
        assertThat(sanitizer.correctEnumValue(Map.of("type", "string"), "anything")).isNull();
    }
}
