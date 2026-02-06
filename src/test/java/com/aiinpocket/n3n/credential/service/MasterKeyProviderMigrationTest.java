package com.aiinpocket.n3n.credential.service;

import com.aiinpocket.n3n.auth.entity.User;
import com.aiinpocket.n3n.auth.repository.UserRepository;
import com.aiinpocket.n3n.credential.entity.Credential;
import com.aiinpocket.n3n.credential.entity.EncryptionKeyMetadata;
import com.aiinpocket.n3n.credential.entity.KeyMigrationLog;
import com.aiinpocket.n3n.credential.repository.CredentialRepository;
import com.aiinpocket.n3n.credential.repository.EncryptionKeyMetadataRepository;
import com.aiinpocket.n3n.credential.repository.KeyMigrationLogRepository;
import com.aiinpocket.n3n.credential.dto.RecoveryKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MasterKeyProviderMigrationTest {

    @Mock
    private RecoveryKeyService recoveryKeyService;
    @Mock
    private EncryptionKeyMetadataRepository metadataRepository;
    @Mock
    private KeyMigrationLogRepository migrationLogRepository;
    @Mock
    private CredentialRepository credentialRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private MasterKeyProvider masterKeyProvider;

    private SecretKey testOldKey;
    private SecretKey testNewKey;
    private UUID testUserId;
    private UUID testCredentialId;

    @BeforeEach
    void setUp() {
        masterKeyProvider = new MasterKeyProvider(
                recoveryKeyService, metadataRepository,
                migrationLogRepository, credentialRepository,
                userRepository, passwordEncoder);

        // Generate test keys
        byte[] oldKeyBytes = new byte[32];
        byte[] newKeyBytes = new byte[32];
        new SecureRandom().nextBytes(oldKeyBytes);
        new SecureRandom().nextBytes(newKeyBytes);
        testOldKey = new SecretKeySpec(oldKeyBytes, "AES");
        testNewKey = new SecretKeySpec(newKeyBytes, "AES");

        testUserId = UUID.randomUUID();
        testCredentialId = UUID.randomUUID();

        // Set the masterKey and currentKeyVersion using reflection
        ReflectionTestUtils.setField(masterKeyProvider, "masterKey", testNewKey);
        ReflectionTestUtils.setField(masterKeyProvider, "currentKeyVersion", 2);
    }

    // ========== migrateWithRecoveryKey ==========

    @Test
    void migrateWithRecoveryKey_invalidRecoveryKey_throwsException() {
        when(recoveryKeyService.validate("bad phrase")).thenReturn(false);

        assertThatThrownBy(() ->
                masterKeyProvider.migrateWithRecoveryKey("bad phrase", testCredentialId, testUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Recovery Key format");
    }

    @Test
    void migrateWithRecoveryKey_credentialNotFound_throwsException() {
        when(recoveryKeyService.validate("valid phrase")).thenReturn(true);
        when(credentialRepository.findById(testCredentialId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                masterKeyProvider.migrateWithRecoveryKey("valid phrase", testCredentialId, testUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Credential not found");
    }

    @Test
    void migrateWithRecoveryKey_success_reEncryptsCredential() throws Exception {
        String recoveryPhrase = "apple banana cherry date elder fig grape honey";
        byte[] plaintext = "secret-data".getBytes();

        // Encrypt plaintext with old key
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        byte[] encryptedData = encryptWithKey(plaintext, iv, testOldKey);

        Credential credential = Credential.builder()
                .id(testCredentialId)
                .name("Test Cred")
                .type("api_key")
                .ownerId(testUserId)
                .encryptedData(encryptedData)
                .encryptionIv(iv)
                .keyVersion(1)
                .keyStatus("active")
                .build();

        when(recoveryKeyService.validate(recoveryPhrase)).thenReturn(true);
        when(credentialRepository.findById(testCredentialId)).thenReturn(Optional.of(credential));
        when(recoveryKeyService.deriveMasterKey(recoveryPhrase)).thenReturn(testOldKey.getEncoded());
        when(migrationLogRepository.save(any(KeyMigrationLog.class))).thenAnswer(i -> i.getArgument(0));
        when(credentialRepository.save(any(Credential.class))).thenAnswer(i -> i.getArgument(0));

        masterKeyProvider.migrateWithRecoveryKey(recoveryPhrase, testCredentialId, testUserId);

        // Verify credential was saved with new encryption
        ArgumentCaptor<Credential> credCaptor = ArgumentCaptor.forClass(Credential.class);
        verify(credentialRepository).save(credCaptor.capture());
        Credential saved = credCaptor.getValue();
        assertThat(saved.getKeyVersion()).isEqualTo(2);
        assertThat(saved.getKeyStatus()).isEqualTo("active");
        // Verify we can decrypt with new key
        byte[] decrypted = decryptWithKey(saved.getEncryptedData(), saved.getEncryptionIv(), testNewKey);
        assertThat(new String(decrypted)).isEqualTo("secret-data");

        // Verify migration log was updated
        ArgumentCaptor<KeyMigrationLog> logCaptor = ArgumentCaptor.forClass(KeyMigrationLog.class);
        verify(migrationLogRepository, atLeast(2)).save(logCaptor.capture());
        List<KeyMigrationLog> logs = logCaptor.getAllValues();
        KeyMigrationLog completedLog = logs.get(logs.size() - 1);
        assertThat(completedLog.getStatus()).isEqualTo(KeyMigrationLog.STATUS_COMPLETED);
    }

    // ========== emergencyRestore ==========

    @Test
    void emergencyRestore_invalidRecoveryKey_throwsException() {
        when(recoveryKeyService.validate("bad phrase")).thenReturn(false);

        assertThatThrownBy(() ->
                masterKeyProvider.emergencyRestore("bad phrase", "password123", testUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid Recovery Key format");
    }

    @Test
    void emergencyRestore_userNotFound_throwsException() {
        when(recoveryKeyService.validate("valid phrase")).thenReturn(true);
        when(userRepository.findById(testUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                masterKeyProvider.emergencyRestore("valid phrase", "password123", testUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    @Test
    void emergencyRestore_wrongPassword_throwsSecurityException() {
        when(recoveryKeyService.validate("valid phrase")).thenReturn(true);
        User user = User.builder()
                .id(testUserId)
                .email("test@example.com")
                .passwordHash("$2a$10$hashed")
                .name("Test User")
                .status("active")
                .build();
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong-password", "$2a$10$hashed")).thenReturn(false);

        assertThatThrownBy(() ->
                masterKeyProvider.emergencyRestore("valid phrase", "wrong-password", testUserId))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Invalid permanent password");
    }

    @Test
    void emergencyRestore_success_returnsNewRecoveryKey() {
        String oldPhrase = "apple banana cherry date elder fig grape honey";
        byte[] oldKeyBytes = testOldKey.getEncoded();
        byte[] newKeyBytes = new byte[32];
        new SecureRandom().nextBytes(newKeyBytes);

        RecoveryKey newRecoveryKey = RecoveryKey.builder()
                .words(List.of("new", "words", "for", "recovery", "key", "backup", "test", "data"))
                .build();

        User user = User.builder()
                .id(testUserId)
                .email("test@example.com")
                .passwordHash("$2a$10$hashed")
                .name("Test User")
                .status("active")
                .build();

        when(recoveryKeyService.validate(oldPhrase)).thenReturn(true);
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("correct-password", "$2a$10$hashed")).thenReturn(true);
        when(recoveryKeyService.generate()).thenReturn(newRecoveryKey);
        when(recoveryKeyService.deriveMasterKey(oldPhrase)).thenReturn(oldKeyBytes);
        when(recoveryKeyService.deriveMasterKey(newRecoveryKey.toPhrase())).thenReturn(newKeyBytes);
        when(metadataRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(metadataRepository.findByKeyTypeAndKeyVersion(any(), anyInt())).thenReturn(Optional.empty());
        when(credentialRepository.findByOwnerIdAndKeyVersionLessThan(eq(testUserId), anyInt()))
                .thenReturn(List.of()); // No credentials to migrate

        RecoveryKey result = masterKeyProvider.emergencyRestore(oldPhrase, "correct-password", testUserId);

        assertThat(result).isNotNull();
        assertThat(result.getWords()).isEqualTo(newRecoveryKey.getWords());

        // Verify metadata was saved
        verify(metadataRepository).save(argThat(meta ->
                meta.getKeyType().equals(EncryptionKeyMetadata.TYPE_RECOVERY) &&
                meta.getStatus().equals(EncryptionKeyMetadata.STATUS_ACTIVE)));
    }

    @Test
    void emergencyRestore_withCredentials_reEncryptsAll() throws Exception {
        String oldPhrase = "apple banana cherry date elder fig grape honey";
        byte[] plaintext = "api-key-123".getBytes();

        // Encrypt with old key
        byte[] iv = new byte[12];
        new SecureRandom().nextBytes(iv);
        byte[] encryptedData = encryptWithKey(plaintext, iv, testOldKey);

        Credential credential = Credential.builder()
                .id(testCredentialId)
                .name("Test Cred")
                .type("api_key")
                .ownerId(testUserId)
                .encryptedData(encryptedData)
                .encryptionIv(iv)
                .keyVersion(1)
                .keyStatus("active")
                .build();

        byte[] newKeyBytes = new byte[32];
        new SecureRandom().nextBytes(newKeyBytes);

        RecoveryKey newRecoveryKey = RecoveryKey.builder()
                .words(List.of("new", "words", "for", "recovery", "key", "backup", "test", "data"))
                .build();

        User user = User.builder()
                .id(testUserId)
                .email("test@example.com")
                .passwordHash("$2a$10$hashed")
                .name("Test User")
                .status("active")
                .build();

        when(recoveryKeyService.validate(oldPhrase)).thenReturn(true);
        when(userRepository.findById(testUserId)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password", "$2a$10$hashed")).thenReturn(true);
        when(recoveryKeyService.generate()).thenReturn(newRecoveryKey);
        when(recoveryKeyService.deriveMasterKey(oldPhrase)).thenReturn(testOldKey.getEncoded());
        when(recoveryKeyService.deriveMasterKey(newRecoveryKey.toPhrase())).thenReturn(newKeyBytes);
        when(metadataRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(metadataRepository.findByKeyTypeAndKeyVersion(any(), anyInt())).thenReturn(Optional.empty());
        when(credentialRepository.findByOwnerIdAndKeyVersionLessThan(eq(testUserId), anyInt()))
                .thenReturn(List.of(credential));
        when(credentialRepository.save(any(Credential.class))).thenAnswer(i -> i.getArgument(0));

        masterKeyProvider.emergencyRestore(oldPhrase, "password", testUserId);

        // Verify credential was re-encrypted
        ArgumentCaptor<Credential> credCaptor = ArgumentCaptor.forClass(Credential.class);
        verify(credentialRepository).save(credCaptor.capture());
        Credential saved = credCaptor.getValue();
        assertThat(saved.getKeyVersion()).isEqualTo(3); // currentKeyVersion was 2, incremented to 3

        // Verify we can decrypt with the new derived key
        SecretKey actualNewKey = new SecretKeySpec(newKeyBytes, "AES");
        byte[] decrypted = decryptWithKey(saved.getEncryptedData(), saved.getEncryptionIv(), actualNewKey);
        assertThat(new String(decrypted)).isEqualTo("api-key-123");
    }

    // ========== Helper Methods ==========

    private byte[] encryptWithKey(byte[] plaintext, byte[] iv, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, key, spec);
        return cipher.doFinal(plaintext);
    }

    private byte[] decryptWithKey(byte[] ciphertext, byte[] iv, SecretKey key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, key, spec);
        return cipher.doFinal(ciphertext);
    }
}
