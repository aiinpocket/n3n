package com.aiinpocket.n3n.auth.service;

import com.aiinpocket.n3n.auth.entity.User;
import com.aiinpocket.n3n.auth.entity.UserRole;
import com.aiinpocket.n3n.auth.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 管理員 Email 綁定：`n3n.auth.admin-emails` 名單內的帳號
 * 在登入或註冊時自動獲得 ADMIN 角色。
 *
 * <ul>
 *   <li>冪等：已有 ADMIN 角色時不做任何事</li>
 *   <li>只會授予角色，永遠不會移除任何角色</li>
 *   <li>Email 比對不分大小寫</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminEmailBinder {

    private final UserRoleRepository userRoleRepository;

    @Value("${n3n.auth.admin-emails:}")
    private String adminEmails;

    /**
     * 若使用者 Email 在管理員名單內且尚未具備 ADMIN 角色，補上 ADMIN 角色。
     * 必須在既有交易內呼叫（登入/註冊流程皆為 @Transactional）。
     *
     * @return true 表示本次呼叫實際授予了 ADMIN 角色
     */
    @Transactional
    public boolean ensureAdminRole(User user) {
        if (user == null || user.getId() == null || user.getEmail() == null) {
            return false;
        }

        Set<String> boundEmails = parseAdminEmails();
        if (boundEmails.isEmpty()
                || !boundEmails.contains(user.getEmail().toLowerCase(Locale.ROOT))) {
            return false;
        }

        boolean hasAdmin = userRoleRepository.findByUserId(user.getId()).stream()
                .anyMatch(role -> "ADMIN".equals(role.getRole()));
        if (hasAdmin) {
            return false;
        }

        userRoleRepository.save(UserRole.builder()
                .userId(user.getId())
                .role("ADMIN")
                .build());
        log.info("Granted ADMIN role to bound admin email: {}", user.getEmail());
        return true;
    }

    private Set<String> parseAdminEmails() {
        if (adminEmails == null || adminEmails.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(adminEmails.split(","))
                .map(String::trim)
                .filter(email -> !email.isEmpty())
                .map(email -> email.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }
}
