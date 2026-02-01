package com.aiinpocket.n3n.gateway.controller;

import com.aiinpocket.n3n.gateway.security.AgentPairingService;
import com.aiinpocket.n3n.gateway.security.DeviceKeyStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for agent pairing operations.
 */
@RestController
@RequestMapping("/api/agent")
@RequiredArgsConstructor
@Slf4j
public class AgentPairingController {

    private final AgentPairingService pairingService;
    private final DeviceKeyStore deviceKeyStore;

    /**
     * Initiate a new pairing session (authenticated user only)
     */
    @PostMapping("/pair/initiate")
    public ResponseEntity<?> initiatePairing(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication required"));
        }

        try {
            UUID userId = UUID.fromString(userDetails.getUsername());
            AgentPairingService.PairingInitiation result = pairingService.initiatePairing(userId);

            return ResponseEntity.ok(Map.of(
                "pairingCode", result.pairingCode(),
                "expiresAt", result.expiresAt().toEpochMilli(),
                "expiresIn", 300 // seconds
            ));

        } catch (Exception e) {
            log.error("Failed to initiate pairing", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to initiate pairing"));
        }
    }

    /**
     * Complete pairing (called by agent)
     * This endpoint is unauthenticated - the pairing code serves as authentication
     */
    @PostMapping("/pair/complete")
    public ResponseEntity<?> completePairing(@RequestBody PairCompleteRequest request) {
        try {
            AgentPairingService.PairingRequest pairingRequest = new AgentPairingService.PairingRequest(
                request.pairingCode(),
                request.deviceId(),
                request.deviceName(),
                request.platform(),
                request.devicePublicKey(),
                request.deviceFingerprint(),
                request.externalAddress(),
                request.directConnectionEnabled() != null && request.directConnectionEnabled(),
                request.allowedIps()
            );

            AgentPairingService.PairingResult result = pairingService.completePairing(pairingRequest);

            return ResponseEntity.ok(Map.of(
                "success", true,
                "platformPublicKey", result.platformPublicKey(),
                "platformFingerprint", result.platformFingerprint(),
                "deviceToken", result.deviceToken()
            ));

        } catch (AgentPairingService.PairingException e) {
            return ResponseEntity.badRequest()
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to complete pairing", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to complete pairing"));
        }
    }

    /**
     * List paired devices for the current user
     */
    @GetMapping("/devices")
    public ResponseEntity<?> listDevices(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication required"));
        }

        try {
            UUID userId = UUID.fromString(userDetails.getUsername());
            List<DeviceKeyStore.DeviceKey> devices = deviceKeyStore.getDeviceKeysForUser(userId);

            List<DeviceInfo> deviceInfos = devices.stream()
                .map(d -> new DeviceInfo(
                    d.getDeviceId(),
                    d.getDeviceName(),
                    d.getPlatform(),
                    d.getPairedAt().toEpochMilli(),
                    d.getLastActiveAt().toEpochMilli(),
                    d.isDirectConnectionEnabled(),
                    d.getExternalAddress(),
                    d.isRevoked()
                ))
                .toList();

            return ResponseEntity.ok(Map.of("devices", deviceInfos));

        } catch (Exception e) {
            log.error("Failed to list devices", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to list devices"));
        }
    }

    /**
     * Update device settings
     */
    @PutMapping("/devices/{deviceId}")
    public ResponseEntity<?> updateDevice(
            @PathVariable String deviceId,
            @RequestBody DeviceUpdateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication required"));
        }

        try {
            UUID userId = UUID.fromString(userDetails.getUsername());

            // Verify device belongs to user
            var deviceKey = deviceKeyStore.getDeviceKey(deviceId);
            if (deviceKey.isEmpty() || !deviceKey.get().getUserId().equals(userId)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Device not found"));
            }

            if (request.externalAddress() != null || request.directConnectionEnabled() != null) {
                pairingService.updateDeviceAddress(
                    deviceId,
                    request.externalAddress(),
                    request.directConnectionEnabled() != null && request.directConnectionEnabled()
                );
            }

            return ResponseEntity.ok(Map.of("success", true));

        } catch (Exception e) {
            log.error("Failed to update device", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to update device"));
        }
    }

    /**
     * Unpair a device
     */
    @DeleteMapping("/devices/{deviceId}")
    public ResponseEntity<?> unpairDevice(
            @PathVariable String deviceId,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication required"));
        }

        try {
            UUID userId = UUID.fromString(userDetails.getUsername());
            pairingService.unpairDevice(userId, deviceId);

            return ResponseEntity.ok(Map.of("success", true));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to unpair device", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to unpair device"));
        }
    }

    /**
     * Revoke all devices (emergency logout all agents)
     */
    @PostMapping("/devices/revoke-all")
    public ResponseEntity<?> revokeAllDevices(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication required"));
        }

        try {
            UUID userId = UUID.fromString(userDetails.getUsername());
            pairingService.revokeAllDevices(userId);

            return ResponseEntity.ok(Map.of("success", true));

        } catch (Exception e) {
            log.error("Failed to revoke devices", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to revoke devices"));
        }
    }

    // Request/Response DTOs

    public record PairCompleteRequest(
        String pairingCode,
        String deviceId,
        String deviceName,
        String platform,
        String devicePublicKey,
        String deviceFingerprint,
        String externalAddress,
        Boolean directConnectionEnabled,
        List<String> allowedIps
    ) {}

    public record DeviceUpdateRequest(
        String externalAddress,
        Boolean directConnectionEnabled,
        List<String> allowedIps
    ) {}

    public record DeviceInfo(
        String deviceId,
        String deviceName,
        String platform,
        long pairedAt,
        long lastActiveAt,
        boolean directConnectionEnabled,
        String externalAddress,
        boolean revoked
    ) {}
}
