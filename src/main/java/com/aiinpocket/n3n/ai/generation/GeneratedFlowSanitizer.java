package com.aiinpocket.n3n.ai.generation;

import com.aiinpocket.n3n.execution.handler.NodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeHandlerRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 生成後的節點設定清理。
 *
 * <p>模型即使被明確要求，偶爾仍會交出 {@code YOUR_SPREADSHEET_ID}、
 * {@code 財報連結選擇器} 這種自己編的佔位值，或漏填 schema 標為必填的欄位。
 * 這兩種情況的共同後果是：流程「看起來建好了」，執行時才失敗，而使用者
 * ——通常不會寫程式——完全看不出是哪裡不對。
 *
 * <p>這裡在流程回到前端之前做兩件事：
 * <ul>
 *   <li>把佔位假值清空。清空後該欄位變成「缺必填」，會被既有的背景驗證流程
 *       抓出來並用白話向使用者詢問，好過留著假值假裝成功。</li>
 *   <li>用 schema 的 default 補上漏填的必填欄位。</li>
 * </ul>
 *
 * <p>刻意不刪除 schema 未定義的多餘欄位：節點執行時本來就會忽略它們，
 * 誤刪反而可能砍掉動態 resource/operation 節點真正需要的設定。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GeneratedFlowSanitizer {

    /** 明顯是模型自己編出來的佔位字樣（不分大小寫比對）。 */
    private static final List<String> PLACEHOLDER_MARKERS = List.of(
        "your_", "your-", "yourname", "your api", "your account",
        "changeme", "change_me", "placeholder", "to_be_filled",
        "xxxxx", "tbd", "fill_me", "fillme", "insert_", "replace_with",
        "請填入", "請輸入", "填入你的", "填入您的", "此處填", "待填",
        "範例值", "範例網址"
    );

    /** 整個值就等於這些字時視為佔位（避免把正常內容中的片語誤判）。 */
    private static final Set<String> PLACEHOLDER_EXACT = Set.of(
        "xxx", "todo", "tbd", "n/a", "na", "none", "null", "undefined",
        "string", "value", "example", "test", "foo", "bar"
    );

    /** YOUR_SPREADSHEET_ID / SOME_API_KEY 這類全大寫底線佔位。 */
    private static final Pattern SCREAMING_PLACEHOLDER =
        Pattern.compile("^[A-Z][A-Z0-9]*(_[A-Z0-9]+)+$");

    /** &lt;your-email&gt; / {your_token} 這類角括號、單層大括號包住的佔位。 */
    private static final Pattern BRACKETED_PLACEHOLDER =
        Pattern.compile("^\\s*[<\\[]\\s*[^>\\]]+\\s*[>\\]]\\s*$");

    /** example.com 家族：RFC 2606 保留網域，一定不是使用者真正要打的目標。 */
    private static final Pattern EXAMPLE_DOMAIN =
        Pattern.compile("(^|[./@])example\\.(com|org|net)(/|$|\\b)", Pattern.CASE_INSENSITIVE);

    /** CJK 字元：出現在選擇器／URL 這種純技術欄位就代表是中文描述，不是真值。 */
    private static final Pattern CJK = Pattern.compile("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}]");

    /** 值必須是機器可解析、不可能寫成自然語言描述的欄位。 */
    private static final Set<String> TECHNICAL_VALUE_KEYS = Set.of(
        "selector", "cssselector", "xpath", "url", "endpoint", "webhookurl",
        "spreadsheetid", "documentid", "fileid", "channelid", "apikey", "token"
    );

    /** 本平台的表達式，是有效值而非佔位，絕不能被清掉。 */
    private static final Pattern EXPRESSION = Pattern.compile("\\{\\{.*}}", Pattern.DOTALL);

    private final NodeHandlerRegistry handlerRegistry;

    /**
     * 單一節點的清理結果。
     *
     * @param nodeId          節點 id
     * @param label           節點標籤（給使用者看的名稱）
     * @param clearedKeys     被清空的佔位欄位
     * @param filledDefaults  用 schema 預設值補上的必填欄位
     * @param missingRequired 清理後仍然缺少、且沒有預設值可補的必填欄位
     */
    public record NodeSanitizeReport(String nodeId, String label, List<String> clearedKeys,
                                     List<String> filledDefaults, List<String> missingRequired) {
        public boolean isClean() {
            return clearedKeys.isEmpty() && filledDefaults.isEmpty() && missingRequired.isEmpty();
        }
    }

    /**
     * 就地清理節點設定，回傳每個被動到的節點的報告。
     *
     * @param nodes 生成出來的節點（元素為含 "type" 與 "config" 的 map）
     * @return 有變動或仍缺必填的節點報告；完全乾淨的節點不會出現在結果中
     */
    public List<NodeSanitizeReport> sanitize(List<Map<String, Object>> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }

        List<NodeSanitizeReport> reports = new ArrayList<>();
        for (Map<String, Object> node : nodes) {
            try {
                NodeSanitizeReport report = sanitizeNode(node);
                if (report != null && !report.isClean()) {
                    reports.add(report);
                }
            } catch (Exception e) {
                // 清理是加分項，不該讓整個生成失敗
                log.warn("Failed to sanitize node {}: {}", node.get("id"), e.getMessage());
            }
        }
        return Collections.unmodifiableList(reports);
    }

    @SuppressWarnings("unchecked")
    private NodeSanitizeReport sanitizeNode(Map<String, Object> node) {
        Object rawConfig = node.get("config");
        if (!(rawConfig instanceof Map)) {
            return null;
        }
        Map<String, Object> config = (Map<String, Object>) rawConfig;
        String type = String.valueOf(node.get("type"));

        Map<String, Object> properties = schemaProperties(type);
        Set<String> required = schemaRequired(type);

        List<String> cleared = new ArrayList<>();
        for (Map.Entry<String, Object> entry : new LinkedHashMap<>(config).entrySet()) {
            if (entry.getValue() instanceof String value && isPlaceholder(entry.getKey(), value)) {
                config.remove(entry.getKey());
                cleared.add(entry.getKey());
            }
        }

        List<String> filled = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String requiredKey : required) {
            if (hasUsableValue(config.get(requiredKey))) {
                continue;
            }
            Object defaultValue = defaultValueFor(properties.get(requiredKey));
            if (defaultValue != null) {
                config.put(requiredKey, defaultValue);
                filled.add(requiredKey);
            } else {
                missing.add(requiredKey);
            }
        }

        if (!cleared.isEmpty()) {
            log.info("Sanitized placeholder values on node {} ({}): {}", node.get("id"), type, cleared);
        }

        return new NodeSanitizeReport(
            String.valueOf(node.get("id")),
            String.valueOf(node.getOrDefault("label", type)),
            List.copyOf(cleared), List.copyOf(filled), List.copyOf(missing));
    }

    /**
     * 判斷一個設定值是不是模型編出來的佔位。
     *
     * <p>寧可漏判也不要誤判：誤判會刪掉使用者真正要的值，漏判只是回到原本的行為。
     */
    boolean isPlaceholder(String key, String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String trimmed = value.trim();

        // 表達式是有效值
        if (EXPRESSION.matcher(trimmed).find()) {
            return false;
        }

        String lower = trimmed.toLowerCase(Locale.ROOT);

        if (PLACEHOLDER_EXACT.contains(lower)) {
            return true;
        }
        if (BRACKETED_PLACEHOLDER.matcher(trimmed).matches()) {
            return true;
        }
        if (SCREAMING_PLACEHOLDER.matcher(trimmed).matches()) {
            return true;
        }
        if (EXAMPLE_DOMAIN.matcher(trimmed).find()) {
            return true;
        }
        for (String marker : PLACEHOLDER_MARKERS) {
            if (lower.contains(marker)) {
                return true;
            }
        }
        // 純技術欄位寫成中文／日文描述（例如 selector = "財報連結選擇器"）一定不是真值
        return isTechnicalKey(key) && CJK.matcher(trimmed).find();
    }

    private boolean isTechnicalKey(String key) {
        if (key == null) {
            return false;
        }
        return TECHNICAL_VALUE_KEYS.contains(key.toLowerCase(Locale.ROOT).replace("_", ""));
    }

    private boolean hasUsableValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String s) {
            return !s.isBlank();
        }
        if (value instanceof Map<?, ?> m) {
            return !m.isEmpty();
        }
        if (value instanceof List<?> l) {
            return !l.isEmpty();
        }
        return true;
    }

    /** schema 的 default；沒有 default 但 enum 只有唯一選項時，那個選項就是唯一正解。 */
    @SuppressWarnings("unchecked")
    private Object defaultValueFor(Object propertySchema) {
        if (!(propertySchema instanceof Map)) {
            return null;
        }
        Map<String, Object> schema = (Map<String, Object>) propertySchema;
        Object explicitDefault = schema.get("default");
        if (explicitDefault != null) {
            return explicitDefault;
        }
        if (schema.get("enum") instanceof List<?> values && values.size() == 1) {
            return values.get(0);
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> schemaProperties(String type) {
        Map<String, Object> schema = configSchema(type);
        return schema.get("properties") instanceof Map
            ? (Map<String, Object>) schema.get("properties") : Map.of();
    }

    private Set<String> schemaRequired(String type) {
        Map<String, Object> schema = configSchema(type);
        if (!(schema.get("required") instanceof List<?> required)) {
            return Set.of();
        }
        return required.stream().map(String::valueOf).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private Map<String, Object> configSchema(String type) {
        try {
            NodeHandler handler = handlerRegistry.findHandler(type).orElse(null);
            if (handler == null) {
                return Map.of();
            }
            Map<String, Object> schema = handler.getConfigSchema();
            return schema != null ? schema : Map.of();
        } catch (Exception e) {
            log.debug("No usable config schema for node type {}: {}", type, e.getMessage());
            return Map.of();
        }
    }
}
