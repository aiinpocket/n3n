package com.aiinpocket.n3n.common.util;

/**
 * Validation helpers for user-supplied components that are interpolated into JDBC URLs.
 *
 * <p>JDBC drivers treat characters such as {@code ?}, {@code &}, {@code ;}, {@code #} and
 * {@code /} as URL/connection-string control characters. If a {@code host} or {@code database}
 * value taken directly from a workflow/credential request is spliced into a JDBC URL without
 * validation, an attacker can inject extra connection parameters — enabling SSRF (redirecting
 * the connection to an internal host) or arbitrary local file reads (e.g. via driver-specific
 * options). These helpers reject the dangerous characters before any JDBC URL is built.
 */
public final class JdbcParamValidator {

    private JdbcParamValidator() {
    }

    /**
     * Validate a database/schema name that will be interpolated into a JDBC URL.
     * Rejects characters that would let the value break out of the database segment and
     * inject connection parameters.
     *
     * @throws IllegalArgumentException if the name is null/blank or contains a forbidden character
     */
    public static void validateDatabaseName(String database) {
        if (database == null || database.isBlank()) {
            throw new IllegalArgumentException("Database name is required");
        }
        // ? & # ; / \ and whitespace are all JDBC URL / connection-string control characters.
        if (containsAny(database, "?&#;/\\") || containsWhitespace(database)) {
            throw new IllegalArgumentException(
                    "Invalid database name: must not contain URL/connection control characters (? & # ; / \\ or whitespace)");
        }
    }

    /**
     * Validate a host (hostname or IP literal) that will be interpolated into a JDBC URL.
     * Rejects characters that would allow injecting an authority section, extra parameters,
     * or path components.
     *
     * @throws IllegalArgumentException if the host is null/blank or contains a forbidden character
     */
    public static void validateHost(String host) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("Host is required");
        }
        // / ? # & @ : and whitespace would let the value alter the JDBC authority / parameters.
        if (containsAny(host, "/?#&@:") || containsWhitespace(host)) {
            throw new IllegalArgumentException(
                    "Invalid host: must not contain '/ ? # & @ :' or whitespace");
        }
    }

    /**
     * Validate a SQLite database/file path. In addition to URL control characters, reject
     * parent-directory traversal ({@code ..}) so a caller cannot escape an intended directory
     * or read arbitrary files.
     *
     * @throws IllegalArgumentException if the value is null/blank, contains traversal or control chars
     */
    public static void validateSqliteDatabase(String database) {
        if (database == null || database.isBlank()) {
            throw new IllegalArgumentException("Database file path is required");
        }
        if (database.contains("..")) {
            throw new IllegalArgumentException("Invalid SQLite path: parent-directory traversal is not allowed");
        }
        // URL-parameter / query chars would let the caller append JDBC options to the file URL.
        if (containsAny(database, "?&#;") || containsWhitespace(database)) {
            throw new IllegalArgumentException(
                    "Invalid SQLite path: must not contain URL/connection control characters (? & # ;) or whitespace");
        }
    }

    private static boolean containsAny(String value, String forbidden) {
        for (int i = 0; i < forbidden.length(); i++) {
            if (value.indexOf(forbidden.charAt(i)) >= 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsWhitespace(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isWhitespace(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
