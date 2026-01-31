package com.aiinpocket.n3n.ai.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient configuration for AI provider HTTP calls.
 *
 * Provides a WebClient.Builder bean that can be injected into
 * AI provider implementations for making reactive HTTP requests.
 */
@Configuration
public class WebClientConfig {

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
