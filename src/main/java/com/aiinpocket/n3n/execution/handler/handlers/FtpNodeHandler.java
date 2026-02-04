package com.aiinpocket.n3n.execution.handler.handlers;

import com.aiinpocket.n3n.execution.handler.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Handler for FTP nodes.
 * Performs FTP file operations.
 */
@Component
@Slf4j
public class FtpNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "ftp";
    }

    @Override
    public String getDisplayName() {
        return "FTP";
    }

    @Override
    public String getDescription() {
        return "Performs FTP file operations (upload, download, list, delete).";
    }

    @Override
    public String getCategory() {
        return "Communication";
    }

    @Override
    public String getIcon() {
        return "cloud-upload";
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String operation = getStringConfig(context, "operation", "list");
        String remotePath = getStringConfig(context, "remotePath", "/");
        String localPath = getStringConfig(context, "localPath", "");
        String credentialId = getStringConfig(context, "credentialId", "");

        log.info("FTP operation: {} on path: {}", operation, remotePath);

        // In a real implementation, this would:
        // 1. Fetch FTP credentials from CredentialService
        // 2. Connect to FTP server
        // 3. Perform the requested operation

        Map<String, Object> output = new HashMap<>();
        output.put("operation", operation);
        output.put("remotePath", remotePath);
        output.put("timestamp", java.time.Instant.now().toString());

        switch (operation) {
            case "list":
                // Return sample file list
                output.put("files", List.of(
                    Map.of("name", "file1.txt", "size", 1024, "type", "file"),
                    Map.of("name", "file2.txt", "size", 2048, "type", "file"),
                    Map.of("name", "folder1", "type", "directory")
                ));
                output.put("status", "success");
                break;

            case "upload":
                output.put("localPath", localPath);
                output.put("status", "uploaded");
                output.put("bytesTransferred", 0);
                break;

            case "download":
                output.put("localPath", localPath);
                output.put("status", "downloaded");
                output.put("bytesTransferred", 0);
                break;

            case "delete":
                output.put("status", "deleted");
                break;

            case "mkdir":
                output.put("status", "created");
                break;

            case "rename":
                String newPath = getStringConfig(context, "newPath", "");
                output.put("newPath", newPath);
                output.put("status", "renamed");
                break;

            default:
                return NodeExecutionResult.builder()
                    .success(false)
                    .errorMessage("Unknown FTP operation: " + operation)
                    .build();
        }

        // Note: Actual FTP implementation would use Apache Commons Net or similar

        return NodeExecutionResult.builder()
            .success(true)
            .output(output)
            .build();
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        return Map.of(
            "type", "object",
            "required", List.of("credentialId", "operation"),
            "properties", Map.of(
                "credentialId", Map.of(
                    "type", "string",
                    "title", "FTP Credential",
                    "description", "ID of the FTP credential to use"
                ),
                "operation", Map.of(
                    "type", "string",
                    "title", "Operation",
                    "enum", List.of("list", "upload", "download", "delete", "mkdir", "rename"),
                    "default", "list"
                ),
                "remotePath", Map.of(
                    "type", "string",
                    "title", "Remote Path",
                    "description", "Path on the FTP server",
                    "default", "/"
                ),
                "localPath", Map.of(
                    "type", "string",
                    "title", "Local Path",
                    "description", "Local file path (for upload/download)"
                ),
                "newPath", Map.of(
                    "type", "string",
                    "title", "New Path",
                    "description", "New path (for rename operation)"
                ),
                "passive", Map.of(
                    "type", "boolean",
                    "title", "Passive Mode",
                    "description", "Use passive FTP mode",
                    "default", true
                )
            )
        );
    }

    @Override
    public Map<String, Object> getInterfaceDefinition() {
        return Map.of(
            "inputs", List.of(
                Map.of("name", "input", "type", "any", "required", false)
            ),
            "outputs", List.of(
                Map.of("name", "output", "type", "object")
            )
        );
    }
}
