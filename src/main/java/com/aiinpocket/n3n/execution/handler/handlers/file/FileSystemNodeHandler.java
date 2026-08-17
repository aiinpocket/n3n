package com.aiinpocket.n3n.execution.handler.handlers.file;

import com.aiinpocket.n3n.execution.handler.AbstractNodeHandler;
import com.aiinpocket.n3n.execution.handler.NodeExecutionContext;
import com.aiinpocket.n3n.execution.handler.NodeExecutionResult;
import com.aiinpocket.n3n.execution.handler.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 檔案系統節點：列出 / 刪除 / 搬移 / 複製檔案、建立資料夾。
 *
 * 官方範本「定期清理任務」用它刪掉過期檔案（olderThanDays）。
 * 路徑安全規則與 read/write file 節點一致：擋 ..、預設禁絕對路徑。
 */
@Component
@Slf4j
public class FileSystemNodeHandler extends AbstractNodeHandler {

    @Override
    public String getType() {
        return "fileSystem";
    }

    @Override
    public String getDisplayName() {
        return "File System";
    }

    @Override
    public String getDescription() {
        return "List, delete, move or copy files and create directories. Supports olderThanDays for cleanup jobs. "
                + "檔案整理：列出、刪除（可只刪超過 N 天的舊檔）、搬移、複製、建立資料夾。";
    }

    @Override
    public String getCategory() {
        return "Files";
    }

    @Override
    public String getIcon() {
        return "folder";
    }

    @Override
    public ValidationResult validateConfig(Map<String, Object> config) {
        Object path = config.get("path");
        if (path == null || path.toString().isBlank()) {
            return ValidationResult.invalid("path", "Path is required");
        }
        return ValidationResult.valid();
    }

    @Override
    protected NodeExecutionResult doExecute(NodeExecutionContext context) {
        String operation = getStringConfig(context, "operation", "list");
        String rawPath = getStringConfig(context, "path", "");

        if (rawPath.isBlank()) {
            return NodeExecutionResult.failure("Path is required — 請填要處理的資料夾或檔案路徑");
        }
        if (rawPath.contains("..")) {
            return NodeExecutionResult.failure("Path traversal is not allowed");
        }

        Path path = Paths.get(rawPath).normalize();
        if (path.isAbsolute() && !getBooleanConfig(context, "allowAbsolutePaths", false)) {
            return NodeExecutionResult.failure("Absolute paths are not allowed");
        }

        try {
            return switch (operation) {
                case "list" -> listFiles(context, path);
                case "delete" -> deleteFiles(context, path);
                case "move" -> moveOrCopy(context, path, true);
                case "copy" -> moveOrCopy(context, path, false);
                case "exists" -> exists(path);
                case "mkdir" -> mkdir(path);
                default -> NodeExecutionResult.failure("Unknown operation: " + operation);
            };
        } catch (IOException e) {
            log.warn("File system operation {} failed on {}: {}", operation, path, e.getMessage());
            return NodeExecutionResult.failure("File operation failed: " + sanitizeErrorMessage(e.getMessage()));
        }
    }

