package com.ejemplo.crudpersona.config;

import java.nio.file.Path;
import org.springframework.core.env.Environment;

public final class RPLCConfiguracionLocal {
    private RPLCConfiguracionLocal() {}

    public static Path archivo(Environment environment) {
        String explicit = environment.getProperty("app.sqlite.path", "").trim();
        if (!explicit.isEmpty()) return Path.of(explicit).toAbsolutePath().normalize();
        String appData = System.getenv("APPDATA");
        Path base = appData == null || appData.isBlank()
                ? Path.of(System.getProperty("user.home")) : Path.of(appData);
        return base.resolve("GestionImportaciones").resolve("sistema.db").toAbsolutePath().normalize();
    }
}
