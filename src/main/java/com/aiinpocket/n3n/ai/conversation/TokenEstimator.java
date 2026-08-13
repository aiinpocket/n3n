package com.aiinpocket.n3n.ai.conversation;

import java.util.List;
import java.util.Map;

/**
 * 粗略但確定性的 token 估算器（無外部依賴）。
 * 規則：CJK 字元（中日韓表意文字、假名、諺文）約 1 token，
 * 其他字元約 4 個字元換 1 token。
 */
public final class TokenEstimator {

    /** 非 CJK 字元換算比例：約 4 字元 = 1 token */
    private static final double LATIN_CHARS_PER_TOKEN = 4.0;

    private TokenEstimator() {
    }

    /**
     * 估算一段文字的 token 數。null 或空字串回傳 0。
     */
    public static int estimate(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int cjkCount = 0;
        int otherCount = 0;
        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            if (isCjk(codePoint)) {
                cjkCount++;
            } else {
                otherCount++;
            }
            i += Character.charCount(codePoint);
        }

        return cjkCount + (int) Math.ceil(otherCount / LATIN_CHARS_PER_TOKEN);
    }

    /**
     * 估算一則訊息（role + content map）的 token 數，
     * 每則訊息額外加 4 token 作為訊息框架開銷。
     */
    public static int estimateMessage(Map<String, Object> message) {
        if (message == null) {
            return 0;
        }
        Object content = message.get("content");
        int contentTokens = content instanceof String s ? estimate(s) : 0;
        return contentTokens + 4;
    }

    /**
     * 估算一串訊息的總 token 數。
     */
    public static int estimateMessages(List<Map<String, Object>> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Map<String, Object> message : messages) {
            total += estimateMessage(message);
        }
        return total;
    }

    private static boolean isCjk(int codePoint) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(codePoint);
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
            || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
            || block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
            || block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS
            || block == Character.UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION
            || block == Character.UnicodeBlock.HIRAGANA
            || block == Character.UnicodeBlock.KATAKANA
            || block == Character.UnicodeBlock.HANGUL_SYLLABLES
            || block == Character.UnicodeBlock.HANGUL_JAMO
            || block == Character.UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS;
    }
}