    private NodeExecutionResult listFiles(NodeExecutionContext context, Path path) throws IOException {
        if (!Files.exists(path)) {
            return NodeExecutionResult.failure("Path does not exist: " + path);
        }
        String pattern = getStringConfig(context, "pattern", "");
        PathMatcher matcher = pattern.isBlank()
                ? null
                : FileSystems.getDefault().getPathMatcher("glob:" + pattern);

        List<Map<String, Object>> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            for (Path p : stream) {
                if (matcher != null && !matcher.matches(p.getFileName())) {
                    continue;
                }
                BasicFileAttributes attrs = Files.readAttributes(p, BasicFileAttributes.class);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("name", p.getFileName().toString());
                entry.put("path", p.toString());
                entry.put("directory", attrs.isDirectory());
                entry.put("size", attrs.size());
                entry.put("modifiedAt", attrs.lastModifiedTime().toInstant().toString());
                files.add(entry);
            }
        }
        return NodeExecutionResult.success(Map.of("files", files, "count", files.size()));
    }

    private NodeExecutionResult deleteFiles(NodeExecutionContext context, Path path) throws IOException {
        if (!Files.exists(path)) {
            // 清理任務對「已經不存在」不該報錯，回報刪了 0 個即可
            return NodeExecutionResult.success(Map.of("deleted", 0, "paths", List.of()));
        }

        int olderThanDays = getIntConfig(context, "olderThanDays", 0);
        String pattern = getStringConfig(context, "pattern", "");
        Instant cutoff = olderThanDays > 0 ? Instant.now().minus(olderThanDays, ChronoUnit.DAYS) : null;
        PathMatcher matcher = pattern.isBlank()
                ? null
                : FileSystems.getDefault().getPathMatcher("glob:" + pattern);

        List<String> deleted = new ArrayList<>();
        if (Files.isDirectory(path)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                for (Path p : stream) {
                    if (Files.isDirectory(p)) {
                        continue; // 只清檔案，不遞迴刪資料夾——清理任務誤刪整棵樹的代價太高
                    }
                    if (matcher != null && !matcher.matches(p.getFileName())) {
                        continue;
                    }
                    if (cutoff != null
                            && Files.getLastModifiedTime(p).toInstant().isAfter(cutoff)) {
                        continue;
                    }
                    Files.delete(p);
                    deleted.add(p.toString());
                }
            }
        } else {
            if (cutoff == null || Files.getLastModifiedTime(path).toInstant().isBefore(cutoff)) {
                Files.delete(path);
                deleted.add(path.toString());
            }
        }
        return NodeExecutionResult.success(Map.of("deleted", deleted.size(), "paths", deleted));
    }

    private NodeExecutionResult moveOrCopy(NodeExecutionContext context, Path source, boolean move) throws IOException {
        String targetRaw = getStringConfig(context, "targetPath", "");
        if (targetRaw.isBlank()) {
            return NodeExecutionResult.failure("targetPath is required for " + (move ? "move" : "copy"));
        }
        if (targetRaw.contains("..")) {
            return NodeExecutionResult.failure("Path traversal is not allowed");
        }
        Path target = Paths.get(targetRaw).normalize();
        if (target.isAbsolute() && !getBooleanConfig(context, "allowAbsolutePaths", false)) {
            return NodeExecutionResult.failure("Absolute paths are not allowed");
        }
        if (target.getParent() != null) {
            Files.createDirectories(target.getParent());
        }
        if (move) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        } else {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return NodeExecutionResult.success(Map.of("from", source.toString(), "to", target.toString()));
    }

    private NodeExecutionResult exists(Path path) {
        boolean exists = Files.exists(path);
        return NodeExecutionResult.success(Map.of("exists", exists, "path", path.toString()));
    }

    private NodeExecutionResult mkdir(Path path) throws IOException {
        Files.createDirectories(path);
        return NodeExecutionResult.success(Map.of("created", true, "path", path.toString()));
    }

    @Override
    public Map<String, Object> getConfigSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("operation", Map.of(
                "type", "string",
                "title", "Operation",
                "enum", List.of("list", "delete", "move", "copy", "exists", "mkdir"),
                "default", "list"
        ));
        properties.put("path", Map.of(
                "type", "string",
                "title", "Path",
                "description", "File or directory to operate on. 要處理的檔案或資料夾"
        ));
        properties.put("targetPath", Map.of(
                "type", "string",
                "title", "Target Path",
                "description", "Destination for move/copy. 搬移或複製的目的地"
        ));
        properties.put("pattern", Map.of(
                "type", "string",
                "title", "Filename Pattern",
                "description", "Glob filter such as *.log (list/delete only). 檔名過濾，例如 *.log"
        ));
        properties.put("olderThanDays", Map.of(
                "type", "integer",
                "title", "Older Than (days)",
                "description", "delete only: keep files newer than N days. 只刪除超過 N 天沒動過的檔案"
        ));
        return Map.of(
                "type", "object",
                "properties", properties,
                "required", List.of("path")
        );
    }
}
