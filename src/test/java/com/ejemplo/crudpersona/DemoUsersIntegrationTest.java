package com.ejemplo.crudpersona;

import com.ejemplo.crudpersona.model.Rol;
import com.ejemplo.crudpersona.repository.UsuarioRepository;
import com.ejemplo.crudpersona.service.ERQZAuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class DemoUsersIntegrationTest {
    @TempDir Path folder;
    private ConfigurableApplicationContext open() {
        return new SpringApplicationBuilder(RPLCSpringAppConfig.class).run(
                "--app.sqlite.path=" + folder.resolve("demo.db"), "--app.demo.users=true",
                "--spring.config.import=optional:file:./sin-config-demo.properties", "--logging.level.root=WARN");
    }
    @Test void autenticaCincoRolesYConservaClavesAlReiniciar() {
        String[] names = {"admin.demo", "cajero.demo", "almacen.demo", "gerente.demo", "contador.demo"};
        Rol[] roles = {Rol.ADMINISTRADOR, Rol.CAJERO, Rol.ALMACENERO, Rol.GERENTE, Rol.CONTADOR};
        try (var context = open()) {
            var auth = context.getBean(ERQZAuthService.class);
            var users = context.getBean(UsuarioRepository.class);
            assertEquals(5, users.count());
            for (int i = 0; i < names.length; i++) {
                var user = auth.autenticar(names[i], "Demo12345");
                assertEquals(roles[i], user.getRol());
                assertTrue(user.getPasswordHash().startsWith("$2"));
            }
            assertThrows(RuntimeException.class, () -> auth.autenticar("admin.demo", "incorrecta"));
            var admin = users.findByUsernameAndActivoTrue("admin.demo").orElseThrow();
            admin.setPasswordHash(new BCryptPasswordEncoder().encode("ClaveCambiada123"));
            users.save(admin);
        }
        try (var context = open()) {
            assertEquals(5, context.getBean(UsuarioRepository.class).count());
            var auth = context.getBean(ERQZAuthService.class);
            assertNotNull(auth.autenticar("admin.demo", "ClaveCambiada123"));
            assertThrows(RuntimeException.class, () -> auth.autenticar("admin.demo", "Demo12345"));
        }
    }
}
