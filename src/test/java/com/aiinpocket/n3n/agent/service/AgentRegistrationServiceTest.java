package com.aiinpocket.n3n.agent.service;

import com.aiinpocket.n3n.agent.entity.AgentRegistration;
import com.aiinpocket.n3n.agent.entity.AgentRegistration.AgentStatus;
import com.aiinpocket.n3n.agent.repository.AgentRegistrationRepository;
import com.aiinpocket.n3n.auth.entity.User;
import com.aiinpocket.n3n.auth.repository.UserRepository;
import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.base.TestDataFactory;
import com.aiinpocket.n3n.gateway.security.AgentCrypto;
import com.aiinpocket.n3n.gateway.security.AgentPairingService;
import com.aiinpocket.n3n.gateway.security.DeviceKeyStore;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AgentRegistrationServiceTest extends BaseServiceTest {

    @Mock
    private AgentRegistrationRepository registrationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GatewaySettingsService gatewaySettingsService;

    @Mock
    private AgentCrypto agentCrypto;

    @Mock
    private DeviceKeyStore deviceKeyStore;

    @Mock
    private AgentPairingService pairingService;

    @InjectMocks
    private AgentRegistrationService agentRegistrationService;

    // ========== Generate Token Tests ==========

    @Test
    void generateToken_validUser_returnsTokenResult() {
        // Given
        UUID userId = UUID.randomUUID();
        User user = TestDataFactory.createUser("test@example.com", "Test User");
        user.setId(userId);

        GatewaySettingsService.AgentConfig config = new GatewaySettingsService.AgentConfig(
                1,
                new GatewaySettingsService.AgentConfig.Gateway("wss://localhost:8080/gateway", "localhost", 8080),
                new GatewaySettingsService.AgentConfig.Registration("token", "agent-id")
        );

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(registrationRepository.save(any(AgentRegistration.class))).thenAnswer(invocation -> {
            AgentRegistration r = invocation.getArgument(0);
            r.setId(UUID.randomUUID());
            return r;
        });
        when(gatewaySettingsService.generateAgentConfig(anyString(), anyString())).thenReturn(config);

        // When
        AgentRegistrationService.TokenGenerationResult result = agentRegistrationService.generateToken(userId);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.registrationId()).isNotNull();
        assertThat(result.token()).isNotNull();
        assertThat(result.config()).isNotNull();
        verify(registrationRepository).save(argThat(r ->
                r.getStatus() == AgentStatus.PENDING && r.getUser().equals(user)));
    }

    @Test
    void generateToken_nonExistingUser_throwsException() {
        // Given
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> agentRegistrationService.generateToken(userId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("User not found");
    }

    // ========== Register Agent Tests ==========

    @Test
    void registerAgent_validToken_registersSuccessfully() throws Exception {
        // Given
        UUID userId = UUID.randomUUID();
        User user = TestDataFactory.createUser("test@example.com", "Test User");
        user.setId(userId);

        AgentRegistration registration = AgentRegistration.builder()
                .id(UUID.randomUUID())
                .user(user)
                .registrationTokenHash("hashed-token")
                .status(AgentStatus.PENDING)
                .build();

        KeyPair keyPair = KeyPairGenerator.getInstance("X25519").generateKeyPair();

        when(registrationRepository.findByRegistrationTokenHash(anyString()))
                .thenReturn(Optional.of(registration));
        when(agentCrypto.generateKeyPair()).thenReturn(keyPair);
        when(agentCrypto.parsePublicKey(any())).thenReturn(keyPair.getPublic());
        when(agentCrypto.deriveSharedSecret(any(), any())).thenReturn(new byte[32]);
        when(agentCrypto.deriveKeys(any(), any(), any())).thenReturn(
                new AgentCrypto.DerivedKeys(new byte[32], new byte[32], new byte[32]));
        when(agentCrypto.computeFingerprint(any())).thenReturn("fingerprint");
        when(agentCrypto.encodePublicKey(any())).thenReturn("encoded-key");
        when(agentCrypto.generateInitialSequence()).thenReturn(1000L);
        when(registrationRepository.save(any(AgentRegistration.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AgentRegistrationService.RegistrationRequest request = new AgentRegistrationService.RegistrationRequest(
                "test-token", "device-123", "My MacBook", "macos",
                "device-public-key", "device-fingerprint"
        );

        // When
        AgentRegistrationService.RegistrationResult result = agentRegistrationService.registerAgent(request);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.success()).isTrue();
        assertThat(result.platformPublicKey()).isEqualTo("encoded-key");
        assertThat(result.platformFingerprint()).isEqualTo("fingerprint");
        assertThat(result.deviceToken()).isNotNull();
        verify(deviceKeyStore).storeDeviceKey(any(DeviceKeyStore.DeviceKey.class));
        verify(registrationRepository).save(argThat(r -> r.getStatus() == AgentStatus.REGISTERED));
    }

    @Test
    void registerAgent_invalidToken_throwsRegistrationException() {
        // Given
        when(registrationRepository.findByRegistrationTokenHash(anyString())).thenReturn(Optional.empty());

        AgentRegistrationService.RegistrationRequest request = new AgentRegistrationService.RegistrationRequest(
                "invalid-token", "device-1", "Mac", "macos", "key", "fp"
        );

        // When/Then
        assertThatThrownBy(() -> agentRegistrationService.registerAgent(request))
                .isInstanceOf(AgentRegistrationService.RegistrationException.class)
                .hasMessageContaining("Invalid or expired");
    }

    @Test
    void registerAgent_alreadyRegistered_throwsRegistrationException() {
        // Given
        User user = TestDataFactory.createUser();
        AgentRegistration registration = AgentRegistration.builder()
                .id(UUID.randomUUID())
                .user(user)
                .registrationTokenHash("hashed")
                .status(AgentStatus.REGISTERED)
                .build();

        when(registrationRepository.findByRegistrationTokenHash(anyString()))
                .thenReturn(Optional.of(registration));

        AgentRegistrationService.RegistrationRequest request = new AgentRegistrationService.RegistrationRequest(
                "used-token", "device-1", "Mac", "macos", "key", "fp"
        );

        // When/Then
        assertThatThrownBy(() -> agentRegistrationService.registerAgent(request))
                .isInstanceOf(AgentRegistrationService.RegistrationException.class)
                .hasMessageContaining("already been used");
    }

    @Test
    void registerAgent_blockedRegistration_throwsRegistrationException() {
        // Given
        User user = TestDataFactory.createUser();
        AgentRegistration registration = AgentRegistration.builder()
                .id(UUID.randomUUID())
                .user(user)
                .registrationTokenHash("hashed")
                .status(AgentStatus.BLOCKED)
                .build();

        when(registrationRepository.findByRegistrationTokenHash(anyString()))
                .thenReturn(Optional.of(registration));

        AgentRegistrationService.RegistrationRequest request = new AgentRegistrationService.RegistrationRequest(
                "blocked-token", "device-1", "Mac", "macos", "key", "fp"
        );

        // When/Then
        assertThatThrownBy(() -> agentRegistrationService.registerAgent(request))
                .isInstanceOf(AgentRegistrationService.RegistrationException.class)
                .hasMessageContaining("blocked");
    }

    @Test
    void registerAgent_cryptoError_throwsRegistrationException() throws Exception {
        // Given
        User user = TestDataFactory.createUser();
        AgentRegistration registration = AgentRegistration.builder()
                .id(UUID.randomUUID())
                .user(user)
                .registrationTokenHash("hashed")
                .status(AgentStatus.PENDING)
                .build();

        when(registrationRepository.findByRegistrationTokenHash(anyString()))
                .thenReturn(Optional.of(registration));
        when(agentCrypto.generateKeyPair()).thenThrow(new GeneralSecurityException("Key generation failed"));

        AgentRegistrationService.RegistrationRequest request = new AgentRegistrationService.RegistrationRequest(
                "test-token", "device-1", "Mac", "macos", "key", "fp"
        );

        // When/Then
        assertThatThrownBy(() -> agentRegistrationService.registerAgent(request))
                .isInstanceOf(AgentRegistrationService.RegistrationException.class)
                .hasMessageContaining("Cryptographic error");
    }

    // ========== Block Agent Tests ==========

    @Test
    void blockAgent_existingRegistration_blocksAndRevokesDeviceKey() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID registrationId = UUID.randomUUID();
        String deviceId = "device-123";

        AgentRegistration registration = AgentRegistration.builder()
                .id(registrationId)
                .status(AgentStatus.REGISTERED)
                .deviceId(deviceId)
                .build();

        DeviceKeyStore.DeviceKey deviceKey = DeviceKeyStore.DeviceKey.builder()
                .deviceId(deviceId)
                .userId(userId)
                .revoked(false)
                .build();

        when(registrationRepository.findByIdAndUserId(registrationId, userId))
                .thenReturn(Optional.of(registration));
        when(registrationRepository.save(any(AgentRegistration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(deviceKeyStore.getDeviceKey(deviceId)).thenReturn(Optional.of(deviceKey));

        // When
        agentRegistrationService.blockAgent(userId, registrationId, "Suspicious activity");

        // Then
        verify(registrationRepository).save(argThat(r ->
                r.getStatus() == AgentStatus.BLOCKED));
        verify(deviceKeyStore).storeDeviceKey(argThat(DeviceKeyStore.DeviceKey::isRevoked));
    }

    @Test
    void blockAgent_nonExistingRegistration_throwsException() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID registrationId = UUID.randomUUID();

        when(registrationRepository.findByIdAndUserId(registrationId, userId))
                .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> agentRegistrationService.blockAgent(userId, registrationId, "reason"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Registration not found");
    }

    @Test
    void blockAgent_noDeviceId_onlyBlocksRegistration() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID registrationId = UUID.randomUUID();

        AgentRegistration registration = AgentRegistration.builder()
                .id(registrationId)
                .status(AgentStatus.PENDING)
                .deviceId(null)
                .build();

        when(registrationRepository.findByIdAndUserId(registrationId, userId))
                .thenReturn(Optional.of(registration));
        when(registrationRepository.save(any(AgentRegistration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When
        agentRegistrationService.blockAgent(userId, registrationId, "reason");

        // Then
        verify(registrationRepository).save(argThat(r -> r.getStatus() == AgentStatus.BLOCKED));
        verify(deviceKeyStore, never()).getDeviceKey(any());
    }

    // ========== Unblock Agent Tests ==========

    @Test
    void unblockAgent_blockedRegistration_unblocksAndUnrevokesDeviceKey() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID registrationId = UUID.randomUUID();
        String deviceId = "device-123";

        AgentRegistration registration = AgentRegistration.builder()
                .id(registrationId)
                .status(AgentStatus.BLOCKED)
                .deviceId(deviceId)
                .blockedAt(Instant.now())
                .blockedReason("test")
                .build();

        DeviceKeyStore.DeviceKey deviceKey = DeviceKeyStore.DeviceKey.builder()
                .deviceId(deviceId)
                .userId(userId)
                .revoked(true)
                .build();

        when(registrationRepository.findByIdAndUserId(registrationId, userId))
                .thenReturn(Optional.of(registration));
        when(registrationRepository.save(any(AgentRegistration.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(deviceKeyStore.getDeviceKey(deviceId)).thenReturn(Optional.of(deviceKey));

        // When
        agentRegistrationService.unblockAgent(userId, registrationId);

        // Then
        verify(registrationRepository).save(argThat(r ->
                r.getStatus() == AgentStatus.REGISTERED));
        verify(deviceKeyStore).storeDeviceKey(argThat(k -> !k.isRevoked()));
    }

    @Test
    void unblockAgent_nonExistingRegistration_throwsException() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID registrationId = UUID.randomUUID();

        when(registrationRepository.findByIdAndUserId(registrationId, userId))
                .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> agentRegistrationService.unblockAgent(userId, registrationId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Registration not found");
    }

    // ========== Delete Registration Tests ==========

    @Test
    void deleteRegistration_withDeviceId_deletesDeviceKeyAndRegistration() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID registrationId = UUID.randomUUID();
        String deviceId = "device-123";

        AgentRegistration registration = AgentRegistration.builder()
                .id(registrationId)
                .deviceId(deviceId)
                .status(AgentStatus.REGISTERED)
                .build();

        when(registrationRepository.findByIdAndUserId(registrationId, userId))
                .thenReturn(Optional.of(registration));

        // When
        agentRegistrationService.deleteRegistration(userId, registrationId);

        // Then
        verify(deviceKeyStore).deleteDeviceKey(deviceId);
        verify(registrationRepository).delete(registration);
    }

    @Test
    void deleteRegistration_withoutDeviceId_onlyDeletesRegistration() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID registrationId = UUID.randomUUID();

        AgentRegistration registration = AgentRegistration.builder()
                .id(registrationId)
                .deviceId(null)
                .status(AgentStatus.PENDING)
                .build();

        when(registrationRepository.findByIdAndUserId(registrationId, userId))
                .thenReturn(Optional.of(registration));

        // When
        agentRegistrationService.deleteRegistration(userId, registrationId);

        // Then
        verify(deviceKeyStore, never()).deleteDeviceKey(any());
        verify(registrationRepository).delete(registration);
    }

    @Test
    void deleteRegistration_nonExistingRegistration_throwsException() {
        // Given
        UUID userId = UUID.randomUUID();
        UUID registrationId = UUID.randomUUID();

        when(registrationRepository.findByIdAndUserId(registrationId, userId))
                .thenReturn(Optional.empty());

        // When/Then
        assertThatThrownBy(() -> agentRegistrationService.deleteRegistration(userId, registrationId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Registration not found");
    }

    // ========== Get Registrations Tests ==========

    @Test
    void getRegistrations_returnsRegistrationsForUser() {
        // Given
        UUID userId = UUID.randomUUID();
        AgentRegistration reg1 = AgentRegistration.builder()
                .id(UUID.randomUUID())
                .status(AgentStatus.PENDING)
                .build();
        AgentRegistration reg2 = AgentRegistration.builder()
                .id(UUID.randomUUID())
                .status(AgentStatus.REGISTERED)
                .deviceId("device-1")
                .build();

        when(registrationRepository.findByUserIdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(reg1, reg2));

        // When
        List<AgentRegistration> result = agentRegistrationService.getRegistrations(userId);

        // Then
        assertThat(result).hasSize(2);
    }

    // ========== Is Device Blocked Tests ==========

    @Test
    void isDeviceBlocked_blockedDevice_returnsTrue() {
        // Given
        AgentRegistration registration = AgentRegistration.builder()
                .id(UUID.randomUUID())
                .deviceId("device-123")
                .status(AgentStatus.BLOCKED)
                .build();

        when(registrationRepository.findByDeviceId("device-123"))
                .thenReturn(Optional.of(registration));

        // When
        boolean result = agentRegistrationService.isDeviceBlocked("device-123");

        // Then
        assertThat(result).isTrue();
    }

    @Test
    void isDeviceBlocked_activeDevice_returnsFalse() {
        // Given
        AgentRegistration registration = AgentRegistration.builder()
                .id(UUID.randomUUID())
                .deviceId("device-123")
                .status(AgentStatus.REGISTERED)
                .build();

        when(registrationRepository.findByDeviceId("device-123"))
                .thenReturn(Optional.of(registration));

        // When
        boolean result = agentRegistrationService.isDeviceBlocked("device-123");

        // Then
        assertThat(result).isFalse();
    }

    @Test
    void isDeviceBlocked_unknownDevice_returnsFalse() {
        // Given
        when(registrationRepository.findByDeviceId("unknown")).thenReturn(Optional.empty());

        // When
        boolean result = agentRegistrationService.isDeviceBlocked("unknown");

        // Then
        assertThat(result).isFalse();
    }

    // ========== Update Last Seen Tests ==========

    @Test
    void updateLastSeen_delegatesToRepository() {
        // Given
        String deviceId = "device-123";

        // When
        agentRegistrationService.updateLastSeen(deviceId);

        // Then
        verify(registrationRepository).updateLastSeenByDeviceId(deviceId);
    }
}
