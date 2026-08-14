package com.aiinpocket.n3n.execution.handler.handlers.scripting;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JavaScriptEngineTest {

    private JavaScriptEngine engine;

    @BeforeEach
    void setUp() {
        engine = new JavaScriptEngine();
    }

    @Test
    @DisplayName("$input 直接取屬性（既有行為）")
    void execute_directPropertyAccess() throws Exception {
        ScriptResult result = engine.execute(
            "return { doubled: $input.value * 2 };",
            Map.of("value", 21), 10000);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsEntry("doubled", 42);
    }

    @Test
    @DisplayName("n8n 風格 $input.item(n)：merged 內以節點 id 為 key 時依數字排序")
    void execute_itemAccess_onMergedNumericKeys() throws Exception {
        Map<String, Object> merged = new LinkedHashMap<>();
        merged.put("9", Map.of("name", "d"));
        merged.put("3", Map.of("name", "a"));
        merged.put("7", Map.of("name", "c"));
        merged.put("5", Map.of("name", "b"));

        ScriptResult result = engine.execute(
            "return { first: $input.item(0).name, last: $input.item(3).name };",
            Map.of("merged", merged), 10000);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsEntry("first", "a").containsEntry("last", "d");
    }

    @Test
    @DisplayName("n8n 風格 $input.first().json.field 可讀單一物件輸入")
    void execute_firstJson_onPlainObject() throws Exception {
        ScriptResult result = engine.execute(
            "return { r: $input.first().json.response };",
            Map.of("response", "hello"), 10000);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsEntry("r", "hello");
    }

    @Test
    @DisplayName("merged 為 list 時 item/all 對應清單元素")
    void execute_items_onMergedList() throws Exception {
        ScriptResult result = engine.execute(
            "return { count: $input.all().length, second: $input.item(1).v };",
            Map.of("merged", List.of(Map.of("v", 1), Map.of("v", 2))), 10000);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData()).containsEntry("count", 2).containsEntry("second", 2);
    }

    @Test
    @DisplayName("執行錯誤時錯誤訊息帶出具體原因")
    void execute_error_containsDetail() throws Exception {
        ScriptResult result = engine.execute(
            "return $input.nonExistent.deeply.nested;",
            Map.of("value", 1), 10000);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorMessage()).contains("Script execution failed:");
    }
}
