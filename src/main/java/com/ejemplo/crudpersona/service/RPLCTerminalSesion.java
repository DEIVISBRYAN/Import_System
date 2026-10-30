package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.model.Usuario;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class RPLCTerminalSesion {
    private final int numeroCaja;
    private Usuario usuario;
    public RPLCTerminalSesion(Environment environment) {
        numeroCaja = environment.getProperty("app.caja.numero", Integer.class, 1);
        if (numeroCaja < 1 || numeroCaja > 3) throw new IllegalArgumentException("Configura app.caja.numero entre 1 y 3.");
    }
    public int numeroCaja() { return numeroCaja; }
    public Long usuarioId() { return usuario == null ? null : usuario.getId(); }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
}
