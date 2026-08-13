package com.aiinpocket.n3n.hostedapp.service;

import com.aiinpocket.n3n.credential.service.EncryptionService;
import com.aiinpocket.n3n.hostedapp.dto.ParamSpec;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Hosted App 參數的秘密值保護：重用 credential 模組的 EncryptionService
 * （AES-256-GCM，主金鑰由 MasterKeyProvider 管理）。
 *
 * manifest 判定為 secret 的參數（名稱含 PASS/SECRET/TOKEN/KEY）在寫入
 * hosted_apps.params 前加密，並加上 enc:v1: 前綴標記；部署時解密還原。
 * 非秘密參數以明文存放（供 UI 回填表單）。
 */
@Service
@RequiredArgsConstructor
public class AppParamCrypto {

    static final String PREFIX = "enc:v1:";

    private final EncryptionService encryptionService;

    /** 依 manifest 的 secret 標記加密參數值（回傳新 map，不動原 map） */
    public Map<String, String> encryptSecrets(Map<String, String> params, List<ParamSpec> specs) {
        if (params == null || params.isEmpty()) {
            return Map.of();
        }
        Set<String> secretNames = specs == null ? Set.of() : specs.stream()
                .filter(ParamSpec::secret)
                .map(ParamSpec::name)
                .collect(Collectors.toSet());
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : params.entrySet()) {
            String value = entry.getValue();
            if (value != null && secretNames.contains(entry.getKey())) {
                result.put(entry.getKey(), PREFIX + encryptionService.encryptToBase64(value));
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }

    /** 解密帶 enc:v1: 前綴的值（部署時用；回傳新 map） */
    public Map<String, String> decrypt(Map<String, String> stored) {
        if (stored == null || stored.isEmpty()) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : stored.entrySet()) {
            String value = entry.getValue();
            if (value != null && value.startsWith(PREFIX)) {
                result.put(entry.getKey(),
                        encryptionService.decryptFromBase64(value.substring(PREFIX.length())));
            } else {
                result.put(entry.getKey(), value);
            }
        }
        return result;
    }
}
