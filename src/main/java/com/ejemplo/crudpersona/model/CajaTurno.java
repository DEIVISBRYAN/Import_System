package com.ejemplo.crudpersona.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "caja_turno")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CajaTurno {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "numero_caja", nullable = false)
    private Integer numeroCaja;

    @Column(name = "fecha_apertura", nullable = false)
    private LocalDateTime fechaApertura;

    @Column(name = "fecha_cierre")
    private LocalDateTime fechaCierre;

    @Column(name = "monto_apertura", nullable = false, precision = 12, scale = 2)
    private BigDecimal montoApertura;

    @Column(name = "monto_efectivo_ventas", precision = 12, scale = 2)
    private BigDecimal montoEfectivoVentas;

    @Column(name = "monto_yape_plin", precision = 12, scale = 2)
    private BigDecimal montoYapePlin;

    @Column(name = "monto_tarjeta", precision = 12, scale = 2)
    private BigDecimal montoTarjeta;

    @Column(name = "monto_mixto", precision = 12, scale = 2)
    private BigDecimal montoMixto;

    @Column(name = "monto_contado", precision = 12, scale = 2)
    private BigDecimal montoContado;

    @Column(name = "monto_esperado", precision = 12, scale = 2)
    private BigDecimal montoEsperado;

    @Column(name = "diferencia", precision = 12, scale = 2)
    private BigDecimal diferencia;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(nullable = false, length = 20)
    private String estado; // ABIERTA, CERRADA
}
