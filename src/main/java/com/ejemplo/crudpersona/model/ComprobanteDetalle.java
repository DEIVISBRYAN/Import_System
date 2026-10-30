package com.ejemplo.crudpersona.model;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;

@Entity
@Table(name = "comprobante_detalle")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ComprobanteDetalle {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comprobante_id", nullable = false)
    private ComprobanteSunat comprobante;
    private String descripcion;
    private Integer cantidad;
    @Column(name = "precio_unitario")
    private BigDecimal precioUnitario;
    private BigDecimal importe;
}
