package com.aiinpocket.n3n.ai.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Prompt Injection 防護
 *
 * 偵測並消毒使用者輸入，防止 prompt injection 攻擊
 */
@Slf4j
@Component
public class PromptSanitizer {

    @Value("${ai.security.prompt-max-length:4000}")
    private int maxPromptLength;

    @Value("${ai.security.enable-injection-detection:true}")
    private boolean injectionDetectionEnabled;

    /**
     * 危險指令 patterns (不分大小寫)
     */
    private static final List<Pattern> INJECTION_PATTERNS = List.of(
        // 忽略指令類
        Pattern.compile("(?i)ignore\\s+(previous|above|all|prior)\\s+(instructions?|prompts?|messages?|context)"),
        Pattern.compile("(?i)disregard\\s+(previous|above|all|prior|everything)"),
        Pattern.compile("(?i)forget\\s+(previous|above|all|prior|everything)"),

        // 系統提示覆寫類
        Pattern.compile("(?i)you\\s+are\\s+now\\s+(a|an|the)"),
        Pattern.compile("(?i)new\\s+(system\\s+)?instructions?"),
        Pattern.compile("(?i)override\\s+(system|instructions?|prompts?)"),
        Pattern.compile("(?i)act\\s+as\\s+(if|a|an|the)"),
        Pattern.compile("(?i)pretend\\s+(to\\s+be|you\\s+are)"),

        // 角色扮演欺騙類
        Pattern.compile("(?i)roleplay\\s+as"),
        Pattern.compile("(?i)you\\s+must\\s+(obey|follow|comply)"),
        Pattern.compile("(?i)from\\s+now\\s+on"),

        // 資訊洩漏類
        Pattern.compile("(?i)reveal\\s+(your|the)\\s+(system|instructions?|prompts?)"),
        Pattern.compile("(?i)show\\s+(me\\s+)?(your|the)\\s+(system|instructions?|prompts?)"),
        Pattern.compile("(?i)what\\s+are\\s+your\\s+(system|secret|hidden)"),
        Pattern.compile("(?i)print\\s+(your\\s+)?(system|initial)\\s+(prompt|instructions?)"),

        // 特殊字元注入類
        Pattern.compile("\\[\\s*INST\\s*\\]", Pattern.CASE_INSENSITIVE),
        Pattern.compile("\\[\\s*/\\s*INST\\s*\\]", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<<\\s*SYS\\s*>>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<<\\s*/\\s*SYS\\s*>>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<\\|?\\s*system\\s*\\|?>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<\\|?\\s*user\\s*\\|?>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<\\|?\\s*assistant\\s*\\|?>", Pattern.CASE_INSENSITIVE),

        // Jailbreak 企圖
        Pattern.compile("(?i)jailbreak"),
        Pattern.compile("(?i)DAN\\s*(mode)?"),
        Pattern.compile("(?i)do\\s+anything\\s+now")
    );

    /**
     * 需要替換的危險字元或序列
     */
    private static final List<String> DANGEROUS_SEQUENCES = List.of(
        "\u0000", // null byte
        "\u200B", // zero-width space
        "\u200C", // zero-width non-joiner
        "\u200D", // zero-width joiner
        "\uFEFF"  // BOM
    );

    /**
     * 驗證並消毒使用者輸入
     *
     * @param input 原始使用者輸入
     * @return 消毒後的輸入
     * @throws PromptInjectionException 如果偵測到 injection 攻擊
     */
    public String sanitize(String input) {
        if (input == null || input.isEmpty()) {
            return "";
        }

        // 1. 移除危險字元序列
        String sanitized = removeDangerousSequences(input);

        // 2. 長度限制
        if (sanitized.length() > maxPromptLength) {
            log.warn("Prompt exceeds max length {}, truncating", maxPromptLength);
            sanitized = sanitized.substring(0, maxPromptLength);
        }

        // 3. 正規化空白字元 (before injection detection to prevent Unicode bypass)
        sanitized = normalizeWhitespace(sanitized);

        // 4. 偵測 injection patterns (after normalization so Unicode tricks are resolved)
        if (injectionDetectionEnabled) {
            detectInjection(sanitized);
        }

        return sanitized;
    }

    /**
     * 僅驗證輸入是否安全（不修改）
     *
     * @param input 使用者輸入
     * @return 驗證結果
     */
    public ValidationResult validate(String input) {
        if (input == null || input.isEmpty()) {
            return ValidationResult.ok();
        }

        // 長度檢查
        if (input.length() > maxPromptLength) {
            return ValidationResult.rejected("輸入長度超過限制 (" + maxPromptLength + " 字元)");
        }

        // 危險字元檢查
        for (String sequence : DANGEROUS_SEQUENCES) {
            if (input.contains(sequence)) {
                return ValidationResult.rejected("輸入包含不允許的字元");
            }
        }

        // Injection pattern 檢查 (normalize whitespace first to prevent Unicode bypass)
        if (injectionDetectionEnabled) {
            String normalized = normalizeWhitespace(input);
            for (Pattern pattern : INJECTION_PATTERNS) {
                if (pattern.matcher(normalized).find()) {
                    return ValidationResult.rejected("偵測到可能的安全威脅");
                }
            }
        }

        return ValidationResult.ok();
    }

    /**
     * 移除危險字元序列
     */
    private String removeDangerousSequences(String input) {
        String result = input;
        for (String sequence : DANGEROUS_SEQUENCES) {
            result = result.replace(sequence, "");
        }
        return result;
    }

    /**
     * 偵測 injection 攻擊
     */
    private void detectInjection(String input) {
        for (Pattern pattern : INJECTION_PATTERNS) {
            if (pattern.matcher(input).find()) {
                log.warn("Detected potential prompt injection: pattern={}", pattern.pattern());
                throw new PromptInjectionException("Detected potential security threat, please modify your input");
            }
        }
    }

    /**
     * 正規化空白字元（Unicode normalization + 連續空白壓縮為單一空白）
     */
    private String normalizeWhitespace(String input) {
        // Unicode NFC normalization to prevent bypasses via decomposed characters
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFC);
        // Replace all Unicode whitespace categories (\\p{Z}) plus standard \\s
        return normalized.replaceAll("[\\p{Z}\\s]+", " ").trim();
    }

    /**
     * 消毒 AI 輸出（移除可能意外洩漏的敏感資訊）
     *
     * @param output AI 原始輸出
     * @return 消毒後的輸出
     */
    public String sanitizeOutput(String output) {
        if (output == null || output.isEmpty()) {
            return "";
        }

        String sanitized = output;

        // 移除可能的 API key patterns
        sanitized = sanitized.replaceAll("(?i)(sk-|api[_-]?key[=:]\\s*)[a-zA-Z0-9-_]{20,}", "[REDACTED]");

        // 移除可能的 session/token patterns
        sanitized = sanitized.replaceAll("(?i)(session[_-]?id[=:]\\s*|token[=:]\\s*)[a-zA-Z0-9-_]{20,}", "[REDACTED]");

        // 移除 UUID patterns（可能是內部 ID）
        // 注意：這可能過於激進，視需求調整
        // sanitized = sanitized.replaceAll("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}", "[UUID]");

        return sanitized;
    }

    /**
     * 驗證結果
     */
    public record ValidationResult(boolean isSafe, String message) {
        public static ValidationResult ok() {
            return new ValidationResult(true, null);
        }

        public static ValidationResult rejected(String message) {
            return new ValidationResult(false, message);
        }
    }

    /**
     * Prompt Injection 例外
     */
    public static class PromptInjectionException extends RuntimeException {
        public PromptInjectionException(String message) {
            super(message);
        }
    }
}
