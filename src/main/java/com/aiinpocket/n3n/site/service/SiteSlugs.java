package com.aiinpocket.n3n.site.service;

import java.security.SecureRandom;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 網站 slug 產生與驗證。
 *
 * slug 規則：^[a-z0-9-]{3,64}$，由名稱轉 kebab-case 並加 4 碼隨機尾碼；
 * 保留字（平台路由）一律拒絕，避免遮蔽 /api、/assets 等路徑。
 */
public final class SiteSlugs {

    public static final Pattern SLUG_PATTERN = Pattern.compile("^[a-z0-9-]{3,64}$");

    /** 平台既有路由與敏感字，不得作為 slug */
    static final Set<String> RESERVED = Set.of(
            "api", "ws", "assets", "static", "login", "logout", "register", "admin",
            "share", "sites", "site", "webhook", "webhooks", "forms", "form", "setup",
            "actuator", "swagger-ui", "oauth2", "index", "app", "www", "root", "system"
    );

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SUFFIX_CHARS = "abcdefghijklmnopqrstuvwxyz0123456789";
    private static final int SUFFIX_LENGTH = 4;
    private static final int MAX_BASE_LENGTH = 64 - SUFFIX_LENGTH - 1;

    private SiteSlugs() {
    }

    /**
     * 由網站名稱產生候選 slug：kebab-case 化 + "-" + 4 碼隨機尾碼。
     * 名稱不含 ASCII 字元時（例如純中文），base 退回 "site"。
     */
    public static String generate(String name) {
        String base = kebabCase(name);
        if (base.length() < 2) {
            base = "site";
        }
        if (base.length() > MAX_BASE_LENGTH) {
            base = base.substring(0, MAX_BASE_LENGTH);
            base = trimDashes(base);
        }
        return base + "-" + randomSuffix();
    }

    /**
     * 驗證 slug：格式 + 保留字。不合法時拋出 IllegalArgumentException。
     */
    public static String validate(String slug) {
        if (slug == null || !SLUG_PATTERN.matcher(slug).matches()) {
            throw new IllegalArgumentException(
                    "Invalid slug (must match [a-z0-9-]{3,64}): " + slug);
        }
        if (RESERVED.contains(slug)) {
            throw new IllegalArgumentException("Slug is a reserved word: " + slug);
        }
        return slug;
    }

    private static String kebabCase(String name) {
        if (name == null) {
            return "";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder(lower.length());
        boolean lastDash = true; // 避免開頭 dash
        for (char c : lower.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) {
                sb.append(c);
                lastDash = false;
            } else if (!lastDash) {
                sb.append('-');
                lastDash = true;
            }
        }
        return trimDashes(sb.toString());
    }

    private static String trimDashes(String value) {
        String result = value;
        while (result.startsWith("-")) {
            result = result.substring(1);
        }
        while (result.endsWith("-")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private static String randomSuffix() {
        StringBuilder sb = new StringBuilder(SUFFIX_LENGTH);
        for (int i = 0; i < SUFFIX_LENGTH; i++) {
            sb.append(SUFFIX_CHARS.charAt(RANDOM.nextInt(SUFFIX_CHARS.length())));
        }
        return sb.toString();
    }
}
