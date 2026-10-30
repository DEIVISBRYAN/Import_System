package com.ejemplo.crudpersona.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "usuarios")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "nombre_completo", nullable = false, length = 120)
    private String nombreCompleto;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Rol rol;

    @Builder.Default
    @Column(nullable = false)
    private Boolean activo = true;

    @Column(name = "creado_en")
    private LocalDateTime creadoEn;

    @PrePersist
    public void prePersist() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now();
        }
    }
}