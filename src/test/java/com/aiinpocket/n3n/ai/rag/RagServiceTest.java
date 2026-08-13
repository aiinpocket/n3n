package com.aiinpocket.n3n.ai.rag;

import com.aiinpocket.n3n.ai.rag.document.Document;
import com.aiinpocket.n3n.ai.rag.document.loaders.TextLoader;
import com.aiinpocket.n3n.ai.rag.splitter.RecursiveCharacterSplitter;
import com.aiinpocket.n3n.ai.service.AiService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Tenant-isolation tests for {@link RagService}.
 *
 * The effective store key is derived server-side as {@code userId + ":" + storeName};
 * the caller-supplied storeName is never the sole key, so two different users using the
 * same storeName (including null/default) must get fully isolated document stores, while
 * the same user reusing a named store across flows still shares it.
 */
@ExtendWith(MockitoExtension.class)
class RagServiceTest {

    @Mock
    private AiService aiService;
    @Mock
    private TextLoader textLoader;
    @Mock
    private RecursiveCharacterSplitter textSplitter;

    private RagService ragService;

    private static final String USER_A = "11111111-1111-1111-1111-111111111111";
    private static final String USER_B = "22222222-2222-2222-2222-222222222222";

    @BeforeEach
    void setUp() {
        ragService = new RagService(aiService, textLoader, textSplitter);
    }

    private void stubEmbeddingPipeline() {
        // Each indexed document is a single chunk; every embedding is identical so a
        // non-empty store always yields a hit and isolation is the only variable.
        when(textSplitter.split(any(Document.class)))
                .thenReturn(List.of(Document.of("secret-doc", Map.of())));
        when(aiService.getEmbedding(anyString())).thenReturn(new float[]{1f, 0f, 0f});
    }

    @Test
    @DisplayName("Same storeName, different users -> isolated stores (no cross-tenant leak)")
    void sameStoreName_differentUsers_isolated() {
        stubEmbeddingPipeline();

        // User A indexes into named store "kb"
        ragService.indexDocument("secret-doc", Map.of(), "kb", USER_A);

        // User A can retrieve their own document
        List<Document> ownResults = ragService.search("query", 5, "kb", USER_A);
        assertThat(ownResults).isNotEmpty();

        // User B, using the SAME storeName, must NOT see user A's document
        List<Document> otherResults = ragService.search("query", 5, "kb", USER_B);
        assertThat(otherResults).isEmpty();
    }

    @Test
    @DisplayName("Null/default storeName is also per-user isolated")
    void defaultStore_isPerUserIsolated() {
        stubEmbeddingPipeline();

        ragService.indexDocument("secret-doc", Map.of(), null, USER_A);

        assertThat(ragService.search("query", 5, null, USER_A)).isNotEmpty();
        assertThat(ragService.search("query", 5, null, USER_B)).isEmpty();
    }

    @Test
    @DisplayName("Same user shares a named store across calls/flows")
    void sameUser_sharesNamedStore() {
        stubEmbeddingPipeline();

        ragService.indexDocument("secret-doc", Map.of(), "kb", USER_A);

        // A second, independent call by the same user hits the same store
        assertThat(ragService.search("query", 5, "kb", USER_A)).isNotEmpty();
    }

    @Test
    @DisplayName("clearStore only clears the calling user's store")
    void clearStore_isPerUser() {
        stubEmbeddingPipeline();

        ragService.indexDocument("secret-doc", Map.of(), "kb", USER_A);
        ragService.indexDocument("secret-doc", Map.of(), "kb", USER_B);

        ragService.clearStore("kb", USER_A);

        assertThat(ragService.search("query", 5, "kb", USER_A)).isEmpty();
        // User B's store is untouched
        assertThat(ragService.search("query", 5, "kb", USER_B)).isNotEmpty();
    }

    @Test
    @DisplayName("Missing userId fails closed (no unscoped access)")
    void missingUserId_failsClosed() {
        assertThatThrownBy(() -> ragService.search("query", 5, "kb", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("userId is required");

        assertThatThrownBy(() -> ragService.search("query", 5, "kb", "  "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
