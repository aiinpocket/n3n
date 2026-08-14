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
}
