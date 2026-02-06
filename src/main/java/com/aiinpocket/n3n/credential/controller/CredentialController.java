package com.aiinpocket.n3n.credential.controller;

import com.aiinpocket.n3n.activity.service.ActivityService;
import com.aiinpocket.n3n.credential.dto.ConnectionTestResult;
import com.aiinpocket.n3n.credential.dto.CreateCredentialRequest;
import com.aiinpocket.n3n.credential.dto.CredentialResponse;
import com.aiinpocket.n3n.credential.dto.TestCredentialRequest;
import com.aiinpocket.n3n.credential.entity.CredentialType;
import com.aiinpocket.n3n.credential.service.ConnectionTestService;
import com.aiinpocket.n3n.credential.service.CredentialService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/credentials")
@RequiredArgsConstructor
@Tag(name = "Credentials", description = "Credential management")
public class CredentialController {

    private final CredentialService credentialService;
    private final ConnectionTestService connectionTestService;
    private final ActivityService activityService;

    @GetMapping
    public ResponseEntity<Page<CredentialResponse>> listCredentials(
            @RequestParam(defaultValue = "false") boolean onlyMine,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        if (onlyMine) {
            return ResponseEntity.ok(credentialService.listMyCredentials(userId, pageable));
        }
        return ResponseEntity.ok(credentialService.listCredentials(userId, pageable));
    }

    @GetMapping("/mine")
    public ResponseEntity<Page<CredentialResponse>> listMyCredentials(
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(credentialService.listMyCredentials(userId, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CredentialResponse> getCredential(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(credentialService.getCredential(id, userId));
    }

    @PostMapping
    public ResponseEntity<CredentialResponse> createCredential(
            @Valid @RequestBody CreateCredentialRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        CredentialResponse response = credentialService.createCredential(request, userId);
        activityService.logCredentialCreate(userId, response.getId(), response.getName(), response.getType());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCredential(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        // Get credential info before deleting for audit log
        CredentialResponse credential = credentialService.getCredential(id, userId);
        credentialService.deleteCredential(id, userId);
        activityService.logCredentialDelete(userId, id, credential.getName());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/data")
    public ResponseEntity<Map<String, Object>> getCredentialData(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        // Get credential info for audit log
        CredentialResponse credential = credentialService.getCredential(id, userId);
        Map<String, Object> data = credentialService.getDecryptedData(id, userId);
        activityService.logCredentialAccess(userId, id, credential.getName(), "api");
        return ResponseEntity.ok(data);
    }

    @GetMapping("/types")
    public ResponseEntity<List<CredentialType>> listCredentialTypes() {
        return ResponseEntity.ok(credentialService.listCredentialTypes());
    }

    /**
     * Test an unsaved credential connection
     * This allows testing connection before saving the credential
     */
    @PostMapping("/test")
    public ResponseEntity<ConnectionTestResult> testConnection(
            @Valid @RequestBody TestCredentialRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {
        ConnectionTestResult result = connectionTestService.testConnection(
                request.getType(), request.getData());
        return ResponseEntity.ok(result);
    }

    /**
     * Test a saved credential connection
     * This tests the connection and updates the credential's metadata with the result
     */
    @PostMapping("/{id}/test")
    public ResponseEntity<ConnectionTestResult> testSavedCredential(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        ConnectionTestResult result = connectionTestService.testSavedCredential(id, userId);
        return ResponseEntity.ok(result);
    }
}
