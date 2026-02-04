package com.aiinpocket.n3n.credential.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.*;

class EncryptionServiceTest {

    private EncryptionService encryptionService;
    // Use a fixed test salt (Base64 encoded 32 bytes)
    private static final String TEST_INSTANCE_SALT = Base64.getEncoder().encodeToString(
        "test-instance-salt-for-testing32".getBytes());

    @BeforeEach
    void setUp() {
        // Use a test encryption key (will be derived using PBKDF2)
        String testKey = "test-encryption-key-for-testing-32b";
        encryptionService = new EncryptionService(testKey, TEST_INSTANCE_SALT);
    }

    // ========== Basic Encryption Tests ==========

    @Test
    void encrypt_plainText_returnsEncryptedData() {
        // Given
        String plaintext = "sensitive-api-key-12345";

        // When
        EncryptionService.EncryptedData result = encryptionService.encrypt(plaintext);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.ciphertext()).isNotEmpty();
        assertThat(result.iv()).isNotEmpty();
        assertThat(result.iv()).hasSize(12); // GCM IV is 12 bytes
    }

    @Test
    void encrypt_sameTextTwice_producesDifferentCiphertext() {
        // Given
        String plaintext = "same-text";

        // When
        EncryptionService.EncryptedData result1 = encryptionService.encrypt(plaintext);
        EncryptionService.EncryptedData result2 = encryptionService.encrypt(plaintext);

        // Then
        assertThat(result1.ciphertext()).isNotEqualTo(result2.ciphertext());
        assertThat(result1.iv()).isNotEqualTo(result2.iv());
    }

    // ========== Decryption Tests ==========

    @Test
    void decrypt_validCiphertext_returnsOriginalText() {
        // Given
        String originalText = "my-secret-password";
        EncryptionService.EncryptedData encrypted = encryptionService.encrypt(originalText);

        // When
        String decrypted = encryptionService.decrypt(encrypted.ciphertext(), encrypted.iv());

        // Then
        assertThat(decrypted).isEqualTo(originalText);
    }

    @Test
    void decrypt_tamperedCiphertext_throwsException() {
        // Given
        String plaintext = "secret";
        EncryptionService.EncryptedData encrypted = encryptionService.encrypt(plaintext);

        // Tamper with ciphertext
        byte[] tampered = encrypted.ciphertext().clone();
        tampered[0] = (byte) (tampered[0] ^ 0xFF);

        // When/Then
        assertThatThrownBy(() -> encryptionService.decrypt(tampered, encrypted.iv()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to decrypt");
    }

    @Test
    void decrypt_wrongIv_throwsException() {
        // Given
        String plaintext = "secret";
        EncryptionService.EncryptedData encrypted = encryptionService.encrypt(plaintext);

        // Use wrong IV
        byte[] wrongIv = new byte[12];

        // When/Then
        assertThatThrownBy(() -> encryptionService.decrypt(encrypted.ciphertext(), wrongIv))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Failed to decrypt");
    }

    // ========== Base64 Encoding Tests ==========

    @Test
    void encryptToBase64_plainText_returnsBase64String() {
        // Given
        String plaintext = "api-key-12345";

        // When
        String result = encryptionService.encryptToBase64(plaintext);

        // Then
        assertThat(result).isNotNull();
        assertThat(result).contains(":"); // Format: iv:ciphertext
        String[] parts = result.split(":");
        assertThat(parts).hasSize(2);
    }

    @Test
    void decryptFromBase64_validString_returnsOriginalText() {
        // Given
        String originalText = "super-secret-token";
        String encrypted = encryptionService.encryptToBase64(originalText);

        // When
        String decrypted = encryptionService.decryptFromBase64(encrypted);

        // Then
        assertThat(decrypted).isEqualTo(originalText);
    }

    @Test
    void decryptFromBase64_invalidFormat_throwsException() {
        // Given
        String invalidFormat = "no-colon-separator";

        // When/Then
        assertThatThrownBy(() -> encryptionService.decryptFromBase64(invalidFormat))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid encrypted data format");
    }

    // ========== Edge Cases ==========

    @Test
    void encrypt_emptyString_succeeds() {
        // Given
        String plaintext = "";

        // When
        EncryptionService.EncryptedData result = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(result.ciphertext(), result.iv());

        // Then
        assertThat(decrypted).isEmpty();
    }

    @Test
    void encrypt_longText_succeeds() {
        // Given
        String plaintext = "x".repeat(10000);

        // When
        EncryptionService.EncryptedData result = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(result.ciphertext(), result.iv());

        // Then
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void encrypt_unicodeText_succeeds() {
        // Given
        String plaintext = "中文密碼 한국어 日本語 🔐";

        // When
        EncryptionService.EncryptedData result = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(result.ciphertext(), result.iv());

        // Then
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void encrypt_specialCharacters_succeeds() {
        // Given
        String plaintext = "!@#$%^&*()_+-=[]{}|;':\",./<>?`~\\";

        // When
        EncryptionService.EncryptedData result = encryptionService.encrypt(plaintext);
        String decrypted = encryptionService.decrypt(result.ciphertext(), result.iv());

        // Then
        assertThat(decrypted).isEqualTo(plaintext);
    }

    // ========== Key Derivation Tests ==========

    @Test
    void constructor_shortKey_derivesSecureKey() {
        // Given - Short key will be derived using PBKDF2 to proper length
        String shortKey = "short";

        // When
        EncryptionService service = new EncryptionService(shortKey, TEST_INSTANCE_SALT);
        String plaintext = "test";
        String encrypted = service.encryptToBase64(plaintext);
        String decrypted = service.decryptFromBase64(encrypted);

        // Then - PBKDF2 derives a secure key regardless of input length
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void constructor_longKey_derivesSecureKey() {
        // Given - Long key will also be derived using PBKDF2
        String longKey = "this-is-a-very-long-key-that-exceeds-32-bytes-in-length";

        // When
        EncryptionService service = new EncryptionService(longKey, TEST_INSTANCE_SALT);
        String plaintext = "test";
        String encrypted = service.encryptToBase64(plaintext);
        String decrypted = service.decryptFromBase64(encrypted);

        // Then
        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void constructor_differentSalt_producesDifferentKeys() {
        // Given
        String key = "same-key";
        String salt1 = Base64.getEncoder().encodeToString("salt-one-32-bytes-for-testing!!".getBytes());
        String salt2 = Base64.getEncoder().encodeToString("salt-two-32-bytes-for-testing!!".getBytes());

        // When
        EncryptionService service1 = new EncryptionService(key, salt1);
        EncryptionService service2 = new EncryptionService(key, salt2);

        String plaintext = "test data";
        String encrypted1 = service1.encryptToBase64(plaintext);

        // Then - Cannot decrypt with different salt-derived key
        assertThatThrownBy(() -> service2.decryptFromBase64(encrypted1))
            .isInstanceOf(RuntimeException.class);
    }

    @Test
    void constructor_nullSalt_generatesRandomSalt() {
        // Given - null salt should generate a random one
        String key = "test-key";

        // When
        EncryptionService service = new EncryptionService(key, null);
        String plaintext = "test";
        String encrypted = service.encryptToBase64(plaintext);
        String decrypted = service.decryptFromBase64(encrypted);

        // Then
        assertThat(decrypted).isEqualTo(plaintext);
    }
}
