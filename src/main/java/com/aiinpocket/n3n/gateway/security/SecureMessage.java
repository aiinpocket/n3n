package com.aiinpocket.n3n.gateway.security;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Secure message wrapper with encryption header.
 * Format: base64url(header).base64url(ciphertext).base64url(tag)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecureMessage {

    /**
     * Message header (transmitted in clear, but authenticated)
     */
    private Header header;

    /**
     * Encrypted payload
     */
    private String ciphertext;

    /**
     * GCM authentication tag
     */
    private String tag;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Header {
        /**
         * Protocol version
         */
        @Builder.Default
        private int v = 1;

        /**
         * Encryption algorithm (always A256GCM)
         */
        @Builder.Default
        private String alg = "A256GCM";

        /**
         * Device ID
         */
        private String did;

        /**
         * Timestamp in milliseconds
         */
        private long ts;

        /**
         * Sequence number (increments with each message)
         */
        private long seq;

        /**
         * Nonce (Base64URL encoded)
         */
        private String nonce;

        /**
         * Direction: c2s (client to server) or s2c (server to client)
         */
        private String dir;
    }

    /**
     * Serialize to compact format: header.ciphertext.tag
     */
    public String toCompact(ObjectMapper objectMapper) {
        try {
            String headerJson = objectMapper.writeValueAsString(header);
            String headerB64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));
            return headerB64 + "." + ciphertext + "." + tag;
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize secure message", e);
        }
    }

    /**
     * Parse from compact format
     */
    public static SecureMessage fromCompact(String compact, ObjectMapper objectMapper) {
        String[] parts = compact.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid secure message format: expected 3 parts");
        }

        try {
            String headerJson = new String(
                Base64.getUrlDecoder().decode(parts[0]),
                StandardCharsets.UTF_8
            );
            Header header = objectMapper.readValue(headerJson, Header.class);

            return SecureMessage.builder()
                .header(header)
                .ciphertext(parts[1])
                .tag(parts[2])
                .build();
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse secure message", e);
        }
    }

    /**
     * Get the header as bytes for AAD (Additional Authenticated Data)
     */
    @JsonIgnore
    public byte[] getHeaderBytes(ObjectMapper objectMapper) {
        try {
            return objectMapper.writeValueAsBytes(header);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize header", e);
        }
    }

    /**
     * Get the ciphertext as bytes
     */
    @JsonIgnore
    public byte[] getCiphertextBytes() {
        return Base64.getUrlDecoder().decode(ciphertext);
    }

    /**
     * Get the tag as bytes
     */
    @JsonIgnore
    public byte[] getTagBytes() {
        return Base64.getUrlDecoder().decode(tag);
    }

    /**
     * Get the nonce as bytes
     */
    @JsonIgnore
    public byte[] getNonceBytes() {
        return Base64.getUrlDecoder().decode(header.getNonce());
    }
}
