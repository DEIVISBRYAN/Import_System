package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.model.Rol;
import com.ejemplo.crudpersona.model.Usuario;
import com.ejemplo.crudpersona.repository.UsuarioRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Locale;

@Service
public class RPLCUsuariosService implements RPLCUsuariosServiceI {
    private final UsuarioRepository usuarios;
    private final RPLCTerminalSesion sesion;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    public RPLCUsuariosService(UsuarioRepository usuarios, RPLCTerminalSesion sesion) {
        this.usuarios = usuarios; this.sesion = sesion;
    }
    private List<Usuario> administrar() {
        var todos = usuarios.bloquearUsuarios();
        if (todos.stream().noneMatch(u -> u.getId().equals(sesion.usuarioId())
                && Boolean.TRUE.equals(u.getActivo()) && u.getRol() == Rol.ADMINISTRADOR))
            throw new IllegalStateException("Solo un administrador activo puede administrar usuarios.");
        return todos;
    }
    private Usuario buscar(List<Usuario> todos, Long id) {
        return todos.stream().filter(u -> u.getId().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado."));
    }
    private String hash(String clave) {
        if (clave == null || clave.isBlank()) throw new IllegalArgumentException("Ingresa una contraseña.");
        if (clave.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
            throw new IllegalArgumentException("La contraseña no puede superar 72 bytes.");
        return encoder.encode(clave);
    }
    @Transactional public Usuario crear(String nombreUsuario, String nombre, String clave, Rol rol) {
        administrar();
        String username = nombreUsuario == null ? "" : nombreUsuario.trim().toLowerCase(Locale.ROOT);
        if (username.isBlank() || nombre == null || nombre.isBlank() || rol == null)
            throw new IllegalArgumentException("Completa usuario, nombre y rol.");
        if (!usuarios.buscarNombre(username).isEmpty()) throw new IllegalArgumentException("Ya existe un usuario con ese nombre.");
        return usuarios.save(Usuario.builder().username(username).nombreCompleto(nombre.trim())
                .passwordHash(hash(clave)).rol(rol).activo(true).build());
    }
    @Transactional public void cambiarClave(Long id, String clave) {
        buscar(administrar(), id).setPasswordHash(hash(clave));
    }
    @Transactional public void cambiarRol(Long id, Rol rol) {
        var todos = administrar(); Usuario usuario = buscar(todos, id);
        if (rol == null) throw new IllegalArgumentException("Selecciona un rol.");
        protegerAdministrador(todos, usuario, rol, Boolean.TRUE.equals(usuario.getActivo()));
        usuario.setRol(rol);
    }
    @Transactional public void cambiarEstado(Long id) {
        var todos = administrar(); Usuario usuario = buscar(todos, id);
        boolean activo = !Boolean.TRUE.equals(usuario.getActivo());
        if (!activo && id.equals(sesion.usuarioId()))
            throw new IllegalArgumentException("No puedes desactivar tu propia sesión.");
        protegerAdministrador(todos, usuario, usuario.getRol(), activo);
        usuario.setActivo(activo);
    }
    private void protegerAdministrador(List<Usuario> todos, Usuario usuario, Rol nuevoRol, boolean activo) {
        if (usuario.getRol() == Rol.ADMINISTRADOR && Boolean.TRUE.equals(usuario.getActivo())
                && (nuevoRol != Rol.ADMINISTRADOR || !activo)
                && todos.stream().noneMatch(u -> !u.getId().equals(usuario.getId())
                    && u.getRol() == Rol.ADMINISTRADOR && Boolean.TRUE.equals(u.getActivo())))
            throw new IllegalArgumentException("Debe quedar al menos un administrador activo.");
    }
    @Transactional(readOnly=true) public Perfil miPerfil() {
        Long id=sesion.usuarioId();
        var usuario=id==null?null:usuarios.findById(id).orElse(null);
        if(usuario==null || !Boolean.TRUE.equals(usuario.getActivo()))throw new IllegalStateException("La sesión no está activa. Vuelve a ingresar.");
        return new Perfil(usuario.getUsername(),usuario.getNombreCompleto(),usuario.getRol().getTitulo(),true);
    }
    @Transactional public Usuario guardarMiPerfil(String nombreUsuario,String nombre,String claveActual,String nuevaClave) {
        Long id=sesion.usuarioId();
        var todos=usuarios.bloquearUsuarios();var usuario=buscar(todos,id);
        if(!Boolean.TRUE.equals(usuario.getActivo()))throw new IllegalStateException("La sesión no está activa.");
        String username=nombreUsuario==null?"":nombreUsuario.trim().toLowerCase(Locale.ROOT);
        if(username.isBlank() || username.length()>50 || nombre==null || nombre.isBlank() || nombre.trim().length()>120)
            throw new IllegalArgumentException("Ingresa un usuario de hasta 50 caracteres y un nombre de hasta 120.");
        if(todos.stream().anyMatch(u->!u.getId().equals(id) && u.getUsername().trim().equalsIgnoreCase(username)))
            throw new IllegalArgumentException("Ese nombre de usuario ya existe.");
        boolean cambiaClave=nuevaClave!=null && !nuevaClave.isEmpty();
        if(cambiaClave || !username.equalsIgnoreCase(usuario.getUsername())) {
            String guardada=usuario.getPasswordHash();
            boolean correcta=claveActual!=null && (guardada.startsWith("$2")?encoder.matches(claveActual,guardada):guardada.equals(claveActual));
            if(!correcta)throw new IllegalArgumentException("La contraseña actual no es correcta.");
        }
        if(cambiaClave){if(nuevaClave.length()<8)throw new IllegalArgumentException("La nueva contraseña debe tener al menos 8 caracteres.");usuario.setPasswordHash(hash(nuevaClave));}
        usuario.setUsername(username);usuario.setNombreCompleto(nombre.trim());return usuario;
    }
}
