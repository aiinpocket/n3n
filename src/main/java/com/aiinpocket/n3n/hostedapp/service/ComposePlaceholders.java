package com.aiinpocket.n3n.hostedapp.service;

import com.aiinpocket.n3n.hostedapp.dto.ParamSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * compose 環境變數佔位符的解析與代換。
 *
 * 支援的形式（compose spec 的 variable interpolation 子集）：
 *   ${VAR}        → 必填參數
 *   ${VAR:?msg}   → 必填參數（錯誤訊息忽略）
 *   ${VAR?msg}    → 必填參數
 *   ${VAR:-def}   → 有預設值的參數
 *   ${VAR-def}    → 有預設值的參數
 */
final class ComposePlaceholders {

    private static final Pattern PLACEHOLDER = Pattern.compile(
            "\\$\\{([A-Za-z_][A-Za-z0-9_]*)(:-|:\\?|-|\\?)?([^}]*)\\}");

    private ComposePlaceholders() {
    }

    /** 從單一值中取出全部佔位符對應的參數定義（依出現順序） */
    static List<ParamSpec> extract(String value) {
        List<ParamSpec> params = new ArrayList<>();
        if (value == null) {
            return params;
        }
        Matcher matcher = PLACEHOLDER.matcher(value);
        while (matcher.find()) {
            String name = matcher.group(1);
            String operator = matcher.group(2);
            String operand = matcher.group(3);
            boolean hasDefault = ":-".equals(operator) || "-".equals(operator);
            params.add(ParamSpec.builder()
                    .name(name)
                    .defaultValue(hasDefault ? operand : null)
                    .required(!hasDefault)
                    .secret(ParamSpec.isSecretName(name))
                    .build());
        }
        return params;
    }

    /**
     * 以使用者參數值代換佔位符；未提供時採 ${VAR:-def} 的預設值，
     * 兩者皆無則代換為空字串（必填檢查已於部署前完成）。
     */
    static String substitute(String value, Map<String, String> params) {
        if (value == null) {
            return null;
        }
        Matcher matcher = PLACEHOLDER.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String operator = matcher.group(2);
            String operand = matcher.group(3);
            String replacement = params.get(name);
            if (replacement == null && (":-".equals(operator) || "-".equals(operator))) {
                replacement = operand;
            }
            matcher.appendReplacement(sb,
                    Matcher.quoteReplacement(replacement == null ? "" : replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
