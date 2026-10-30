package com.ejemplo.crudpersona.repository;

import com.ejemplo.crudpersona.model.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    Optional<Usuario> findByUsernameAndActivoTrue(String username);
    boolean existsByUsername(String username);
    @org.springframework.data.jpa.repository.Query("SELECT u FROM Usuario u WHERE lower(trim(u.username)) = lower(trim(:username))")
    java.util.List<Usuario> buscarNombre(String username);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT u FROM Usuario u ORDER BY u.id")
    java.util.List<Usuario> bloquearUsuarios();
}
