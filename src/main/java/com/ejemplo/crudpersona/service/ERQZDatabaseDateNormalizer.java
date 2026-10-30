package com.ejemplo.crudpersona.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;

@Component
public class ERQZDatabaseDateNormalizer {

    private final JdbcTemplate jdbcTemplate;
    private final boolean sqlite;

    public ERQZDatabaseDateNormalizer(JdbcTemplate jdbcTemplate, Environment environment) {
        this.jdbcTemplate = jdbcTemplate;
        this.sqlite = "sqlite".equals(environment.getProperty("app.database.kind"));
    }

    public void normalize() {
        if (!sqlite) return;
        normalizeTimestamp("ventas", "fecha_emision");
        normalizeTimestamp("caja_turno", "fecha_apertura");
        normalizeTimestamp("caja_turno", "fecha_cierre");
        normalizeTimestamp("comprobantes_sunat", "fecha_emision");
        normalizeTimestamp("historial_precio", "fecha_cambio");
        normalizeTimestamp("usuarios", "creado_en");
        normalizeDate("lotes", "fecha_ingreso");
        normalizeDate("lotes", "fecha_vencimiento");
        normalizeDate("tipo_cambio", "fecha");
        normalizeDate("compras_dua", "fecha_ingreso");
    }

    private void normalizeTimestamp(String table, String column) {
        jdbcTemplate.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbcTemplate, """
                UPDATE %s
                SET %s = CASE
                    WHEN length(trim(%s)) > 10 THEN strftime('%%Y-%%m-%%d %%H:%%M:%%f', CAST(%s AS INTEGER) / 1000, 'unixepoch')
                    ELSE strftime('%%Y-%%m-%%d %%H:%%M:%%f', CAST(%s AS INTEGER), 'unixepoch')
                END
                WHERE %s IS NOT NULL
                  AND %s NOT LIKE '%%-%%'
                  AND %s GLOB '[0-9]*'
                """.formatted(table, column, column, column, column, column, column, column)));
        jdbcTemplate.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbcTemplate, """
                UPDATE %s
                SET %s = %s || '.000'
                WHERE %s IS NOT NULL
                  AND %s LIKE '____-__-__ __:__:__'
                  AND %s NOT LIKE '%%.%%'
                """.formatted(table, column, column, column, column, column)));
        jdbcTemplate.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbcTemplate, """
                UPDATE %s
                SET %s = %s || ' 00:00:00.000'
                WHERE %s IS NOT NULL
                  AND %s LIKE '____-__-__'
                  AND length(trim(%s)) = 10
                """.formatted(table, column, column, column, column, column)));
    }

    private void normalizeDate(String table, String column) {
        jdbcTemplate.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbcTemplate, """
                UPDATE %s
                SET %s = CASE
                    WHEN length(trim(%s)) > 10 THEN strftime('%%Y-%%m-%%d 00:00:00.000', CAST(%s AS INTEGER) / 1000, 'unixepoch')
                    ELSE strftime('%%Y-%%m-%%d 00:00:00.000', CAST(%s AS INTEGER), 'unixepoch')
                END
                WHERE %s IS NOT NULL
                  AND %s NOT LIKE '%%-%%'
                  AND %s GLOB '[0-9]*'
                """.formatted(table, column, column, column, column, column, column, column)));
        jdbcTemplate.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbcTemplate, """
                UPDATE %s
                SET %s = %s || ' 00:00:00.000'
                WHERE %s IS NOT NULL
                  AND %s LIKE '____-__-__'
                  AND length(trim(%s)) = 10
                """.formatted(table, column, column, column, column, column)));
    }
}
