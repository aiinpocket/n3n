package com.aiinpocket.n3n.hostedapp.dto;

import lombok.Builder;

import java.util.Locale;

/**
 * 使用者須填寫（或可覆寫）的參數定義。
 *
 * @param name         環境變數名稱
 * @param defaultValue 預設值（${VAR:-def} 或 Dockerfile ENV/ARG 預設）；null = 無預設
 * @param required     無預設值即為必填
 * @param secret       名稱含 PASS/SECRET/TOKEN/KEY 的啟發式判定，儲存時加密、回傳時遮罩
 */
@Builder
public record ParamSpec(
        String name,
        String defaultValue,
        boolean required,
        boolean secret
) {

    /** 秘密參數啟發式：名稱（不分大小寫）含 PASS / SECRET / TOKEN / KEY */
    public static boolean isSecretName(String name) {
        if (name == null) {
            return false;
        }
        String upper = name.toUpperCase(Locale.ROOT);
        return upper.contains("PASS") || upper.contains("SECRET")
                || upper.contains("TOKEN") || upper.contains("KEY");
    }
}
