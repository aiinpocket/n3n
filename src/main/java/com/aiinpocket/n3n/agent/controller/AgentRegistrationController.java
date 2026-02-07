package com.aiinpocket.n3n.agent.controller;

import com.aiinpocket.n3n.agent.entity.AgentRegistration;
import com.aiinpocket.n3n.agent.service.AgentRegistrationService;
import com.aiinpocket.n3n.agent.service.GatewaySettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Agent registration management REST Controller
 */
@RestController
@RequestMapping("/api/agents")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Agent Registration", description = "Agent registration management")
public class AgentRegistrationController {

    private final AgentRegistrationService registrationService;
    private final GatewaySettingsService gatewaySettingsService;
    private final ObjectMapper objectMapper;

    /**
     * Generate new Agent registration token.
     * Returns JSON config file download.
     */
    @PostMapping("/tokens")
    public ResponseEntity<?> generateToken(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication required"));
        }

        try {
            UUID userId = UUID.fromString(userDetails.getUsername());
            AgentRegistrationService.TokenGenerationResult result =
                registrationService.generateToken(userId);

            // Return as downloadable JSON file
            String configJson = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(result.config());

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setContentDispositionFormData("attachment", "n3n-agent-config.json");

            return ResponseEntity.ok()
                .headers(headers)
                .body(configJson.getBytes(StandardCharsets.UTF_8));

        } catch (Exception e) {
            log.error("Failed to generate token", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to generate token"));
        }
    }

    /**
     * Generate token and return JSON response (no download)
     */
    @PostMapping("/tokens/json")
    public ResponseEntity<?> generateTokenJson(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication required"));
        }

        try {
            UUID userId = UUID.fromString(userDetails.getUsername());
            AgentRegistrationService.TokenGenerationResult result =
                registrationService.generateToken(userId);

            return ResponseEntity.ok(Map.of(
                "registrationId", result.registrationId(),
                "agentId", result.agentId(),
                "config", result.config()
            ));

        } catch (Exception e) {
            log.error("Failed to generate token", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to generate token"));
        }
    }

