package com.ejemplo.crudpersona.service;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.regex.Pattern;

public final class DBQPSqlDialect {
    private DBQPSqlDialect() {}
    public static Long nullableLong(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        long value=rs.getLong(column);
        return rs.wasNull()?null:value;
    }
    private static final Pattern QUOTED = Pattern.compile("'(?:''|[^'])*'|\"(?:\"\"|[^\"])*\"|--[^\\r\\n]*|/\\*[\\s\\S]*?\\*/");

    public static String sql(JdbcTemplate jdbc, String sql) {
        if (jdbc.getDataSource() == null) return sql;
        boolean sqlite = Boolean.TRUE.equals(jdbc.execute((ConnectionCallback<Boolean>) c ->
                "SQLite".equalsIgnoreCase(c.getMetaData().getDatabaseProductName())));
        return sqlite ? sqlite(sql) : sql;
    }

    public static String sqlite(String sql) {
        if (sql.trim().matches("(?is)SELECT\\s+pg_advisory_xact_lock\\s*\\([\\d,\\s]+\\)\\s*;?")) return "SELECT 1";
        var result = new StringBuilder();
        var matcher = QUOTED.matcher(sql);
        int previous = 0;
        while (matcher.find()) {
            result.append(code(sql.substring(previous, matcher.start()))).append(matcher.group());
            previous = matcher.end();
        }
        return result.append(code(sql.substring(previous))).toString();
    }

    private static String code(String sql) {
        return sql.replaceAll("(?i)\\s+FOR\\s+(?:NO\\s+KEY\\s+)?UPDATE(?:\\s+OF\\s+[a-zA-Z_][a-zA-Z_0-9]*)?", "")
                .replaceAll("(?i)\\bILIKE\\b", "LIKE")
                .replaceAll("(?i)\\boctet_length\\s*\\(", "length(")
                .replaceAll("(?i)\\b(?:now\\s*\\(\\s*\\)|localtimestamp\\b)", "(strftime('%Y-%m-%d %H:%M:%f','now','localtime'))");
    }
}
