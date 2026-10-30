package com.ejemplo.crudpersona.config;

import com.ejemplo.crudpersona.model.Rol;
import com.ejemplo.crudpersona.model.Usuario;
import com.ejemplo.crudpersona.repository.UsuarioRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.HexFormat;

@Component
@ConditionalOnProperty(name="app.database.kind", havingValue="sqlite")
public class ERQZInicioSqlite implements ApplicationRunner {
    private final UsuarioRepository users;
    private final Environment environment;
    private Path accesoInicial;

    public ERQZInicioSqlite(UsuarioRepository users, Environment environment) {
        this.users=users; this.environment=environment;
    }
    public void run(ApplicationArguments arguments) throws Exception {
        if (environment.getProperty("app.demo.users", Boolean.class, false)) {
            crearCuentasDemo();
            return;
        }
        if (users.count()!=0) return;
        byte[] bytes=new byte[12];new SecureRandom().nextBytes(bytes);
        String password=HexFormat.of().formatHex(bytes);
        Path file=RPLCConfiguracionLocal.archivo(environment).resolveSibling("ACCESO_INICIAL.txt");
        Files.writeString(file,"Usuario: admin\nClave inicial: "+password+"\nCambia esta clave desde Mi perfil y conserva este archivo en privado.\n",StandardOpenOption.CREATE_NEW);
        users.save(Usuario.builder().username("admin").nombreCompleto("Administrador local").rol(Rol.ADMINISTRADOR)
                .activo(true).passwordHash(new BCryptPasswordEncoder().encode(password)).build());
        accesoInicial=file;
    }
    public Path accesoInicial(){return accesoInicial;}

    /** Cuentas de demostración solicitadas; no cambia claves de cuentas existentes. */
    private void crearCuentasDemo() {
        var encoder = new BCryptPasswordEncoder();
        String[] nombres = {"admin.demo", "cajero.demo", "almacen.demo", "gerente.demo", "contador.demo"};
        Rol[] roles = {Rol.ADMINISTRADOR, Rol.CAJERO, Rol.ALMACENERO, Rol.GERENTE, Rol.CONTADOR};
        for (int i = 0; i < nombres.length; i++) {
            if (!users.buscarNombre(nombres[i]).isEmpty()) continue;
            users.save(Usuario.builder().username(nombres[i]).nombreCompleto("Demostración · " + roles[i].getTitulo())
                    .rol(roles[i]).activo(true).passwordHash(encoder.encode("Demo12345")).build());
        }
    }
}
