package com.ejemplo.crudpersona.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.nio.file.Files;

@Configuration
public class DBQPDatabaseConfig {

    @Bean
    public DataSource dataSource(Environment environment) {
        if (!"sqlite".equals(environment.getProperty("app.database.kind"))) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(environment.getRequiredProperty("spring.datasource.url"));
            config.setUsername(environment.getRequiredProperty("spring.datasource.username"));
            config.setPassword(environment.getRequiredProperty("spring.datasource.password"));
            config.setDriverClassName("org.postgresql.Driver");
            config.setMaximumPoolSize(6);
            config.setMinimumIdle(1);
            config.setConnectionTimeout(10000);
            config.setPoolName("ImportadoraPostgreSQL");
            return new HikariDataSource(config);
        }
        var dbFile = RPLCConfiguracionLocal.archivo(environment);
        try { Files.createDirectories(dbFile.getParent()); }
        catch (java.io.IOException e) { throw new IllegalStateException("No se puede crear la carpeta de datos SQLite: " + dbFile.getParent(), e); }
        String jdbcUrl = "jdbc:sqlite:" + dbFile;

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setDriverClassName("org.sqlite.JDBC");
        config.addDataSourceProperty("foreign_keys", "true");
        config.addDataSourceProperty("journal_mode", "WAL");
        config.addDataSourceProperty("busy_timeout", "10000");
        config.addDataSourceProperty("transaction_mode", "IMMEDIATE");
        // Una conexion serializa las operaciones locales y evita escritores simultaneos.
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(15000);
        config.setPoolName("GestionImportacionesSQLite");

        return new HikariDataSource(config);
    }
}
