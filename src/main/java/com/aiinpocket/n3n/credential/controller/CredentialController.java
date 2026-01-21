package com.aiinpocket.n3n.credential.controller;

import com.aiinpocket.n3n.credential.dto.CreateCredentialRequest;
import com.aiinpocket.n3n.credential.dto.CredentialResponse;
import com.aiinpocket.n3n.credential.entity.CredentialType;
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
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/credentials")
@RequiredArgsConstructor
public class CredentialController {

    private final CredentialService credentialService;

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
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(credentialService.createCredential(request, userId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCredential(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        credentialService.deleteCredential(id, userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/data")
    public ResponseEntity<Map<String, Object>> getCredentialData(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {
        UUID userId = UUID.fromString(userDetails.getUsername());
        return ResponseEntity.ok(credentialService.getDecryptedData(id, userId));
    }

    @GetMapping("/types")
    public ResponseEntity<List<CredentialType>> listCredentialTypes() {
        return ResponseEntity.ok(credentialService.listCredentialTypes());
    }
}
