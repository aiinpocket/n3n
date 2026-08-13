package com.aiinpocket.n3n.execution.handler.handlers.document;

import java.awt.Color;

/**
 * 簡報主題（背景 / 強調 / 墨色）。
 *
 * warm 為預設「紙上工作室」暖色調。
 */
public record SlideTheme(String name, Color background, Color accent, Color ink) {

    public static final SlideTheme WARM = new SlideTheme(
            "warm", new Color(0xF6, 0xF1, 0xE7), new Color(0xC0, 0x65, 0x3B), new Color(0x3B, 0x32, 0x2A));

    public static final SlideTheme LIGHT = new SlideTheme(
            "light", new Color(0xFF, 0xFF, 0xFF), new Color(0x1F, 0x6F, 0xEB), new Color(0x1F, 0x23, 0x28));

    public static final SlideTheme DARK = new SlideTheme(
            "dark", new Color(0x1E, 0x1E, 0x22), new Color(0xE8, 0xA8, 0x7C), new Color(0xF0, 0xED, 0xE8));

    /**
     * 依名稱取得主題；未知名稱回傳 warm。
     */
    public static SlideTheme of(String name) {
        if (name == null) {
            return WARM;
        }
        return switch (name.trim().toLowerCase()) {
            case "light" -> LIGHT;
            case "dark" -> DARK;
            default -> WARM;
        };
    }
}
