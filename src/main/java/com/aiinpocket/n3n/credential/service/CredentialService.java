package com.aiinpocket.n3n.credential.service;

import com.aiinpocket.n3n.activity.service.ActivityService;
import com.aiinpocket.n3n.common.constant.Status;
import com.aiinpocket.n3n.common.exception.ResourceNotFoundException;
import com.aiinpocket.n3n.credential.dto.CreateCredentialRequest;
import com.aiinpocket.n3n.credential.dto.CredentialResponse;
import com.aiinpocket.n3n.credential.entity.Credential;
import com.aiinpocket.n3n.credential.entity.CredentialType;
import com.aiinpocket.n3n.credential.repository.CredentialRepository;
import com.aiinpocket.n3n.credential.repository.CredentialTypeRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class CredentialService {

    private final CredentialRepository credentialRepository;
    private final CredentialTypeRepository credentialTypeRepository;
    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;
    private final ActivityService activityService;

    public Page<CredentialResponse> listCredentials(UUID userId, Pageable pageable) {
        return credentialRepository.findAccessibleByUser(userId, pageable)
            .map(CredentialResponse::from);
    }

    public Page<CredentialResponse> listMyCredentials(UUID userId, Pageable pageable) {
        return credentialRepository.findByOwnerId(userId, pageable)
            .map(CredentialResponse::from);
    }

    public CredentialResponse getCredential(UUID id, UUID userId) {
        Credential credential = credentialRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Credential not found: " + id));

        // Check access
        if (!credential.getOwnerId().equals(userId) && Status.Visibility.PRIVATE.equals(credential.getVisibility())) {
            throw new ResourceNotFoundException("Credential not found: " + id);
        }

        return CredentialResponse.from(credential);
    }

    @Transactional
    public CredentialResponse createCredential(CreateCredentialRequest request, UUID userId) {
        // Validate type exists
        credentialTypeRepository.findByName(request.getType())
            .orElseThrow(() -> new IllegalArgumentException("Invalid credential type: " + request.getType()));

        // Check for duplicate name
        if (credentialRepository.existsByNameAndOwnerId(request.getName(), userId)) {
            throw new IllegalArgumentException("Credential with this name already exists");
        }

        // Encrypt the credential data
        String dataJson;
        try {
            dataJson = objectMapper.writeValueAsString(request.getData());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid credential data");
        }

        EncryptionService.EncryptedData encrypted = encryptionService.encrypt(dataJson);

        Credential credential = Credential.builder()
            .name(request.getName())
            .type(request.getType())
            .description(request.getDescription())
            .ownerId(userId)
            .workspaceId(request.getWorkspaceId())
            .visibility(request.getVisibility())
            .encryptedData(encrypted.ciphertext())
            .encryptionIv(encrypted.iv())
            .build();

        credential = credentialRepository.save(credential);
        log.info("Credential created: id={}, type={}, owner={}", credential.getId(), credential.getType(), userId);

        return CredentialResponse.from(credential);
    }

    @Transactional
    public void deleteCredential(UUID id, UUID userId) {
        Credential credential = credentialRepository.findByIdAndOwnerId(id, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Credential not found: " + id));

        credentialRepository.delete(credential);
        activityService.logCredentialAccess(userId, credential.getId(), credential.getName(), "delete");
        log.info("Credential deleted: id={}", id);
    }

    public Map<String, Object> getDecryptedData(UUID id, UUID userId) {
        Credential credential = credentialRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Credential not found: " + id));

        // Check access
        if (!credential.getOwnerId().equals(userId) && Status.Visibility.PRIVATE.equals(credential.getVisibility())) {
            throw new ResourceNotFoundException("Credential not found: " + id);
        }

        // Audit: log credential decryption access
        activityService.logCredentialAccess(userId, credential.getId(), credential.getName(), "decrypt");

        String decryptedJson = encryptionService.decrypt(
            credential.getEncryptedData(),
            credential.getEncryptionIv()
        );

        try {
            return objectMapper.readValue(decryptedJson, Map.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to parse credential data", e);
        }
    }

    public List<CredentialType> listCredentialTypes() {
        return credentialTypeRepository.findAll();
    }
}
