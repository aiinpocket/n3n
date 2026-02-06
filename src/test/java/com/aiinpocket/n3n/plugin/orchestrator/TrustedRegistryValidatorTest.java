package com.aiinpocket.n3n.plugin.orchestrator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.*;

class TrustedRegistryValidatorTest {

    private TrustedRegistryValidator validator;

    @BeforeEach
    void setUp() {
        validator = new TrustedRegistryValidator();
        ReflectionTestUtils.setField(validator, "trustedRegistriesConfig", "ghcr.io/n3n,docker.io/n3n");
    }

    // ========== getTrustedRegistries ==========

    @Test
    void getTrustedRegistries_returnsParsedList() {
        var registries = validator.getTrustedRegistries();
        assertThat(registries).containsExactly("ghcr.io/n3n", "docker.io/n3n");
    }

    @Test
    void getTrustedRegistries_trimsWhitespace() {
        ReflectionTestUtils.setField(validator, "trustedRegistriesConfig", " ghcr.io/n3n , docker.io/n3n ");
        var registries = validator.getTrustedRegistries();
        assertThat(registries).containsExactly("ghcr.io/n3n", "docker.io/n3n");
    }

    @Test
    void getTrustedRegistries_emptyConfig_returnsEmptyList() {
        ReflectionTestUtils.setField(validator, "trustedRegistriesConfig", "");
        var registries = validator.getTrustedRegistries();
        assertThat(registries).isEmpty();
    }

    // ========== isFromTrustedRegistry ==========

    @Test
    void isFromTrustedRegistry_trustedImage_returnsTrue() {
        assertThat(validator.isFromTrustedRegistry("ghcr.io/n3n/my-plugin")).isTrue();
        assertThat(validator.isFromTrustedRegistry("docker.io/n3n/my-plugin")).isTrue();
    }

    @Test
    void isFromTrustedRegistry_untrustedImage_returnsFalse() {
        assertThat(validator.isFromTrustedRegistry("evil.io/malware")).isFalse();
        assertThat(validator.isFromTrustedRegistry("ghcr.io/attacker/exploit")).isFalse();
    }

    @Test
    void isFromTrustedRegistry_imageWithTag_returnsTrue() {
        assertThat(validator.isFromTrustedRegistry("ghcr.io/n3n:latest")).isTrue();
    }

    @Test
    void isFromTrustedRegistry_dockerHubOfficialImage_returnsTrue() {
        // Docker Hub official images (no slash prefix) should be allowed when docker.io is trusted
        assertThat(validator.isFromTrustedRegistry("nginx")).isTrue();
        assertThat(validator.isFromTrustedRegistry("library/nginx")).isTrue();
    }

    @Test
    void isFromTrustedRegistry_emptyRegistries_allowsAll() {
        ReflectionTestUtils.setField(validator, "trustedRegistriesConfig", "");
        assertThat(validator.isFromTrustedRegistry("any-image")).isTrue();
    }

    @Test
    void isFromTrustedRegistry_noDockerIo_blockOfficialImages() {
        ReflectionTestUtils.setField(validator, "trustedRegistriesConfig", "ghcr.io/n3n");
        assertThat(validator.isFromTrustedRegistry("nginx")).isFalse();
    }
}
