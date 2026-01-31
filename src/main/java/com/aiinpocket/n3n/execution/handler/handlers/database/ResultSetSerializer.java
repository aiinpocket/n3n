package com.aiinpocket.n3n.execution.handler.handlers.database;

import lombok.extern.slf4j.Slf4j;

import java.io.Reader;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Serializes JDBC ResultSet to JSON-compatible structures.
 *
 * Handles:
 * - Primitive types (int, long, float, double, boolean)
 * - String and text types (VARCHAR, CHAR, TEXT, CLOB)
 * - Numeric types (NUMERIC, DECIMAL, BigDecimal)
 * - Date/Time types (DATE, TIME, TIMESTAMP, with/without timezone)
 * - Binary types (BLOB, BINARY) - converted to Base64
 * - Arrays (PostgreSQL arrays, etc.)
 * - JSON/JSONB (PostgreSQL, MySQL)
 * - NULL values
 */
@Slf4j
public class ResultSetSerializer {

    private static final DateTimeFormatter ISO_DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter ISO_TIME = DateTimeFormatter.ISO_LOCAL_TIME;
    private static final DateTimeFormatter ISO_DATETIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final DateTimeFormatter ISO_OFFSET_DATETIME = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    /**
     * Convert a ResultSet to a list of maps (rows).
     *
     * @param rs      ResultSet to convert
     * @param maxRows Maximum rows to fetch (0 = unlimited)
     * @return List of row maps
     */
    public static List<Map<String, Object>> toList(ResultSet rs, int maxRows) throws SQLException {
        List<Map<String, Object>> rows = new ArrayList<>();
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();

        // Get column info
        List<ColumnInfo> columns = new ArrayList<>();
        for (int i = 1; i <= columnCount; i++) {
            columns.add(new ColumnInfo(
                meta.getColumnLabel(i),
                meta.getColumnType(i),
                meta.getColumnTypeName(i)
            ));
        }

        int rowCount = 0;
        while (rs.next()) {
            if (maxRows > 0 && rowCount >= maxRows) {
                break;
            }

            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < columnCount; i++) {
                ColumnInfo col = columns.get(i);
                Object value = convertValue(rs, i + 1, col.sqlType, col.typeName);
                row.put(col.name, value);
            }
            rows.add(row);
            rowCount++;
        }

