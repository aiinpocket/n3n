package com.aiinpocket.n3n.auth.event;

import java.util.UUID;

/**
 * 使用者成功登入 / 註冊後發布的事件。
 * 其他模組（如 flow 分享邀請）可據此執行後續處理，避免 auth 模組反向依賴。
 */
public record UserAuthenticatedEvent(UUID userId, String email) {
}
