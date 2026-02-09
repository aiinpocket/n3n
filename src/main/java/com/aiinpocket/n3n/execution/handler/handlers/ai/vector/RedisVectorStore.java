package com.aiinpocket.n3n.execution.handler.handlers.ai.vector;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Redis vector store implementation.
 *
 * Uses Redis Hash to store vectors and metadata.
 * Search uses brute-force cosine similarity calculation (suitable for small-scale data).
 *
 * For production environments, consider using Redis Stack's vector search
 * or dedicated vector databases (Pinecone, Weaviate, Milvus).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RedisVectorStore implements VectorStore {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String KEY_PREFIX = "vector:";
    private static final String INDEX_PREFIX = "vector:index:";

    @Override
    public CompletableFuture<Void> upsert(String namespace, VectorDocument document) {
        return CompletableFuture.runAsync(() -> {
            try {
                String key = KEY_PREFIX + namespace + ":" + document.id();

                Map<String, String> data = new HashMap<>();
                data.put("id", document.id());
                data.put("vector", objectMapper.writeValueAsString(document.vector()));
                data.put("content", document.content());
                data.put("metadata", objectMapper.writeValueAsString(document.metadata()));

                redisTemplate.opsForHash().putAll(key, data);
                redisTemplate.expire(key, java.time.Duration.ofDays(7));

                // Add to index
                String indexKey = INDEX_PREFIX + namespace;
                redisTemplate.opsForSet().add(indexKey, document.id());
                redisTemplate.expire(indexKey, java.time.Duration.ofDays(7));

                log.debug("Upserted vector: {} in {}", document.id(), namespace);
            } catch (Exception e) {
                log.error("Failed to upsert vector: {}", e.getMessage());
                throw new RuntimeException("Failed to upsert vector", e);
            }
        });
    }

    @Override
    public CompletableFuture<Void> upsertBatch(String namespace, List<VectorDocument> documents) {
        return CompletableFuture.runAsync(() -> {
            for (VectorDocument doc : documents) {
                upsert(namespace, doc).join();
            }
            log.debug("Upserted {} vectors in {}", documents.size(), namespace);
        });
    }

    @Override
    @SuppressWarnings("unchecked")
    public CompletableFuture<List<SearchResult>> search(
        String namespace,
        List<Float> queryVector,
        int topK,
        Map<String, Object> filter
    ) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String indexKey = INDEX_PREFIX + namespace;
                Set<String> ids = redisTemplate.opsForSet().members(indexKey);

                if (ids == null || ids.isEmpty()) {
                    return List.of();
                }

                // Calculate similarity for all vectors
                List<ScoredDocument> scored = new ArrayList<>();

                for (String id : ids) {
                    String key = KEY_PREFIX + namespace + ":" + id;
                    Map<Object, Object> data = redisTemplate.opsForHash().entries(key);

                    if (data.isEmpty()) continue;

                    // Parse vector
                    String vectorJson = (String) data.get("vector");
                    List<Float> vector = objectMapper.readValue(vectorJson,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Float.class));

                    // Parse metadata
                    String metadataJson = (String) data.get("metadata");
                    Map<String, Object> metadata = objectMapper.readValue(metadataJson, Map.class);

                    // Apply filter conditions
                    if (filter != null && !matchesFilter(metadata, filter)) {
                        continue;
                    }

                    // Calculate cosine similarity
                    float score = cosineSimilarity(queryVector, vector);

                    scored.add(new ScoredDocument(
                        id,
                        score,
                        (String) data.get("content"),
                        metadata
                    ));
                }

                // Sort and take topK
                return scored.stream()
                    .sorted((a, b) -> Float.compare(b.score, a.score))
                    .limit(topK)
                    .map(s -> new SearchResult(s.id, s.score, s.content, s.metadata))
                    .collect(Collectors.toList());

            } catch (Exception e) {
                log.error("Vector search failed: {}", e.getMessage());
                return List.of();
            }
        });
    }

    private boolean matchesFilter(Map<String, Object> metadata, Map<String, Object> filter) {
        for (Map.Entry<String, Object> entry : filter.entrySet()) {
            Object metaValue = metadata.get(entry.getKey());
            if (metaValue == null || !metaValue.equals(entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private float cosineSimilarity(List<Float> a, List<Float> b) {
        if (a.size() != b.size()) return 0f;

        float dotProduct = 0f;
        float normA = 0f;
        float normB = 0f;

        for (int i = 0; i < a.size(); i++) {
            float ai = a.get(i);
            float bi = b.get(i);
            dotProduct += ai * bi;
            normA += ai * ai;
            normB += bi * bi;
        }

        if (normA == 0 || normB == 0) return 0f;

        return dotProduct / (float) (Math.sqrt(normA) * Math.sqrt(normB));
    }

    @Override
    public CompletableFuture<Void> delete(String namespace, String id) {
        return CompletableFuture.runAsync(() -> {
            String key = KEY_PREFIX + namespace + ":" + id;
            String indexKey = INDEX_PREFIX + namespace;

            redisTemplate.delete(key);
            redisTemplate.opsForSet().remove(indexKey, id);

            log.debug("Deleted vector: {} from {}", id, namespace);
        });
    }

    @Override
    public CompletableFuture<Void> deleteAll(String namespace) {
        return CompletableFuture.runAsync(() -> {
            String indexKey = INDEX_PREFIX + namespace;
            Set<String> ids = redisTemplate.opsForSet().members(indexKey);

            if (ids != null) {
                for (String id : ids) {
                    String key = KEY_PREFIX + namespace + ":" + id;
                    redisTemplate.delete(key);
                }
            }

            redisTemplate.delete(indexKey);
            log.debug("Deleted all vectors in namespace: {}", namespace);
        });
    }

    @Override
    public CompletableFuture<Long> count(String namespace) {
        return CompletableFuture.supplyAsync(() -> {
            String indexKey = INDEX_PREFIX + namespace;
            Long size = redisTemplate.opsForSet().size(indexKey);
            return size != null ? size : 0L;
        });
    }

    private record ScoredDocument(
        String id,
        float score,
        String content,
        Map<String, Object> metadata
    ) {}
}
