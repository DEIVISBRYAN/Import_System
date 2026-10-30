package com.ejemplo.crudpersona.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "historial_precio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HistorialPrecio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(name = "tipo_cambio_anterior", precision = 6, scale = 4)
    private BigDecimal tipoCambioAnterior;

    @Column(name = "precio_anterior", precision = 12, scale = 2)
    private BigDecimal precioAnterior;

    @Column(name = "tipo_cambio_nuevo", precision = 6, scale = 4)
    private BigDecimal tipoCambioNuevo;

    @Column(name = "precio_nuevo", precision = 12, scale = 2)
    private BigDecimal precioNuevo;

    @Column(precision = 12, scale = 2)
    private BigDecimal diferencia;

    @Column(name = "fecha_cambio", nullable = false)
    private LocalDateTime fechaCambio;

    @Column(name = "usuario_id")
    private Long usuarioId;
}
