package com.ejemplo.crudpersona.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "ubicaciones_almacen")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UbicacionAlmacen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String codigo;

    @Column(nullable = false, length = 10)
    private String pasillo;

    @Column(nullable = false)
    private Integer anaquel;

    @Column(nullable = false)
    private Integer nivel;

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
