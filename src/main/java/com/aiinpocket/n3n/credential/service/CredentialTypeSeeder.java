package com.aiinpocket.n3n.credential.service;

import com.aiinpocket.n3n.credential.entity.CredentialType;
import com.aiinpocket.n3n.credential.repository.CredentialTypeRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Seeds default credential types on application startup if they don't exist.
 * This ensures credential types are always available even when Flyway migrations
 * are skipped due to Hibernate ddl-auto=update creating tables first.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CredentialTypeSeeder {

    private final CredentialTypeRepository credentialTypeRepository;

    @PostConstruct
    public void seed() {
        List<SeedType> seeds = List.of(
                new SeedType("http_basic", "HTTP Basic Auth", "Username and password authentication",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "username", Map.of("type", "string", "title", "Username"),
                                        "password", Map.of("type", "string", "format", "password", "title", "Password")),
                                "required", List.of("username", "password"))),
                new SeedType("http_bearer", "HTTP Bearer Token", "Bearer token authentication",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "token", Map.of("type", "string", "format", "password", "title", "Token")),
                                "required", List.of("token"))),
                new SeedType("api_key", "API Key", "API key authentication",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "key", Map.of("type", "string", "format", "password", "title", "API Key"),
                                        "header", Map.of("type", "string", "title", "Header Name", "default", "X-API-Key")),
                                "required", List.of("key"))),
                new SeedType("oauth2", "OAuth2", "OAuth2 authentication",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "clientId", Map.of("type", "string", "title", "Client ID"),
                                        "clientSecret", Map.of("type", "string", "format", "password", "title", "Client Secret"),
                                        "tokenUrl", Map.of("type", "string", "format", "uri", "title", "Token URL"),
                                        "scope", Map.of("type", "string", "title", "Scope")),
                                "required", List.of("clientId", "clientSecret", "tokenUrl"))),
                new SeedType("database", "Database Connection", "Database connection credentials",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "host", Map.of("type", "string", "title", "Host"),
                                        "port", Map.of("type", "integer", "title", "Port"),
                                        "database", Map.of("type", "string", "title", "Database"),
                                        "username", Map.of("type", "string", "title", "Username"),
                                        "password", Map.of("type", "string", "format", "password", "title", "Password")),
                                "required", List.of("host", "database", "username", "password"))),
                new SeedType("ssh", "SSH Key", "SSH key authentication",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "host", Map.of("type", "string", "title", "Host"),
                                        "port", Map.of("type", "integer", "title", "Port", "default", 22),
                                        "username", Map.of("type", "string", "title", "Username"),
                                        "privateKey", Map.of("type", "string", "format", "textarea", "title", "Private Key"),
                                        "passphrase", Map.of("type", "string", "format", "password", "title", "Passphrase")),
                                "required", List.of("host", "username", "privateKey"))),
                new SeedType("mongodb", "MongoDB", "MongoDB connection credentials",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "host", Map.of("type", "string", "title", "Host"),
                                        "port", Map.of("type", "integer", "title", "Port", "default", 27017),
                                        "database", Map.of("type", "string", "title", "Database"),
                                        "username", Map.of("type", "string", "title", "Username"),
                                        "password", Map.of("type", "string", "format", "password", "title", "Password"),
                                        "authSource", Map.of("type", "string", "title", "Auth Source", "default", "admin")),
                                "required", List.of("host", "database"))),
                new SeedType("redis", "Redis", "Redis connection credentials",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "host", Map.of("type", "string", "title", "Host"),
                                        "port", Map.of("type", "integer", "title", "Port", "default", 6379),
                                        "password", Map.of("type", "string", "format", "password", "title", "Password"),
                                        "database", Map.of("type", "integer", "title", "Database Index", "default", 0)),
                                "required", List.of("host"))),
                new SeedType("elasticsearch", "Elasticsearch", "Elasticsearch connection credentials",
                        Map.of("type", "object",
                                "properties", Map.of(
                                        "host", Map.of("type", "string", "title", "Host"),
                                        "port", Map.of("type", "integer", "title", "Port", "default", 9200),
                                        "username", Map.of("type", "string", "title", "Username"),
                                        "password", Map.of("type", "string", "format", "password", "title", "Password"),
                                        "scheme", Map.of("type", "string", "title", "Scheme", "default", "https")),
                                "required", List.of("host")))
        );

        int seeded = 0;
        for (SeedType s : seeds) {
            if (!credentialTypeRepository.existsByName(s.name())) {
                credentialTypeRepository.save(CredentialType.builder()
                        .name(s.name())
                        .displayName(s.displayName())
                        .description(s.description())
                        .fieldsSchema(s.fieldsSchema())
                        .build());
                seeded++;
            }
        }

        if (seeded > 0) {
            log.info("Seeded {} credential types", seeded);
        }
    }

    private record SeedType(String name, String displayName, String description, Map<String, Object> fieldsSchema) {}
}
