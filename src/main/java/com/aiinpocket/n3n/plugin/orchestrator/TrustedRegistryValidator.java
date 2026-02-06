package com.aiinpocket.n3n.plugin.orchestrator;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 可信任 Registry 驗證器
 * Docker 和 K8s 編排器共用此邏輯。
 */
@Component
@Slf4j
public class TrustedRegistryValidator {

    @Value("${n3n.docker.trusted-registries:ghcr.io/n3n,docker.io/n3n}")
    private String trustedRegistriesConfig;

    public List<String> getTrustedRegistries() {
        return Arrays.stream(trustedRegistriesConfig.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * 驗證映像是否來自可信任的 Registry
     */
    public boolean isFromTrustedRegistry(String image) {
        List<String> trustedRegistries = getTrustedRegistries();
        if (trustedRegistries.isEmpty()) {
            log.warn("No trusted registries configured, allowing all images");
            return true;
        }

        for (String registry : trustedRegistries) {
            if (image.startsWith(registry + "/") || image.startsWith(registry + ":")) {
                return true;
            }
        }

        // Docker Hub 官方映像（無前綴）
        if (!image.contains("/") || image.startsWith("library/")) {
            return trustedRegistries.stream().anyMatch(r -> r.contains("docker.io"));
        }

        log.warn("Image {} is not from a trusted registry. Trusted registries: {}", image, trustedRegistries);
        return false;
    }
}
