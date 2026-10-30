package com.ejemplo.crudpersona.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "tipo_cambio")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TipoCambio {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate fecha;

    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal compra;

    @Column(nullable = false, precision = 6, scale = 4)
    private BigDecimal venta;

    @Column(length = 20)
    private String origen; // SUNAT, SBS, MANUAL
}