package com.ejemplo.crudpersona.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "lotes")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Lote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;

    @Column(name = "codigo_lote", nullable = false, length = 50)
    private String codigoLote;

    @Column(name = "numero_dua", length = 50)
    private String numeroDua;

    @Column(length = 60)
    private String ubicacion;

    @Column(name = "cantidad_inicial", nullable = false)
    private Integer cantidadInicial;

    @Column(name = "cantidad_disponible", nullable = false)
    private Integer cantidadDisponible;

    @Column(name = "costo_unitario_usd", precision = 12, scale = 4)
    private BigDecimal costoUnitarioUsd;

    @Column(name = "costo_unitario_pen", nullable = false, precision = 12, scale = 4)
    private BigDecimal costoUnitarioPen;

    @Column(name = "fecha_ingreso", nullable = false)
    private LocalDate fechaIngreso;

    @Column(name = "fecha_vencimiento")
    private LocalDate fechaVencimiento;
}
