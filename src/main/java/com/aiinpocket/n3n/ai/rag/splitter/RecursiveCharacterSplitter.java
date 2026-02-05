package com.aiinpocket.n3n.ai.rag.splitter;

import com.aiinpocket.n3n.ai.rag.document.Document;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 遞迴字元分割器
 *
 * 類似 LangChain 的 RecursiveCharacterTextSplitter。
 * 嘗試按照分隔符的層級依序分割文字，確保語義完整性。
 */
@Component
@Slf4j
public class RecursiveCharacterSplitter implements TextSplitter {

    private static final List<String> DEFAULT_SEPARATORS = List.of(
            "\n\n",     // 段落分隔
            "\n",       // 換行
            ". ",       // 句號
            "。",       // 中文句號
            "! ",       // 驚嘆號
            "？",       // 中文問號
            "? ",       // 問號
            "; ",       // 分號
            "；",       // 中文分號
            ", ",       // 逗號
            "，",       // 中文逗號
            " ",        // 空格
            ""          // 字元
    );

    private final int chunkSize;
    private final int chunkOverlap;
    private final List<String> separators;

    public RecursiveCharacterSplitter() {
        this(1000, 200, DEFAULT_SEPARATORS);
    }

    @Builder
    public RecursiveCharacterSplitter(int chunkSize, int chunkOverlap, List<String> separators) {
        this.chunkSize = chunkSize > 0 ? chunkSize : 1000;
        this.chunkOverlap = Math.min(chunkOverlap, this.chunkSize / 2);
        this.separators = separators != null ? separators : DEFAULT_SEPARATORS;
    }

    @Override
    public List<Document> split(Document document) {
        if (document == null || document.getContent() == null) {
            return List.of();
        }

        List<String> chunks = splitText(document.getContent());
        List<Document> result = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> metadata = new HashMap<>();
            if (document.getMetadata() != null) {
                metadata.putAll(document.getMetadata());
            }
            metadata.put("chunk_index", i);
            metadata.put("chunk_count", chunks.size());

            result.add(Document.builder()
                    .content(chunks.get(i))
                    .metadata(metadata)
                    .build());
        }

        log.debug("Split document into {} chunks (chunk_size={}, overlap={})",
                result.size(), chunkSize, chunkOverlap);
        return result;
    }

    @Override
    public List<String> splitText(String text) {
        if (text == null || text.isEmpty()) {
            return List.of();
        }

        return recursiveSplit(text, separators);
    }

    private List<String> recursiveSplit(String text, List<String> seps) {
        List<String> finalChunks = new ArrayList<>();

        // 找到適用的分隔符
        String separator = "";
        List<String> remainingSeps = new ArrayList<>();

        for (int i = 0; i < seps.size(); i++) {
            String sep = seps.get(i);
            if (sep.isEmpty()) {
                separator = sep;
                break;
            }
            if (text.contains(sep)) {
                separator = sep;
                remainingSeps = seps.subList(i + 1, seps.size());
                break;
            }
        }

        // 使用分隔符分割
        String[] splits;
        if (separator.isEmpty()) {
            // 按字元分割
            splits = text.split("");
        } else {
            splits = text.split(java.util.regex.Pattern.quote(separator), -1);
        }

        // 合併小片段
        List<String> goodSplits = new ArrayList<>();
        String currentSep = separator.isEmpty() ? "" : separator;

        for (String split : splits) {
            if (split.length() < chunkSize) {
                goodSplits.add(split);
            } else {
                // 片段太大，需要進一步分割
                if (!goodSplits.isEmpty()) {
                    List<String> merged = mergeSplits(goodSplits, currentSep);
                    finalChunks.addAll(merged);
                    goodSplits.clear();
                }

                if (remainingSeps.isEmpty()) {
                    finalChunks.add(split);
                } else {
                    List<String> subChunks = recursiveSplit(split, remainingSeps);
                    finalChunks.addAll(subChunks);
                }
            }
        }

        if (!goodSplits.isEmpty()) {
            List<String> merged = mergeSplits(goodSplits, currentSep);
            finalChunks.addAll(merged);
        }

        return finalChunks;
    }

    private List<String> mergeSplits(List<String> splits, String separator) {
        List<String> docs = new ArrayList<>();
        List<String> currentDoc = new ArrayList<>();
        int total = 0;

        for (String split : splits) {
            int len = split.length();

            if (total + len + (currentDoc.isEmpty() ? 0 : separator.length()) > chunkSize) {
                if (!currentDoc.isEmpty()) {
                    String doc = String.join(separator, currentDoc);
                    if (!doc.isBlank()) {
                        docs.add(doc.trim());
                    }

                    // 保留 overlap 部分
                    while (total > chunkOverlap || (total + len > chunkSize && total > 0)) {
                        total -= currentDoc.get(0).length();
                        if (!currentDoc.isEmpty() && !separator.isEmpty()) {
                            total -= separator.length();
                        }
                        currentDoc.remove(0);
                    }
                }
            }

            currentDoc.add(split);
            total += len + (currentDoc.size() > 1 ? separator.length() : 0);
        }

        if (!currentDoc.isEmpty()) {
            String doc = String.join(separator, currentDoc);
            if (!doc.isBlank()) {
                docs.add(doc.trim());
            }
        }

        return docs;
    }

    /**
     * 建立自訂配置的分割器
     */
    public static RecursiveCharacterSplitter withConfig(int chunkSize, int chunkOverlap) {
        return RecursiveCharacterSplitter.builder()
                .chunkSize(chunkSize)
                .chunkOverlap(chunkOverlap)
                .separators(DEFAULT_SEPARATORS)
                .build();
    }
}
