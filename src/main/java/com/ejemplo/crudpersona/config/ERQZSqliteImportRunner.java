package com.ejemplo.crudpersona.config;

import com.ejemplo.crudpersona.model.LocalDateTextConverter;
import com.ejemplo.crudpersona.model.LocalDateTimeTextConverter;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/** Importacion explicita, transaccional e irrepetible; nunca escribe en la base de origen. */
@Component
public class ERQZSqliteImportRunner implements ApplicationRunner {
    private static final List<String> TABLES = List.of("usuarios", "productos", "proveedores", "lotes",
            "caja_turno", "ventas", "detalle_venta", "comprobantes_sunat", "comprobante_detalle",
            "tipo_cambio", "historial_precio", "reposicion_config", "pedidos_reposicion", "compras_dua",
            "detalle_compra_dua", "ubicaciones_almacen");
    private static final Set<String> DATES = Set.of("fecha_ingreso", "fecha_vencimiento", "fecha");
    private final DataSource dataSource;
    private final Environment environment;

    public ERQZSqliteImportRunner(DataSource dataSource, Environment environment) {
        this.dataSource = dataSource;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        String source = environment.getProperty("app.database.import-sqlite", System.getenv("APP_MIGRATE_SQLITE"));
        if (source == null || source.isBlank() || "sqlite".equals(environment.getProperty("app.database.kind"))) return;
        importar(Path.of(source));
    }

    public Map<String, Long> importar(Path source) throws Exception {
        if (!Files.isRegularFile(source)) throw new IllegalArgumentException("No existe la base SQLite indicada.");
        Class.forName("org.sqlite.JDBC");
        Path backupDir = Path.of(".local", "backups").toAbsolutePath();
        Files.createDirectories(backupDir);
        Path backup = backupDir.resolve("importadora-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")) + ".db");
        try (Connection origin = DriverManager.getConnection(sqliteUrl(source)); Statement st = origin.createStatement()) {
            st.execute("VACUUM INTO '" + backup.toString().replace("'", "''") + "'");
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        try (Connection sqlite = DriverManager.getConnection(sqliteUrl(backup)); Connection pg = dataSource.getConnection()) {
            pg.setAutoCommit(false);
            try (Statement st = pg.createStatement()) {
                st.execute("SELECT pg_advisory_xact_lock(73145001)");
                for (String table : TABLES) {
                    try (ResultSet rs = st.executeQuery("SELECT count(*) FROM " + table)) {
                        rs.next();
                        if (rs.getLong(1) != 0) throw new IllegalStateException("Importacion cancelada: PostgreSQL ya contiene datos en " + table + ". No se reemplazo nada.");
                    }
                }
                Set<String> sources = new HashSet<>();
                try (ResultSet rs = sqlite.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
                    while (rs.next()) sources.add(rs.getString("TABLE_NAME"));
                }
                for (String table : TABLES) {
                    if (!sources.contains(table)) continue;
                    counts.put(table, copiar(sqlite, pg, table));
                }
                if (sources.contains("datos_empresa")) {
                    st.executeUpdate("DELETE FROM datos_empresa");
                    copiar(sqlite, pg, "datos_empresa");
                }
                st.executeUpdate("UPDATE secuencias_comprobante s SET siguiente = GREATEST(COALESCE((SELECT MAX(v.correlativo) + 1 FROM ventas v WHERE v.serie = s.serie), 1), COALESCE((SELECT MAX(c.correlativo) + 1 FROM comprobantes_sunat c WHERE c.serie = s.serie), 1))");
                st.executeUpdate("UPDATE comprobantes_sunat SET estado_sunat='PENDIENTE', hash_cpe=NULL WHERE estado_sunat='ACEPTADO' AND (hash_cpe LIKE 'LOCAL-%' OR hash_cpe='cE4u0Wp1fD9mZq9X2k5R8vL0==')");
                for (String table : TABLES) {
                    if (!table.equals("reposicion_config")) {
                        st.execute("SELECT setval(pg_get_serial_sequence('" + table + "', 'id'), COALESCE((SELECT max(id) FROM " + table + "), 1), EXISTS(SELECT 1 FROM " + table + "))");
                    }
                }
                pg.commit();
            } catch (Exception e) {
                pg.rollback();
                throw e;
            }
        }
        System.out.println("SQLite conservado. Respaldo: " + backup + ". Registros importados a PostgreSQL: " + counts);
        return counts;
    }

    private long copiar(Connection sqlite, Connection pg, String table) throws SQLException {
        Set<String> targetColumns = new HashSet<>();
        Map<String, Integer> types = new HashMap<>();
        try (ResultSet columns = pg.getMetaData().getColumns(null, "public", table, "%")) {
            while (columns.next()) {
                String name = columns.getString("COLUMN_NAME");
                targetColumns.add(name);
                types.put(name, columns.getInt("DATA_TYPE"));
            }
        }
        try (Statement reader = sqlite.createStatement(); ResultSet rows = reader.executeQuery("SELECT * FROM " + table)) {
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= rows.getMetaData().getColumnCount(); i++) {
                String name = rows.getMetaData().getColumnName(i);
                if (targetColumns.contains(name)) columns.add(name);
            }
            String sql = "INSERT INTO " + table + " (" + String.join(",", columns) + ") VALUES (" + String.join(",", Collections.nCopies(columns.size(), "?")) + ")";
            long count = 0;
            try (PreparedStatement writer = pg.prepareStatement(sql)) {
                while (rows.next()) {
                    for (int i = 0; i < columns.size(); i++) {
                        String name = columns.get(i);
                        Object value = rows.getObject(name);
                        int type = types.get(name);
                        if (value == null) writer.setNull(i + 1, type);
                        else if (type == Types.BOOLEAN || type == Types.BIT) writer.setBoolean(i + 1, rows.getInt(name) != 0);
                        else if (DATES.contains(name)) {
                            var converter = new LocalDateTextConverter();
                            writer.setString(i + 1, converter.convertToDatabaseColumn(converter.convertToEntityAttribute(value.toString())));
                        } else if (name.startsWith("fecha_") || name.equals("creado_en")) {
                            var converter = new LocalDateTimeTextConverter();
                            writer.setString(i + 1, converter.convertToDatabaseColumn(converter.convertToEntityAttribute(value.toString())));
                        } else writer.setObject(i + 1, value);
                    }
                    writer.executeUpdate();
                    count++;
                }
            }
            return count;
        }
    }

    private String sqliteUrl(Path file) {
        return "jdbc:sqlite:file:" + file.toAbsolutePath().toString().replace('\\', '/') + "?mode=ro";
    }
}
