package com.aiinpocket.n3n.credential.service;

import com.aiinpocket.n3n.credential.dto.RecoveryKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class EncryptionServiceTest {

    private EncryptionService encryptionService;

    @Mock
    private MasterKeyProvider masterKeyProvider;

    private SecretKey testKey;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // Generate a test AES-256 key
        byte[] keyBytes = new byte[32];
        new SecureRandom().nextBytes(keyBytes);
        testKey = new SecretKeySpec(keyBytes, "AES");

        when(masterKeyProvider.getMasterKey()).thenReturn(testKey);
        when(masterKeyProvider.getKeySource()).thenReturn("test");
        when(masterKeyProvider.needsRecoveryKeySetup()).thenReturn(false);

        encryptionService = new EncryptionService(masterKeyProvider);
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

    // ========== Recovery Key Integration Tests ==========

    @Test
    void needsRecoveryKeySetup_delegatesToMasterKeyProvider() {
        // Given
        when(masterKeyProvider.needsRecoveryKeySetup()).thenReturn(true);

        // When
        boolean result = encryptionService.needsRecoveryKeySetup();

        // Then
        assertThat(result).isTrue();
        verify(masterKeyProvider).needsRecoveryKeySetup();
    }

    @Test
    void getPendingRecoveryKey_delegatesToMasterKeyProvider() {
        // Given
        RecoveryKey mockKey = RecoveryKey.builder()
                .words(java.util.List.of("apple", "banana", "cherry", "date", "elder", "fig", "grape", "honey"))
                .build();
        when(masterKeyProvider.getPendingRecoveryKey()).thenReturn(mockKey);

        // When
        RecoveryKey result = encryptionService.getPendingRecoveryKey();

        // Then
        assertThat(result).isEqualTo(mockKey);
        verify(masterKeyProvider).getPendingRecoveryKey();
    }

    // ========== Different Keys Produce Different Ciphertext ==========

    @Test
    void encrypt_withDifferentKey_producesDifferentCiphertext() {
        // Given
        String plaintext = "test data";
        String encrypted1 = encryptionService.encryptToBase64(plaintext);

        // Create new key
        byte[] newKeyBytes = new byte[32];
        new SecureRandom().nextBytes(newKeyBytes);
        SecretKey newKey = new SecretKeySpec(newKeyBytes, "AES");
        when(masterKeyProvider.getMasterKey()).thenReturn(newKey);

        // Recreate service with new key
        EncryptionService newService = new EncryptionService(masterKeyProvider);
        String encrypted2 = newService.encryptToBase64(plaintext);

        // Then - Different keys produce different ciphertext
        assertThat(encrypted1).isNotEqualTo(encrypted2);

        // And cannot decrypt with wrong key
        assertThatThrownBy(() -> newService.decryptFromBase64(encrypted1))
                .isInstanceOf(RuntimeException.class);
    }
}
