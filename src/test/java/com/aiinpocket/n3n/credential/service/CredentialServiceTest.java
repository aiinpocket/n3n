package com.aiinpocket.n3n.credential.service;

import com.aiinpocket.n3n.activity.service.ActivityService;
import com.aiinpocket.n3n.base.BaseServiceTest;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.credential.dto.CreateCredentialRequest;
import com.aiinpocket.n3n.credential.dto.CredentialResponse;
import com.aiinpocket.n3n.credential.entity.Credential;
import com.aiinpocket.n3n.credential.entity.CredentialType;
import com.aiinpocket.n3n.credential.repository.CredentialRepository;
import com.aiinpocket.n3n.credential.repository.CredentialTypeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CredentialServiceTest extends BaseServiceTest {

    @Mock
    private CredentialRepository credentialRepository;

    @Mock
    private CredentialTypeRepository credentialTypeRepository;

    @Mock
    private EncryptionService encryptionService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ActivityService activityService;

    @InjectMocks
    private CredentialService credentialService;

    private UUID userId;
    private UUID otherUserId;
    private UUID credentialId;
    private Credential privateCredential;
    private Credential sharedCredential;
    private CredentialType apiKeyType;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        credentialId = UUID.randomUUID();

        privateCredential = Credential.builder()
                .id(credentialId)
                .name("My API Key")
                .type("api_key")
                .description("Test credential")
                .ownerId(userId)
                .visibility("private")
                .encryptedData(new byte[]{1, 2, 3})
                .encryptionIv(new byte[]{4, 5, 6})
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        sharedCredential = Credential.builder()
                .id(UUID.randomUUID())
                .name("Shared Key")
                .type("api_key")
                .description("Shared credential")
                .ownerId(userId)
                .visibility("shared")
                .encryptedData(new byte[]{7, 8, 9})
                .encryptionIv(new byte[]{10, 11, 12})
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        apiKeyType = CredentialType.builder()
                .id(UUID.randomUUID())
                .name("api_key")
                .displayName("API Key")
                .description("API Key credential")
                .fieldsSchema(Map.of("apiKey", Map.of("type", "string")))
                .build();
    }

    @Nested
    @DisplayName("listCredentials")
    class ListCredentials {

        @Test
        @DisplayName("should return page of accessible credentials")
        void listCredentials_returnsPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Credential> credentialPage = new PageImpl<>(
                    List.of(privateCredential, sharedCredential), pageable, 2);
            when(credentialRepository.findAccessibleByUser(userId, pageable))
                    .thenReturn(credentialPage);

            Page<CredentialResponse> result = credentialService.listCredentials(userId, pageable);

            assertThat(result.getContent()).hasSize(2);
            assertThat(result.getContent().get(0).getName()).isEqualTo("My API Key");
            assertThat(result.getContent().get(1).getName()).isEqualTo("Shared Key");
            verify(credentialRepository).findAccessibleByUser(userId, pageable);
        }

        @Test
        @DisplayName("should return empty page when no credentials")
        void listCredentials_emptyPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Credential> emptyPage = new PageImpl<>(List.of(), pageable, 0);
            when(credentialRepository.findAccessibleByUser(userId, pageable))
                    .thenReturn(emptyPage);

            Page<CredentialResponse> result = credentialService.listCredentials(userId, pageable);

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getTotalElements()).isZero();
        }
    }

    @Nested
    @DisplayName("listMyCredentials")
    class ListMyCredentials {

        @Test
        @DisplayName("should return page of user's own credentials")
        void listMyCredentials_returnsPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Credential> credentialPage = new PageImpl<>(
                    List.of(privateCredential), pageable, 1);
            when(credentialRepository.findByOwnerId(userId, pageable))
                    .thenReturn(credentialPage);

            Page<CredentialResponse> result = credentialService.listMyCredentials(userId, pageable);

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getOwnerId()).isEqualTo(userId);
            verify(credentialRepository).findByOwnerId(userId, pageable);
        }
    }

    @Nested
    @DisplayName("getCredential")
    class GetCredential {

        @Test
        @DisplayName("should return credential when user is owner")
        void getCredential_asOwner_returnsCredential() {
            when(credentialRepository.findById(credentialId))
                    .thenReturn(Optional.of(privateCredential));

            CredentialResponse result = credentialService.getCredential(credentialId, userId);

            assertThat(result.getId()).isEqualTo(credentialId);
            assertThat(result.getName()).isEqualTo("My API Key");
            assertThat(result.getType()).isEqualTo("api_key");
        }

        @Test
        @DisplayName("should return shared credential to non-owner")
        void getCredential_sharedToNonOwner_returnsCredential() {
            when(credentialRepository.findById(sharedCredential.getId()))
                    .thenReturn(Optional.of(sharedCredential));

            CredentialResponse result = credentialService.getCredential(
                    sharedCredential.getId(), otherUserId);

            assertThat(result.getId()).isEqualTo(sharedCredential.getId());
            assertThat(result.getVisibility()).isEqualTo("shared");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException for private credential accessed by non-owner")
        void getCredential_privateNotOwner_throwsException() {
            when(credentialRepository.findById(credentialId))
                    .thenReturn(Optional.of(privateCredential));

            assertThatThrownBy(() -> credentialService.getCredential(credentialId, otherUserId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Credential not found");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException for non-existing credential")
        void getCredential_nonExisting_throwsException() {
            UUID nonExistingId = UUID.randomUUID();
            when(credentialRepository.findById(nonExistingId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> credentialService.getCredential(nonExistingId, userId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Credential not found");
        }
    }

    @Nested
    @DisplayName("createCredential")
    class CreateCredential {

        private CreateCredentialRequest createRequest() {
            CreateCredentialRequest request = new CreateCredentialRequest();
            request.setName("New API Key");
            request.setType("api_key");
            request.setDescription("New test credential");
            request.setVisibility("private");
            request.setData(Map.of("apiKey", "sk-test-12345"));
            return request;
        }

        @Test
        @DisplayName("should create credential successfully")
        void createCredential_success() {
            CreateCredentialRequest request = createRequest();
            EncryptionService.EncryptedData encryptedData =
                    new EncryptionService.EncryptedData(new byte[]{1, 2, 3}, new byte[]{4, 5, 6});

            when(credentialTypeRepository.findByName("api_key"))
                    .thenReturn(Optional.of(apiKeyType));
            when(credentialRepository.existsByNameAndOwnerId("New API Key", userId))
                    .thenReturn(false);
            when(encryptionService.encrypt(anyString()))
                    .thenReturn(encryptedData);
            when(credentialRepository.save(any(Credential.class)))
                    .thenAnswer(invocation -> {
                        Credential saved = invocation.getArgument(0);
                        saved.setId(UUID.randomUUID());
                        saved.setCreatedAt(Instant.now());
                        saved.setUpdatedAt(Instant.now());
                        return saved;
                    });

            CredentialResponse result = credentialService.createCredential(request, userId);

            assertThat(result).isNotNull();
            assertThat(result.getName()).isEqualTo("New API Key");
            assertThat(result.getType()).isEqualTo("api_key");
            assertThat(result.getOwnerId()).isEqualTo(userId);
            assertThat(result.getVisibility()).isEqualTo("private");

            verify(credentialTypeRepository).findByName("api_key");
            verify(credentialRepository).existsByNameAndOwnerId("New API Key", userId);
            verify(encryptionService).encrypt(anyString());
            verify(credentialRepository).save(any(Credential.class));
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for invalid type")
        void createCredential_invalidType_throwsException() {
            CreateCredentialRequest request = createRequest();
            request.setType("invalid_type");

            when(credentialTypeRepository.findByName("invalid_type"))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> credentialService.createCredential(request, userId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid credential type");

            verify(credentialRepository, never()).save(any());
        }

        @Test
        @DisplayName("should throw IllegalArgumentException for duplicate name")
        void createCredential_duplicateName_throwsException() {
            CreateCredentialRequest request = createRequest();

            when(credentialTypeRepository.findByName("api_key"))
                    .thenReturn(Optional.of(apiKeyType));
            when(credentialRepository.existsByNameAndOwnerId("New API Key", userId))
                    .thenReturn(true);

            assertThatThrownBy(() -> credentialService.createCredential(request, userId))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("already exists");

            verify(credentialRepository, never()).save(any());
            verify(encryptionService, never()).encrypt(anyString());
        }

        @Test
        @DisplayName("should create credential with workspace ID")
        void createCredential_withWorkspaceId() {
            CreateCredentialRequest request = createRequest();
            UUID workspaceId = UUID.randomUUID();
            request.setWorkspaceId(workspaceId);
            EncryptionService.EncryptedData encryptedData =
                    new EncryptionService.EncryptedData(new byte[]{1, 2, 3}, new byte[]{4, 5, 6});

            when(credentialTypeRepository.findByName("api_key"))
                    .thenReturn(Optional.of(apiKeyType));
            when(credentialRepository.existsByNameAndOwnerId("New API Key", userId))
                    .thenReturn(false);
            when(encryptionService.encrypt(anyString()))
                    .thenReturn(encryptedData);
            when(credentialRepository.save(any(Credential.class)))
                    .thenAnswer(invocation -> {
                        Credential saved = invocation.getArgument(0);
                        saved.setId(UUID.randomUUID());
                        return saved;
                    });

            CredentialResponse result = credentialService.createCredential(request, userId);

            assertThat(result.getWorkspaceId()).isEqualTo(workspaceId);
        }

        @Test
        @DisplayName("should create credential with shared visibility")
        void createCredential_sharedVisibility() {
            CreateCredentialRequest request = createRequest();
            request.setVisibility("shared");
            EncryptionService.EncryptedData encryptedData =
                    new EncryptionService.EncryptedData(new byte[]{1, 2, 3}, new byte[]{4, 5, 6});

            when(credentialTypeRepository.findByName("api_key"))
                    .thenReturn(Optional.of(apiKeyType));
            when(credentialRepository.existsByNameAndOwnerId("New API Key", userId))
                    .thenReturn(false);
            when(encryptionService.encrypt(anyString()))
                    .thenReturn(encryptedData);
            when(credentialRepository.save(any(Credential.class)))
                    .thenAnswer(invocation -> {
                        Credential saved = invocation.getArgument(0);
                        saved.setId(UUID.randomUUID());
                        return saved;
                    });

            CredentialResponse result = credentialService.createCredential(request, userId);

            assertThat(result.getVisibility()).isEqualTo("shared");
        }
    }

    @Nested
    @DisplayName("deleteCredential")
    class DeleteCredential {

        @Test
        @DisplayName("should delete credential when found by id and owner")
        void deleteCredential_success() {
            when(credentialRepository.findByIdAndOwnerId(credentialId, userId))
                    .thenReturn(Optional.of(privateCredential));

            credentialService.deleteCredential(credentialId, userId);

            verify(credentialRepository).delete(privateCredential);
            verify(activityService).logCredentialAccess(userId, credentialId, "My API Key", "delete");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when credential not found")
        void deleteCredential_notFound_throwsException() {
            UUID nonExistingId = UUID.randomUUID();
            when(credentialRepository.findByIdAndOwnerId(nonExistingId, userId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> credentialService.deleteCredential(nonExistingId, userId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Credential not found");

            verify(credentialRepository, never()).delete(any());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException when not owner")
        void deleteCredential_notOwner_throwsException() {
            when(credentialRepository.findByIdAndOwnerId(credentialId, otherUserId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> credentialService.deleteCredential(credentialId, otherUserId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Credential not found");

            verify(credentialRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("getDecryptedData")
    class GetDecryptedData {

        @Test
        @DisplayName("should return decrypted data for owner")
        void getDecryptedData_success() {
            when(credentialRepository.findById(credentialId))
                    .thenReturn(Optional.of(privateCredential));
            when(encryptionService.decrypt(
                    privateCredential.getEncryptedData(),
                    privateCredential.getEncryptionIv()))
                    .thenReturn("{\"apiKey\":\"sk-test-12345\"}");

            Map<String, Object> result = credentialService.getDecryptedData(credentialId, userId);

            assertThat(result).containsEntry("apiKey", "sk-test-12345");
            verify(encryptionService).decrypt(
                    privateCredential.getEncryptedData(),
                    privateCredential.getEncryptionIv());
            verify(activityService).logCredentialAccess(userId, credentialId, "My API Key", "decrypt");
        }

        @Test
        @DisplayName("should return decrypted data for shared credential accessed by non-owner")
        void getDecryptedData_sharedNonOwner_success() {
            when(credentialRepository.findById(sharedCredential.getId()))
                    .thenReturn(Optional.of(sharedCredential));
            when(encryptionService.decrypt(
                    sharedCredential.getEncryptedData(),
                    sharedCredential.getEncryptionIv()))
                    .thenReturn("{\"apiKey\":\"shared-key\"}");

            Map<String, Object> result = credentialService.getDecryptedData(
                    sharedCredential.getId(), otherUserId);

            assertThat(result).containsEntry("apiKey", "shared-key");
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException for private credential accessed by non-owner")
        void getDecryptedData_privateNotOwner_throwsException() {
            when(credentialRepository.findById(credentialId))
                    .thenReturn(Optional.of(privateCredential));

            assertThatThrownBy(() -> credentialService.getDecryptedData(credentialId, otherUserId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Credential not found");

            verify(encryptionService, never()).decrypt(any(), any());
        }

        @Test
        @DisplayName("should throw ResourceNotFoundException for non-existing credential")
        void getDecryptedData_nonExisting_throwsException() {
            UUID nonExistingId = UUID.randomUUID();
            when(credentialRepository.findById(nonExistingId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> credentialService.getDecryptedData(nonExistingId, userId))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Credential not found");
        }

        @Test
        @DisplayName("should throw RuntimeException when decrypted data is invalid JSON")
        void getDecryptedData_invalidJson_throwsException() {
            when(credentialRepository.findById(credentialId))
                    .thenReturn(Optional.of(privateCredential));
            when(encryptionService.decrypt(
                    privateCredential.getEncryptedData(),
                    privateCredential.getEncryptionIv()))
                    .thenReturn("not-valid-json");

            assertThatThrownBy(() -> credentialService.getDecryptedData(credentialId, userId))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Failed to parse credential data");
        }
    }

    @Nested
    @DisplayName("listCredentialTypes")
    class ListCredentialTypes {

        @Test
        @DisplayName("should return all credential types")
        void listCredentialTypes_returnsAll() {
            CredentialType oauthType = CredentialType.builder()
                    .id(UUID.randomUUID())
                    .name("oauth2")
                    .displayName("OAuth2")
                    .fieldsSchema(Map.of())
                    .build();
            when(credentialTypeRepository.findAll())
                    .thenReturn(List.of(apiKeyType, oauthType));

            List<CredentialType> result = credentialService.listCredentialTypes();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getName()).isEqualTo("api_key");
            assertThat(result.get(1).getName()).isEqualTo("oauth2");
            verify(credentialTypeRepository).findAll();
        }

        @Test
        @DisplayName("should return empty list when no types exist")
        void listCredentialTypes_empty() {
            when(credentialTypeRepository.findAll())
                    .thenReturn(List.of());

            List<CredentialType> result = credentialService.listCredentialTypes();

            assertThat(result).isEmpty();
        }
    }
}
