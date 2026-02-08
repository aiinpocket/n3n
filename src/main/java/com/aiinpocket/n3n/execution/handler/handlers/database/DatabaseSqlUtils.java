package com.aiinpocket.n3n.execution.handler.handlers.database;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared SQL utility methods for database operation classes.
 * <p>
 * Provides parameter parsing, named parameter processing,
 * PreparedStatement parameter binding, identifier validation and quoting.
 */
final class DatabaseSqlUtils {

    // Pattern for named parameters :paramName
    static final Pattern NAMED_PARAM_PATTERN = Pattern.compile(":([a-zA-Z_][a-zA-Z0-9_]*)");

    private DatabaseSqlUtils() {}

    // ==================== Parameter Helpers ====================

    static String getRequiredParam(Map<String, Object> params, String name) {
        Object value = params.get(name);
        if (value == null || (value instanceof String && ((String) value).isEmpty())) {
            throw new IllegalArgumentException("Required parameter '" + name + "' is missing");
        }
        return value.toString();
    }

    static String getParam(Map<String, Object> params, String name, String defaultValue) {
        Object value = params.get(name);
        if (value == null || (value instanceof String && ((String) value).isEmpty())) {
            return defaultValue;
        }
        return value.toString();
    }

    static int getIntParam(Map<String, Object> params, String name, int defaultValue) {
        Object value = params.get(name);
        if (value == null) return defaultValue;
        if (value instanceof Number) return ((Number) value).intValue();
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    static boolean getBoolParam(Map<String, Object> params, String name, boolean defaultValue) {
        Object value = params.get(name);
        if (value == null) return defaultValue;
        if (value instanceof Boolean) return (Boolean) value;
        return Boolean.parseBoolean(value.toString());
    }

    // ==================== SQL Parameter Processing ====================

    static Object[] parseParams(ObjectMapper objectMapper, String paramsJson, String sql) throws Exception {
        if (paramsJson == null || paramsJson.isEmpty()) {
            return new Object[0];
        }

        Object parsed = objectMapper.readValue(paramsJson, Object.class);

        if (parsed instanceof List) {
            return ((List<?>) parsed).toArray();
        } else if (parsed instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = (Map<String, Object>) parsed;
            List<Object> orderedParams = new ArrayList<>();

            Matcher matcher = NAMED_PARAM_PATTERN.matcher(sql);
            while (matcher.find()) {
                String paramName = matcher.group(1);
                if (map.containsKey(paramName)) {
                    orderedParams.add(map.get(paramName));
                } else {
                    orderedParams.add(null);
                }
            }
            return orderedParams.toArray();
        }

        return new Object[0];
    }

    static String processNamedParams(String sql, String paramsJson) {
        if (paramsJson == null || paramsJson.isEmpty()) {
            return sql;
        }
        return NAMED_PARAM_PATTERN.matcher(sql).replaceAll("?");
    }

    // ==================== PreparedStatement Binding ====================

    static void setParameters(PreparedStatement stmt, Object[] params) throws SQLException {
        for (int i = 0; i < params.length; i++) {
            setParameter(stmt, i + 1, params[i]);
        }
    }

    private static void setParameter(PreparedStatement stmt, int index, Object value) throws SQLException {
        if (value == null) {
            stmt.setNull(index, Types.NULL);
        } else if (value instanceof String) {
            stmt.setString(index, (String) value);
        } else if (value instanceof Integer) {
            stmt.setInt(index, (Integer) value);
        } else if (value instanceof Long) {
            stmt.setLong(index, (Long) value);
        } else if (value instanceof Double) {
            stmt.setDouble(index, (Double) value);
        } else if (value instanceof Float) {
            stmt.setFloat(index, (Float) value);
        } else if (value instanceof Boolean) {
            stmt.setBoolean(index, (Boolean) value);
        } else if (value instanceof java.util.Date) {
            stmt.setTimestamp(index, new Timestamp(((java.util.Date) value).getTime()));
        } else if (value instanceof byte[]) {
            stmt.setBytes(index, (byte[]) value);
        } else {
            stmt.setString(index, value.toString());
        }
    }

    // ==================== Identifier Validation ====================

    static boolean isValidIdentifier(String identifier) {
        if (identifier == null || identifier.isEmpty()) {
            return false;
        }
        return identifier.matches("^[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)?$");
    }

    static String quoteIdentifier(Connection conn, String identifier) {
        try {
            String quote = conn.getMetaData().getIdentifierQuoteString();
            if (quote == null || quote.equals(" ")) {
                quote = "\"";
            }
            if (identifier.contains(".")) {
                String[] parts = identifier.split("\\.");
                return quote + escapeIdentifier(parts[0], quote) + quote + "." + quote + escapeIdentifier(parts[1], quote) + quote;
            }
            return quote + escapeIdentifier(identifier, quote) + quote;
        } catch (SQLException e) {
            return "\"" + escapeIdentifier(identifier, "\"") + "\"";
        }
    }

    private static String escapeIdentifier(String identifier, String quoteChar) {
        // Double the quote character to escape it (SQL standard)
        return identifier.replace(quoteChar, quoteChar + quoteChar);
    }
}
