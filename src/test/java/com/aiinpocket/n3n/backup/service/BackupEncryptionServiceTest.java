package com.aiinpocket.n3n.backup.service;

import com.aiinpocket.n3n.credential.service.RecoveryKeyService;
import com.aiinpocket.n3n.credential.wordlist.BIP39WordList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BackupEncryptionServiceTest {

    private BackupEncryptionService encryptionService;
    private RecoveryKeyService recoveryKeyService;

    @BeforeEach
    void setUp() {
        BIP39WordList wordList = new BIP39WordList();
        wordList.init(); // Manually trigger @PostConstruct
        recoveryKeyService = new RecoveryKeyService(wordList);
        encryptionService = new BackupEncryptionService(recoveryKeyService);
    }

    @Test
    void encryptAndDecrypt_withRecoveryKey_shouldRoundTrip() {
        // Given
        var recoveryKey = recoveryKeyService.generate();
        String phrase = recoveryKey.toPhrase();
        byte[] data = "Hello, encrypted backup!".getBytes(StandardCharsets.UTF_8);

        // When
        var encrypted = encryptionService.encrypt(data, phrase);

        // Then
        assertThat(encrypted.iv()).isNotBlank();
        assertThat(encrypted.data()).isNotBlank();

        // Decrypt
        byte[] decrypted = encryptionService.decrypt(encrypted.iv(), encrypted.data(), phrase);
        assertThat(decrypted).isEqualTo(data);
    }

    @Test
    void encryptWithKey_shouldRoundTripWithDecryptWithKey() throws Exception {
        // Given
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256, new SecureRandom());
        SecretKey key = keyGen.generateKey();
        byte[] data = "{\"flows\":[],\"credentials\":[]}".getBytes(StandardCharsets.UTF_8);

        // When
        var encrypted = encryptionService.encryptWithKey(data, key);

        // Then
        assertThat(encrypted.iv()).isNotBlank();
        assertThat(encrypted.data()).isNotBlank();

        // Decrypt
        byte[] decrypted = encryptionService.decryptWithKey(encrypted.iv(), encrypted.data(), key.getEncoded());
        assertThat(decrypted).isEqualTo(data);
    }

    @Test
    void decrypt_withWrongKey_shouldThrow() throws Exception {
        // Given
        KeyGenerator keyGen = KeyGenerator.getInstance("AES");
        keyGen.init(256, new SecureRandom());
        SecretKey key1 = keyGen.generateKey();
        SecretKey key2 = keyGen.generateKey();
        byte[] data = "secret data".getBytes(StandardCharsets.UTF_8);

        var encrypted = encryptionService.encryptWithKey(data, key1);

        // When/Then
        assertThatThrownBy(() ->
                encryptionService.decryptWithKey(encrypted.iv(), encrypted.data(), key2.getEncoded())
        ).isInstanceOf(RuntimeException.class)
         .hasMessageContaining("Decryption failed");
    }

    @Test
    void calculateChecksum_shouldBeConsistent() {
        byte[] data = "test data for checksum".getBytes(StandardCharsets.UTF_8);

        String checksum1 = encryptionService.calculateChecksum(data);
        String checksum2 = encryptionService.calculateChecksum(data);

        assertThat(checksum1).isEqualTo(checksum2);
        assertThat(checksum1).isNotBlank();
    }

    @Test
    void calculateChecksum_differentData_shouldDiffer() {
        byte[] data1 = "data A".getBytes(StandardCharsets.UTF_8);
        byte[] data2 = "data B".getBytes(StandardCharsets.UTF_8);

        String checksum1 = encryptionService.calculateChecksum(data1);
        String checksum2 = encryptionService.calculateChecksum(data2);

        assertThat(checksum1).isNotEqualTo(checksum2);
    }

    @Test
    void calculateFingerprint_shouldBeConsistent() {
        var recoveryKey = recoveryKeyService.generate();
        String phrase = recoveryKey.toPhrase();

        String fp1 = encryptionService.calculateFingerprint(phrase);
        String fp2 = encryptionService.calculateFingerprint(phrase);

        assertThat(fp1).isEqualTo(fp2);
        assertThat(fp1).hasSize(16); // SHA-256 first 16 hex chars
    }

    @Test
    void calculateFingerprint_shouldBeCaseInsensitive() {
        var recoveryKey = recoveryKeyService.generate();
        String phrase = recoveryKey.toPhrase();

        String fp1 = encryptionService.calculateFingerprint(phrase.toLowerCase());
        String fp2 = encryptionService.calculateFingerprint(phrase.toUpperCase());

        assertThat(fp1).isEqualTo(fp2);
    }

    @Test
    void encryptedPayload_shouldNotContainPlaintext() {
        var recoveryKey = recoveryKeyService.generate();
        String phrase = recoveryKey.toPhrase();
        String sensitiveData = "super-secret-api-key-12345";
        byte[] data = sensitiveData.getBytes(StandardCharsets.UTF_8);

        var encrypted = encryptionService.encrypt(data, phrase);

        assertThat(encrypted.data()).doesNotContain(sensitiveData);
    }

    @Test
    void encrypt_largePayload_shouldWork() {
        var recoveryKey = recoveryKeyService.generate();
        String phrase = recoveryKey.toPhrase();
        // 1 MB payload
        byte[] data = new byte[1024 * 1024];
        new SecureRandom().nextBytes(data);

        var encrypted = encryptionService.encrypt(data, phrase);
        byte[] decrypted = encryptionService.decrypt(encrypted.iv(), encrypted.data(), phrase);

        assertThat(decrypted).isEqualTo(data);
    }
}
