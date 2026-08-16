package com.aiinpocket.n3n.execution.expression;

import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class N3nExpressionEvaluatorTest {

    private N3nExpressionEvaluator evaluator;
    private NodeExecutionContext context;

    @BeforeEach
    void setUp() {
        evaluator = new N3nExpressionEvaluator();
        Map<String, Object> falOutput = Map.of(
            "images", List.of("https://fal.media/files/a.png", "https://fal.media/files/b.png"),
            "imageUrl", "https://fal.media/files/a.png",
            "artifactId", "aaaa-bbbb"
        );
        context = NodeExecutionContext.builder()
            .executionId(UUID.randomUUID())
            .nodeId("3")
            .nodeType("saveArtifact")
            .nodeConfig(Map.of())
            .inputData(Map.of("foo", "bar"))
            .previousOutputs(Map.of("2", falOutput))
            .build();
    }

    @Test
    @DisplayName("$node[\"id\"].json.field 標準語法可取上游輸出")
    void evaluate_standardNodeRef_resolvesField() {
        Object value = evaluator.evaluate("{{$node[\"2\"].json.imageUrl}}", context);
        assertThat(value).isEqualTo("https://fal.media/files/a.png");
    }

    @Test
    @DisplayName("$node2.output.field AI 常見寫法可取上游輸出")
    void evaluate_aiStyleNodeRef_resolvesField() {
        Object value = evaluator.evaluate("{{$node2.output.imageUrl}}", context);
        assertThat(value).isEqualTo("https://fal.media/files/a.png");
    }

    @Test
    @DisplayName("$node2.json.field 亦可取上游輸出")
    void evaluate_aiStyleJsonPrefix_resolvesField() {
        Object value = evaluator.evaluate("{{$node2.json.artifactId}}", context);
        assertThat(value).isEqualTo("aaaa-bbbb");
    }

    @Test
    @DisplayName("陣列索引：$node2.output.images[0] 回傳第一個 URL")
    void evaluate_arrayIndex_returnsElement() {
        Object value = evaluator.evaluate("{{$node2.output.images[0]}}", context);
        assertThat(value).isEqualTo("https://fal.media/files/a.png");
    }

    @Test
    @DisplayName("對 URL 字串取 .url（n8n 風格 images[0].url）寬容回傳字串本身")
    void evaluate_urlPropertyOnString_returnsString() {
        Object value = evaluator.evaluate("{{$node2.output.images[0].url}}", context);
        assertThat(value).isEqualTo("https://fal.media/files/a.png");
    }

    @Test
    @DisplayName("$node2.output（無欄位）回傳整個輸出 map")
    void evaluate_nodeOutputRoot_returnsMap() {
        Object value = evaluator.evaluate("{{$node2.output}}", context);
        assertThat(value).isInstanceOf(Map.class);
    }

    @Test
    @DisplayName("未知節點回傳 null")
    void evaluate_unknownNode_returnsNull() {
        Object value = evaluator.evaluate("{{$node9.output.imageUrl}}", context);
        assertThat(value).isNull();
    }

    @Test
    @DisplayName("對字串取其他屬性（非 url/href）仍回傳 null")
    void evaluate_otherPropertyOnString_returnsNull() {
        Object value = evaluator.evaluate("{{$node2.output.images[0].size}}", context);
        assertThat(value).isNull();
    }

    @Test
    @DisplayName("$json.field 既有行為不受影響")
    void evaluate_jsonField_stillWorks() {
        Object value = evaluator.evaluate("{{$json.foo}}", context);
        assertThat(value).isEqualTo("bar");
    }

    @Test
    @DisplayName("$now.format('YYYY-MM-DD') 回傳當天日期字串")
    void evaluate_nowFormat_returnsFormattedDate() {
        Object value = evaluator.evaluate("{{$now.format('YYYY-MM-DD')}}", context);
        assertThat(value).isInstanceOf(String.class);
        assertThat((String) value).matches("\\d{4}-\\d{2}-\\d{2}");
    }

    @Test
    @DisplayName("範本中的 $now.format 也會被替換而非留空")
    void evaluateTemplate_nowFormat_interpolates() {
        String result = evaluator.evaluateTemplate("報告_{{$now.format('YYYY-MM-DD')}}.md", context);
        assertThat(result).matches("報告_\\d{4}-\\d{2}-\\d{2}\\.md");
    }

    @Test
    @DisplayName("$today 回傳 YYYY-MM-DD")
    void evaluate_today_returnsDate() {
        Object value = evaluator.evaluate("{{$today}}", context);
        assertThat((String) value).matches("\\d{4}-\\d{2}-\\d{2}");
    }

    @Test
    @DisplayName("單引號節點引用 $node['id'] 也能解析")
    void evaluate_singleQuotedNodeRef_resolvesField() {
        Object value = evaluator.evaluate("{{$node['2'].json.imageUrl}}", context);
        assertThat(value).isEqualTo("https://fal.media/files/a.png");
    }

    @Test
    @DisplayName("$node[...].output 與 .json 等價")
    void evaluate_nodeRefOutputAlias_resolvesField() {
        Object value = evaluator.evaluate("{{$node['2'].output.imageUrl}}", context);
        assertThat(value).isEqualTo("https://fal.media/files/a.png");
    }

    @Test
    @DisplayName("n8n 風格的 ={{ }} 前綴會被剝除而非當成字面字串")
    void evaluate_n8nEqualsPrefix_resolvesField() {
        Object value = evaluator.evaluate("={{$json.foo}}", context);
        assertThat(value).isEqualTo("bar");
    }

    @Test
    @DisplayName("範本的 ={{ }} 前綴同樣會被剝除並代入值")
    void evaluateTemplate_n8nEqualsPrefix_interpolates() {
        String result = evaluator.evaluateTemplate("={{$json.foo}}-suffix", context);
        assertThat(result).isEqualTo("bar-suffix");
    }

    @Test
    @DisplayName("不是 ={{ 開頭的等號字串不受影響（例如純文字設定值）")
    void evaluateTemplate_plainEquals_isUntouched() {
        String result = evaluator.evaluateTemplate("=not an expression", context);
        assertThat(result).isEqualTo("=not an expression");
    }

    @Test
    @DisplayName("範本前後空白不會被吃掉")
    void evaluateTemplate_preservesSurroundingWhitespace() {
        String result = evaluator.evaluateTemplate("  {{$json.foo}}  ", context);
        assertThat(result).isEqualTo("  bar  ");
    }

    @Test
    @DisplayName("$now.quarter() 回傳 1-4，模型不必再自己算 Math.ceil(month/3)")
    void evaluate_nowQuarter_returnsQuarterNumber() {
        Object value = evaluator.evaluate("{{$now.quarter()}}", context);
        assertThat(value).isInstanceOf(Integer.class);
        assertThat((Integer) value).isBetween(1, 4);
    }

    @Test
    @DisplayName("$now.year()/month()/day() 回傳合理範圍的數字")
    void evaluate_nowParts_returnNumbers() {
        assertThat((Integer) evaluator.evaluate("{{$now.year()}}", context)).isGreaterThan(2000);
        assertThat((Integer) evaluator.evaluate("{{$now.month()}}", context)).isBetween(1, 12);
        assertThat((Integer) evaluator.evaluate("{{$now.day()}}", context)).isBetween(1, 31);
    }

    @Test
    @DisplayName("不支援的算式保留原文，不再靜默變成空字串")
    void evaluateTemplate_unsupportedArithmeticKeptVisible() {
        String result = evaluator.evaluateTemplate("Q{{ Math.ceil($now.month() / 3) }}報告", context);
        assertThat(result).isEqualTo("Q{{ Math.ceil($now.month() / 3) }}報告");
    }

    @Test
    @DisplayName("$json 取不到值時仍插入空字串（選填欄位常見，不製造雜訊）")
    void evaluateTemplate_knownExpressionMissingValueStaysEmpty() {
        String result = evaluator.evaluateTemplate("[{{$json.notThere}}]", context);
        assertThat(result).isEqualTo("[]");
    }

    @Test
    @DisplayName("$node[...] 引用不到資料時保留原文，避免模型對著空指令自由發揮")
    void evaluateTemplate_missingNodeRefKeptVisible() {
        String result = evaluator.evaluateTemplate("請分析：{{$node[\"99\"].json.data}}", context);
        assertThat(result).isEqualTo("請分析：{{$node[\"99\"].json.data}}");
    }

    @Test
    @DisplayName("$node[...] 取得到資料時正常代入，不受上面規則影響")
    void evaluateTemplate_existingNodeRefStillInterpolates() {
        String result = evaluator.evaluateTemplate("圖：{{$node[\"2\"].json.imageUrl}}", context);
        assertThat(result).isEqualTo("圖：https://fal.media/files/a.png");
    }
}
