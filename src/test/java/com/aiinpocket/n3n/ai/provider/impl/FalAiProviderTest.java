package com.aiinpocket.n3n.ai.provider.impl;

import com.aiinpocket.n3n.ai.provider.AiModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FalAiProviderTest {

    private final FalAiProvider provider = new FalAiProvider();

    @Test
    @DisplayName("registers as the 'fal' provider and does not support chat")
    void identity() {
        assertThat(provider.getProviderId()).isEqualTo("fal");
        assertThat(provider.supportsChat()).isFalse();
        assertThat(provider.requiresApiKey()).isTrue();
    }

    @Test
    @DisplayName("returns a static list of media generation models")
    void fetchModels_returnsMediaModels() throws Exception {
        List<AiModel> models = provider.fetchModels("any-key", null).get();

        assertThat(models).isNotEmpty();
        assertThat(models).allSatisfy(m -> {
            assertThat(m.getProviderId()).isEqualTo("fal");
            assertThat(m.getId()).startsWith("fal-ai/");
        });
        assertThat(models).anySatisfy(m -> assertThat(m.getDisplayName()).contains("Video"));
    }

    @Test
    @DisplayName("chat requests fail with UnsupportedOperationException")
    void chat_unsupported() {
        assertThatThrownBy(() -> provider.chat(null, null).get())
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(UnsupportedOperationException.class);
    }
}