    /**
     * Generate one-click install command.
     * User copies and pastes this command to install.
     */
    @PostMapping("/install-command")
    public ResponseEntity<?> generateInstallCommand(
            @AuthenticationPrincipal UserDetails userDetails,
            @RequestHeader(value = "X-Forwarded-Host", required = false) String forwardedHost,
            @RequestHeader(value = "Host", required = false) String host) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication required"));
        }

        try {
            UUID userId = UUID.fromString(userDetails.getUsername());
            AgentRegistrationService.TokenGenerationResult result =
                registrationService.generateToken(userId);

            // Determine base URL
            String baseUrl = forwardedHost != null ? forwardedHost : (host != null ? host : "localhost:8080");
            String protocol = baseUrl.contains("localhost") ? "http" : "https";

            String installCommand = String.format(
                "curl -fsSL %s://%s/api/agents/install.sh?token=%s | bash",
                protocol, baseUrl, result.config().registration().token()
            );

            return ResponseEntity.ok(Map.of(
                "command", installCommand,
                "registrationId", result.registrationId(),
                "agentId", result.agentId()
            ));

        } catch (Exception e) {
            log.error("Failed to generate install command", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to generate install command"));
        }
    }

    /**
     * Install script - user runs curl ... | bash
     */
    @GetMapping(value = "/install.sh", produces = "text/plain")
    public ResponseEntity<String> getInstallScript(
            @RequestParam String token,
            @RequestHeader(value = "X-Forwarded-Host", required = false) String forwardedHost,
            @RequestHeader(value = "Host", required = false) String host) {

        String baseUrl = forwardedHost != null ? forwardedHost : (host != null ? host : "localhost:8080");
        String protocol = baseUrl.contains("localhost") ? "http" : "https";
        String serverUrl = protocol + "://" + baseUrl;

        String script = """
            #!/bin/bash
            set -e

            echo ""
            echo "╔══════════════════════════════════════════════════════════════╗"
            echo "║              N3N Agent Installer                             ║"
            echo "╚══════════════════════════════════════════════════════════════╝"
            echo ""

            # Detect system
            OS="$(uname -s)"
            ARCH="$(uname -m)"

            if [ "$OS" != "Darwin" ]; then
                echo "Error: Only macOS is currently supported"
                exit 1
            fi

            # Stop existing agent
            pkill -9 -f n3n-agent 2>/dev/null || true

            # Clean old data
            rm -rf "$HOME/.n3n-agent" 2>/dev/null
            rm -rf "$HOME/Library/Application Support/N3N Agent" 2>/dev/null

            # Install directory
            INSTALL_DIR="$HOME/.n3n-agent"
            mkdir -p "$INSTALL_DIR"

            echo "Downloading Agent..."
            curl -fsSL "%s/api/agents/binary/macos" -o "$INSTALL_DIR/n3n-agent"
            chmod +x "$INSTALL_DIR/n3n-agent"

            echo "Writing configuration..."
            curl -fsSL "%s/api/agents/config?token=%s" -o "$INSTALL_DIR/n3n-agent-config.json"

            echo "Starting Agent..."
            cd "$INSTALL_DIR"
            ./n3n-agent run --log-level info &

            echo ""
            echo "Installation complete! Agent is running in background."
            echo ""
            echo "Install path: $INSTALL_DIR"
            echo "Check status: $INSTALL_DIR/n3n-agent status"
            echo "Stop agent:   pkill -f n3n-agent"
            echo ""
            """.formatted(serverUrl, serverUrl, token);

        return ResponseEntity.ok(script);
    }

    /**
     * Download Agent binary
     */
    @GetMapping("/binary/{platform}")
    public ResponseEntity<?> downloadBinary(@PathVariable String platform) {
        try {
            String resourcePath;
            if ("macos".equalsIgnoreCase(platform)) {
                resourcePath = "static/downloads/N3N Agent.app/Contents/MacOS/n3n-agent-bin";
            } else {
                return ResponseEntity.badRequest().body("Unsupported platform");
            }

            ClassPathResource resource = new ClassPathResource(resourcePath);
            if (!resource.exists()) {
                return ResponseEntity.notFound().build();
            }

            byte[] content = resource.getInputStream().readAllBytes();

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);

            return ResponseEntity.ok().headers(headers).body(content);

        } catch (Exception e) {
            log.error("Failed to download binary", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Download Agent config
     */
    @GetMapping("/config")
    public ResponseEntity<?> downloadConfig(@RequestParam String token) {
        try {
            // Validate token and get config
            var registration = registrationService.getRegistrationByToken(token);
            if (registration == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "Invalid token"));
            }

            var config = registrationService.generateConfigForToken(token);
            String configJson = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(config);

            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(configJson);

        } catch (Exception e) {
            log.error("Failed to get config", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to get config"));
        }
    }

    /**
     * Token-based agent registration (public endpoint for one-click install)
     * Called by Agent after downloading config file
     */
    @PostMapping("/register")
    public ResponseEntity<?> registerWithToken(@Valid @RequestBody TokenRegistrationRequest request) {
        try {
            log.info("Agent registration request: deviceId={}, platform={}",
                request.deviceId(), request.platform());

            // Validate token and get registration
            var registration = registrationService.getRegistrationByToken(request.token());
            if (registration == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                        "success", false,
                        "error", "Invalid or expired token"
                    ));
            }

            // Complete registration
            var result = registrationService.completeTokenRegistration(
                request.token(),
                request.deviceId(),
                request.deviceName(),
                request.platform(),
                request.devicePublicKey(),
                request.deviceFingerprint()
            );

            if (result == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of(
                        "success", false,
                        "error", "Registration failed"
                    ));
            }

            return ResponseEntity.ok(Map.of(
                "success", true,
                "platformPublicKey", result.platformPublicKey(),
                "platformFingerprint", result.platformFingerprint(),
                "deviceToken", result.deviceToken()
            ));

        } catch (Exception e) {
            log.error("Failed to register agent", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                    "success", false,
                    "error", "Registration failed: " + e.getMessage()
                ));
        }
    }

    // Token registration request DTO
    public record TokenRegistrationRequest(
        String token,
        String deviceId,
        String deviceName,
        String platform,
        String devicePublicKey,
        String deviceFingerprint
    ) {}

    /**
     * One-click download: generates DMG for macOS, ZIP for Windows
     */
    @PostMapping("/download/{platform}")
    public ResponseEntity<?> downloadAgentPackage(
            @PathVariable String platform,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication required"));
        }

        try {
            UUID userId = UUID.fromString(userDetails.getUsername());
            AgentRegistrationService.TokenGenerationResult result =
                registrationService.generateToken(userId);

            // Generate config JSON
            String configJson = objectMapper.writerWithDefaultPrettyPrinter()
                .writeValueAsString(result.config());

            if ("macos".equalsIgnoreCase(platform)) {
                return createMacOSDmg(userId, configJson);
            } else if ("windows".equalsIgnoreCase(platform)) {
                return createWindowsZip(userId, configJson);
            } else {
                return ResponseEntity.badRequest()
                    .body(Map.of("error", "Unsupported platform: " + platform));
            }

        } catch (Exception e) {
            log.error("Failed to generate agent package", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to generate agent package: " + e.getMessage()));
        }
    }

    /**
     * Create macOS DMG file
     */
    private ResponseEntity<?> createMacOSDmg(UUID userId, String configJson) throws IOException, InterruptedException {
        Path tempDir = Files.createTempDirectory("n3n-agent-");
        Path dmgContentDir = tempDir.resolve("N3N Agent");
        Files.createDirectories(dmgContentDir);

        try {
            // Copy Agent.app
            ClassPathResource agentApp = new ClassPathResource("static/downloads/N3N Agent.app");
            Path agentAppDest = dmgContentDir.resolve("N3N Agent.app");
            copyDirectory(agentApp.getFile().toPath(), agentAppDest);

            // Set executable permissions
            Path macosDir = agentAppDest.resolve("Contents/MacOS");
            Files.setPosixFilePermissions(macosDir.resolve("n3n-agent"),
                java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));
            Files.setPosixFilePermissions(macosDir.resolve("n3n-agent-bin"),
                java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x"));

            // Write config file
            Files.writeString(dmgContentDir.resolve("n3n-agent-config.json"), configJson);

            // Create DMG using hdiutil
            Path dmgPath = tempDir.resolve("N3N-Agent.dmg");
            ProcessBuilder pb = new ProcessBuilder(
                "hdiutil", "create",
                "-volname", "N3N Agent",
                "-srcfolder", dmgContentDir.toString(),
                "-ov",
                "-format", "UDZO",
                dmgPath.toString()
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                log.error("hdiutil failed: {}", output);
                throw new IOException("Failed to create DMG: " + output);
            }

            // Read DMG content
            byte[] dmgContent = Files.readAllBytes(dmgPath);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
            headers.setContentDispositionFormData("attachment", "N3N-Agent.dmg");

            log.info("Generated DMG for user {}", userId);

            return ResponseEntity.ok()
                .headers(headers)
                .body(dmgContent);

        } finally {
            // Cleanup temp directory
            deleteDirectory(tempDir);
        }
    }

    /**
     * Create Windows ZIP file
     */
    private ResponseEntity<?> createWindowsZip(UUID userId, String configJson) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            addFileToZip(zos, "static/downloads/n3n-agent-windows-x64.exe",
                "N3N Agent/n3n-agent.exe");

            ZipEntry configEntry = new ZipEntry("N3N Agent/n3n-agent-config.json");
            zos.putNextEntry(configEntry);
            zos.write(configJson.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);
        headers.setContentDispositionFormData("attachment", "N3N-Agent-windows.zip");

        log.info("Generated Windows ZIP for user {}", userId);

        return ResponseEntity.ok()
            .headers(headers)
            .body(baos.toByteArray());
    }

    /**
     * Copy directory recursively
     */
    private void copyDirectory(Path source, Path target) throws IOException {
        Files.walk(source).forEach(src -> {
            try {
                Path dest = target.resolve(source.relativize(src));
                if (Files.isDirectory(src)) {
                    Files.createDirectories(dest);
                } else {
                    Files.copy(src, dest);
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * Delete directory recursively
     */
    private void deleteDirectory(Path dir) {
        try {
            Files.walk(dir)
                .sorted((a, b) -> b.compareTo(a))
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // ignore
                    }
                });
        } catch (IOException e) {
            // ignore
        }
    }

    /**
     * Add .app bundle to ZIP (recursively)
     */
    private void addAppBundleToZip(ZipOutputStream zos, String appPath) throws IOException {
        ClassPathResource appResource = new ClassPathResource(appPath);
        if (!appResource.exists()) {
            throw new IOException("App bundle not found: " + appPath);
        }

        Path appDir = appResource.getFile().toPath();
        String baseName = appDir.getFileName().toString();

        Files.walk(appDir)
            .filter(path -> !Files.isDirectory(path))
            .forEach(path -> {
                try {
                    String relativePath = baseName + "/" + appDir.relativize(path).toString();
                    ZipEntry entry = new ZipEntry(relativePath);

                    // Preserve executable permission for binary files
                    if (relativePath.contains("MacOS/")) {
                        entry.setExtra(new byte[]{0x75, 0x78, 0x0b, 0x00, 0x01, 0x04, (byte)0xf5, 0x01, 0x00, 0x00, 0x04, 0x14, 0x00, 0x00, 0x00});
                    }

                    zos.putNextEntry(entry);
                    Files.copy(path, zos);
                    zos.closeEntry();
                } catch (IOException e) {
                    throw new RuntimeException("Failed to add file to zip: " + path, e);
                }
            });
    }

    /**
     * Add single file to ZIP
     */
    private void addFileToZip(ZipOutputStream zos, String resourcePath, String entryName) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        if (!resource.exists()) {
            log.warn("Resource not found: {}", resourcePath);
            return;
        }

        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        try (InputStream is = resource.getInputStream()) {
            is.transferTo(zos);
        }
        zos.closeEntry();
    }

    /**
     * List all Agent registrations
     */
    @GetMapping("/registrations")
    public ResponseEntity<?> listRegistrations(@AuthenticationPrincipal UserDetails userDetails) {
        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication required"));
        }

        try {
            UUID userId = UUID.fromString(userDetails.getUsername());
            List<AgentRegistration> registrations = registrationService.getRegistrations(userId);

            List<RegistrationInfo> infos = registrations.stream()
                .map(this::toInfo)
                .toList();

            return ResponseEntity.ok(Map.of("registrations", infos));

        } catch (Exception e) {
            log.error("Failed to list registrations", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to list registrations"));
        }
    }

    /**
     * Block Agent
     */
    @PutMapping("/{id}/block")
    public ResponseEntity<?> blockAgent(
            @PathVariable UUID id,
            @RequestBody(required = false) BlockRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication required"));
        }

        try {
            UUID userId = UUID.fromString(userDetails.getUsername());
            String reason = request != null ? request.reason() : "Blocked by user";
            registrationService.blockAgent(userId, id, reason);

            return ResponseEntity.ok(Map.of("success", true));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to block agent", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to block agent"));
        }
    }

    /**
     * Unblock Agent
     */
    @PutMapping("/{id}/unblock")
    public ResponseEntity<?> unblockAgent(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication required"));
        }

        try {
            UUID userId = UUID.fromString(userDetails.getUsername());
            registrationService.unblockAgent(userId, id);

            return ResponseEntity.ok(Map.of("success", true));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to unblock agent", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to unblock agent"));
        }
    }

    /**
     * Delete Agent registration
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRegistration(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Authentication required"));
        }

        try {
            UUID userId = UUID.fromString(userDetails.getUsername());
            registrationService.deleteRegistration(userId, id);

            return ResponseEntity.ok(Map.of("success", true));

        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to delete registration", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to delete registration"));
        }
    }

    private RegistrationInfo toInfo(AgentRegistration reg) {
        return new RegistrationInfo(
            reg.getId(),
            reg.getDeviceId(),
            reg.getDeviceName(),
            reg.getPlatform(),
            reg.getStatus().name(),
            reg.getCreatedAt().toEpochMilli(),
            reg.getRegisteredAt() != null ? reg.getRegisteredAt().toEpochMilli() : null,
            reg.getBlockedAt() != null ? reg.getBlockedAt().toEpochMilli() : null,
            reg.getBlockedReason(),
            reg.getLastSeenAt() != null ? reg.getLastSeenAt().toEpochMilli() : null
        );
    }

    // Request/Response DTOs

    public record BlockRequest(String reason) {}

    public record RegistrationInfo(
        UUID id,
        String deviceId,
        String deviceName,
        String platform,
        String status,
        long createdAt,
        Long registeredAt,
        Long blockedAt,
        String blockedReason,
        Long lastSeenAt
    ) {}
}
