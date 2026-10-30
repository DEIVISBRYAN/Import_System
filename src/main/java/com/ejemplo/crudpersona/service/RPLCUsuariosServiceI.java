package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.model.Rol;
import com.ejemplo.crudpersona.model.Usuario;

public interface RPLCUsuariosServiceI {
    Usuario crear(String nombreUsuario, String nombre, String clave, Rol rol);
    void cambiarClave(Long id, String clave);
    void cambiarRol(Long id, Rol rol);
    void cambiarEstado(Long id);
    Perfil miPerfil();
    Usuario guardarMiPerfil(String usuario,String nombre,String claveActual,String nuevaClave);
    record Perfil(String usuario,String nombre,String rol,boolean activo) {}
}
