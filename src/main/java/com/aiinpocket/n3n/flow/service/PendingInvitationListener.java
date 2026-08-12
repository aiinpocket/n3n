package com.aiinpocket.n3n.flow.service;

import com.aiinpocket.n3n.auth.event.UserAuthenticatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 使用者登入 / 註冊成功後，自動接受其 Email 待處理的流程分享邀請。
 *
 * 使用 @Async + AFTER_COMMIT：
 * - 在登入交易提交後才執行，且在獨立執行緒上以全新交易寫入
 * - 任何失敗只記錄日誌，絕不影響登入流程
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PendingInvitationListener {

    private final FlowShareService flowShareService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserAuthenticated(UserAuthenticatedEvent event) {
        try {
            flowShareService.acceptPendingInvitations(event.userId(), event.email());
        } catch (Exception e) {
            // 絕不讓邀請接受失敗影響登入
            log.error("Failed to accept pending flow invitations for user {} ({}): {}",
                event.userId(), event.email(), e.getMessage(), e);
        }
    }
}