        return rows;
    }

    /**
     * Get column metadata from a ResultSet.
     */
    public static List<Map<String, Object>> getColumnMetadata(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        int columnCount = meta.getColumnCount();

        List<Map<String, Object>> columns = new ArrayList<>();
        for (int i = 1; i <= columnCount; i++) {
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("name", meta.getColumnLabel(i));
            col.put("type", meta.getColumnTypeName(i));
            col.put("jdbcType", meta.getColumnType(i));
            col.put("nullable", meta.isNullable(i) != ResultSetMetaData.columnNoNulls);
            col.put("precision", meta.getPrecision(i));
            col.put("scale", meta.getScale(i));
            columns.add(col);
        }

        return columns;
    }

    /**
     * Convert a single column value to JSON-compatible type.
     */
    private static Object convertValue(ResultSet rs, int columnIndex, int sqlType, String typeName) throws SQLException {
        Object value = rs.getObject(columnIndex);

        // Handle NULL
        if (value == null || rs.wasNull()) {
            return null;
        }

        // Handle by SQL type
        return switch (sqlType) {
            // String types
            case Types.CHAR, Types.VARCHAR, Types.LONGVARCHAR, Types.NCHAR, Types.NVARCHAR, Types.LONGNVARCHAR ->
                rs.getString(columnIndex);

            // Integer types
            case Types.TINYINT, Types.SMALLINT ->
                rs.getInt(columnIndex);

            case Types.INTEGER ->
                rs.getInt(columnIndex);

            case Types.BIGINT ->
                rs.getLong(columnIndex);

            // Floating point types
            case Types.FLOAT, Types.REAL ->
                rs.getFloat(columnIndex);

            case Types.DOUBLE ->
                rs.getDouble(columnIndex);

            // Decimal/Numeric types - preserve precision
            case Types.NUMERIC, Types.DECIMAL -> {
                BigDecimal bd = rs.getBigDecimal(columnIndex);
                if (bd == null) {
                    yield null;
                }
                // If scale is 0, return as long; otherwise as double or string
                if (bd.scale() == 0 && bd.compareTo(BigDecimal.valueOf(Long.MAX_VALUE)) <= 0
                    && bd.compareTo(BigDecimal.valueOf(Long.MIN_VALUE)) >= 0) {
                    yield bd.longValue();
                } else if (bd.scale() <= 10) {
                    yield bd.doubleValue();
                } else {
                    // High precision - return as string to avoid loss
                    yield bd.toPlainString();
                }
            }

            // Boolean type
            case Types.BOOLEAN, Types.BIT ->
                rs.getBoolean(columnIndex);

            // Date/Time types
            case Types.DATE -> {
                java.sql.Date date = rs.getDate(columnIndex);
                yield date != null ? date.toLocalDate().format(ISO_DATE) : null;
            }

            case Types.TIME -> {
                Time time = rs.getTime(columnIndex);
                yield time != null ? time.toLocalTime().format(ISO_TIME) : null;
            }

            case Types.TIME_WITH_TIMEZONE -> {
                Object timeObj = rs.getObject(columnIndex);
                if (timeObj instanceof OffsetTime ot) {
                    yield ot.format(DateTimeFormatter.ISO_OFFSET_TIME);
                }
                yield timeObj != null ? timeObj.toString() : null;
            }

            case Types.TIMESTAMP -> {
                Timestamp ts = rs.getTimestamp(columnIndex);
                if (ts == null) {
                    yield null;
                }
                yield ts.toLocalDateTime().format(ISO_DATETIME);
            }

            case Types.TIMESTAMP_WITH_TIMEZONE -> {
                Object tsObj = rs.getObject(columnIndex);
                if (tsObj instanceof OffsetDateTime odt) {
                    yield odt.format(ISO_OFFSET_DATETIME);
                } else if (tsObj instanceof ZonedDateTime zdt) {
                    yield zdt.format(ISO_OFFSET_DATETIME);
                }
                yield tsObj != null ? tsObj.toString() : null;
            }

            // Binary types - convert to Base64
            case Types.BINARY, Types.VARBINARY, Types.LONGVARBINARY, Types.BLOB -> {
                byte[] bytes = rs.getBytes(columnIndex);
                yield bytes != null ? Base64.getEncoder().encodeToString(bytes) : null;
            }

            // CLOB - read as string
            case Types.CLOB, Types.NCLOB -> {
                Clob clob = rs.getClob(columnIndex);
                if (clob == null) {
                    yield null;
                }
                yield clobToString(clob);
            }

            // Array types (PostgreSQL, etc.)
            case Types.ARRAY -> {
                Array arr = rs.getArray(columnIndex);
                if (arr == null) {
                    yield null;
                }
                Object[] arrayData = (Object[]) arr.getArray();
                yield Arrays.asList(arrayData);
            }

            // JSON types (check by type name for PostgreSQL/MySQL)
            case Types.OTHER -> {
                String lowerTypeName = typeName.toLowerCase();
                if (lowerTypeName.equals("json") || lowerTypeName.equals("jsonb")) {
                    // Return as string - let caller parse if needed
                    yield rs.getString(columnIndex);
                } else if (lowerTypeName.equals("uuid")) {
                    yield rs.getString(columnIndex);
                } else if (lowerTypeName.contains("interval")) {
                    yield rs.getString(columnIndex);
                }
                // Default: try getString
                yield rs.getString(columnIndex);
            }

            // SQLXML
            case Types.SQLXML -> {
                SQLXML xml = rs.getSQLXML(columnIndex);
                yield xml != null ? xml.getString() : null;
            }

            // REF, STRUCT, ROWID, etc. - convert to string
            default -> {
                log.debug("Unknown SQL type {} ({}), converting to string", sqlType, typeName);
                yield value.toString();
            }
        };
    }

    /**
     * Convert CLOB to String.
     */
    private static String clobToString(Clob clob) throws SQLException {
        try (Reader reader = clob.getCharacterStream()) {
            StringBuilder sb = new StringBuilder();
            char[] buffer = new char[4096];
            int len;
            while ((len = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, len);
            }
            return sb.toString();
        } catch (Exception e) {
            throw new SQLException("Failed to read CLOB", e);
        }
    }

    /**
     * Column info holder.
     */
    private record ColumnInfo(String name, int sqlType, String typeName) {}

    /**
     * Determine if a SQL statement is a query (SELECT) or modification (INSERT/UPDATE/DELETE).
     *
     * @param sql SQL statement
     * @return true if it's a query (returns rows)
     */
    public static boolean isQuery(String sql) {
        String trimmed = sql.trim().toLowerCase();

        // Remove leading comments
        while (trimmed.startsWith("--") || trimmed.startsWith("/*")) {
            if (trimmed.startsWith("--")) {
                int newline = trimmed.indexOf('\n');
                if (newline == -1) break;
                trimmed = trimmed.substring(newline + 1).trim();
            } else {
                int endComment = trimmed.indexOf("*/");
                if (endComment == -1) break;
                trimmed = trimmed.substring(endComment + 2).trim();
            }
        }

        // Check first keyword
        if (trimmed.startsWith("select")) return true;
        if (trimmed.startsWith("with")) return true; // CTE, usually followed by SELECT
        if (trimmed.startsWith("show")) return true;  // MySQL SHOW commands
        if (trimmed.startsWith("describe")) return true;
        if (trimmed.startsWith("explain")) return true;
        if (trimmed.startsWith("table")) return true;  // PostgreSQL TABLE command

        // INSERT/UPDATE/DELETE with RETURNING clause (PostgreSQL)
        if (trimmed.contains(" returning ") || trimmed.endsWith(" returning")) {
            return true;
        }

        return false;
    }

    /**
     * Determine the type of modification statement.
     *
     * @param sql SQL statement
     * @return "insert", "update", "delete", or "other"
     */
    public static String getModificationType(String sql) {
        String trimmed = sql.trim().toLowerCase();

        // Remove leading comments
        while (trimmed.startsWith("--") || trimmed.startsWith("/*")) {
            if (trimmed.startsWith("--")) {
                int newline = trimmed.indexOf('\n');
                if (newline == -1) break;
                trimmed = trimmed.substring(newline + 1).trim();
            } else {
                int endComment = trimmed.indexOf("*/");
                if (endComment == -1) break;
                trimmed = trimmed.substring(endComment + 2).trim();
            }
        }

        if (trimmed.startsWith("insert")) return "insert";
        if (trimmed.startsWith("update")) return "update";
        if (trimmed.startsWith("delete")) return "delete";
        if (trimmed.startsWith("merge")) return "merge";
        if (trimmed.startsWith("truncate")) return "truncate";
        if (trimmed.startsWith("create")) return "create";
        if (trimmed.startsWith("alter")) return "alter";
        if (trimmed.startsWith("drop")) return "drop";

        return "other";
    }
}
