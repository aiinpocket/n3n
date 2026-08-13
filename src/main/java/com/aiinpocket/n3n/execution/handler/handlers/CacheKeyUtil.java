package com.aiinpocket.n3n.execution.handler.handlers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Shared helper for deriving connection/client cache keys.
 *
 * <p>Security rationale: connection pools and node clients are cached across the whole JVM
 * (singleton handler beans). Cache keys must incorporate the full credential secret so that a
 * different password/api-key/connection-string yields a different pool. To avoid holding the
 * plaintext secret in memory as a map key, the secret material is folded into a SHA-256 hex
 * digest. This also replaces collidable {@code String.hashCode()} usages.
 */
public final class CacheKeyUtil {

    private CacheKeyUtil() {
    }

    /**
     * Compute a lowercase SHA-256 hex digest of the given input. A {@code null} input is treated
     * as the empty string so callers never have to null-guard. The returned value never contains
     * the plaintext and is safe to log.
     *
     * @param input the (possibly secret) string to hash; may be {@code null}
     * @return 64-character lowercase hex string
     */
    public static String sha256Hex(String input) {
        String safe = input != null ? input : "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(safe.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                int v = b & 0xFF;
                if (v < 0x10) {
                    hex.append('0');
                }
                hex.append(Integer.toHexString(v));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed present on every JVM; this cannot happen in practice.
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Null-safe helper used when concatenating multiple credential fields into a single
     * pre-hash descriptor. Returns {@code ""} for {@code null}.
     */
    public static String nullToEmpty(String value) {
        return value != null ? value : "";
    }
}
