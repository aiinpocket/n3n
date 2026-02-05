package com.aiinpocket.n3n.ai.rag.document.loaders;

import com.aiinpocket.n3n.ai.rag.document.Document;
import com.aiinpocket.n3n.ai.rag.document.DocumentLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 純文字檔案載入器
 *
 * 支援 .txt, .md, .csv, .json, .xml 等文字檔案。
 */
@Component
@Slf4j
public class TextLoader implements DocumentLoader {

    private static final List<String> SUPPORTED_EXTENSIONS = List.of(
            "txt", "md", "markdown", "csv", "json", "xml", "yaml", "yml", "html", "htm"
    );

    @Override
    public List<Document> load(String filePath) {
        try {
            Path path = Path.of(filePath);
            String content = Files.readString(path, StandardCharsets.UTF_8);

            Document doc = Document.of(content, Map.of(
                    "source", filePath,
                    "type", "text",
                    "filename", path.getFileName().toString()
            ));

            log.debug("Loaded text document from {}: {} chars", filePath, content.length());
            return List.of(doc);

        } catch (Exception e) {
            log.error("Failed to load text document from {}", filePath, e);
            throw new RuntimeException("Failed to load document: " + e.getMessage(), e);
        }
    }

    @Override
    public List<Document> load(InputStream inputStream, String sourceName) {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            String content = reader.lines().collect(Collectors.joining("\n"));

            Document doc = Document.of(content, Map.of(
                    "source", sourceName,
                    "type", "text"
            ));

            log.debug("Loaded text document from stream {}: {} chars", sourceName, content.length());
            return List.of(doc);

        } catch (Exception e) {
            log.error("Failed to load text document from stream {}", sourceName, e);
            throw new RuntimeException("Failed to load document: " + e.getMessage(), e);
        }
    }

    @Override
    public List<String> getSupportedExtensions() {
        return SUPPORTED_EXTENSIONS;
    }
}
