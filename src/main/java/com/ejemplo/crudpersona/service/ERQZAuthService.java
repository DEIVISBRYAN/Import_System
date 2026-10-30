package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.model.Usuario;
import com.ejemplo.crudpersona.repository.UsuarioRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class ERQZAuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public ERQZAuthService(UsuarioRepository usuarioRepository) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = new BCryptPasswordEncoder();
    }

    /**
     * Valida usuario y contraseña contra la base central.
     * El rol se asigna automáticamente del registro en BD.
     */
    public Usuario autenticar(String username, String rawPassword) {
        if (username == null || username.trim().isEmpty()) {
            throw new RuntimeException("Por favor ingrese su nombre de usuario.");
        }
        if (rawPassword == null || rawPassword.isEmpty()) {
            throw new RuntimeException("Por favor ingrese su contraseña.");
        }

        var encontrados = usuarioRepository.buscarNombre(username).stream()
                .filter(u -> Boolean.TRUE.equals(u.getActivo())).toList();
        if (encontrados.size() != 1) throw new RuntimeException("Usuario o contraseña incorrectos.");
        Usuario usuario = encontrados.get(0);

        String passwordGuardado = usuario.getPasswordHash();
        boolean esBcrypt = passwordGuardado != null && passwordGuardado.startsWith("$2");
        boolean passwordValido = esBcrypt
                ? passwordEncoder.matches(rawPassword, passwordGuardado)
                : rawPassword.equals(passwordGuardado);

        if (!passwordValido) {
            throw new RuntimeException("Usuario o contraseña incorrectos.");
        }

        return usuario;
    }
}
