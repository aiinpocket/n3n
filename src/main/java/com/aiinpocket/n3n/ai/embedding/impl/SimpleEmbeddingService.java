package com.aiinpocket.n3n.ai.embedding.impl;

import com.aiinpocket.n3n.ai.embedding.EmbeddingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/**
 * 簡單嵌入服務 - 後備實作
 *
 * 當沒有配置 AI 提供者時使用的簡化嵌入實作。
 * 使用字元頻率 + 雜湊來產生固定維度的向量。
 *
 * 注意：此實作不具備語義理解能力，僅作為後備方案。
 * 建議在生產環境使用 OpenAI 或 Ollama 的真正嵌入模型。
 *
 * 配置方式：
 * - n3n.embedding.provider=simple
 */
@Service
@ConditionalOnProperty(name = "n3n.embedding.provider", havingValue = "simple")
@Slf4j
public class SimpleEmbeddingService implements EmbeddingService {

    private static final int DIMENSION = 256;

    public SimpleEmbeddingService() {
        log.warn("Using SimpleEmbeddingService - this is a fallback implementation without semantic understanding. " +
                "Consider configuring OpenAI or Ollama for production use.");
    }

    @Override
    public float[] getEmbedding(String text) {
        if (text == null || text.isEmpty()) {
            return new float[DIMENSION];
        }

        float[] embedding = new float[DIMENSION];
        String normalizedText = text.toLowerCase().trim();

        // 方法 1: 字元頻率分佈
        for (char c : normalizedText.toCharArray()) {
            int index = Math.abs(c * 31) % DIMENSION;
            embedding[index] += 1.0f;
        }

        // 方法 2: n-gram 雜湊
        for (int i = 0; i < normalizedText.length() - 2; i++) {
            String trigram = normalizedText.substring(i, i + 3);
            int index = Math.abs(trigram.hashCode()) % DIMENSION;
            embedding[index] += 0.5f;
        }

        // 方法 3: 詞語雜湊 (以空格分割)
        String[] words = normalizedText.split("\\s+");
        for (String word : words) {
            if (!word.isEmpty()) {
                int index = Math.abs(word.hashCode()) % DIMENSION;
                embedding[index] += 2.0f;
            }
        }

        // L2 正規化
        float sumSquares = 0f;
        for (float v : embedding) {
            sumSquares += v * v;
        }
        float norm = (float) Math.sqrt(sumSquares);
        if (norm > 0) {
            for (int i = 0; i < embedding.length; i++) {
                embedding[i] /= norm;
            }
        }

        return embedding;
    }

    @Override
    public List<float[]> getBatchEmbeddings(List<String> texts) {
        return texts.stream()
                .map(this::getEmbedding)
                .toList();
    }

    @Override
    public int getDimension() {
        return DIMENSION;
    }

    @Override
    public String getProviderName() {
        return "simple";
    }

    @Override
    public String getModelName() {
        return "simple-hash-embedding";
    }

    @Override
    public boolean isAvailable() {
        return true; // 始終可用
    }
}
