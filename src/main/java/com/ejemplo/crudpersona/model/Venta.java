package com.ejemplo.crudpersona.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ventas")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Venta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDateTime fechaEmision;

    @Column(name = "tipo_comprobante", nullable = false, length = 20)
    private String tipoComprobante; // BOLETA, FACTURA

    @Column(nullable = false, length = 4)
    private String serie;

    @Column(nullable = false)
    private Integer correlativo;

    @Column(name = "cliente_documento", length = 20)
    private String clienteDocumento;

    @Column(name = "cliente_nombre", length = 150)
    private String clienteNombre;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal igv;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "metodo_pago", nullable = false, length = 20)
    private String metodoPago; // EFECTIVO, YAPE_PLIN, TARJETA, MIXTO

    @Column(name = "caja_turno_id")
    private Long cajaTurnoId;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Column(name = "estado", nullable = false, length = 20)
    private String estado; // COMPLETADA, ANULADA

    @OneToMany(mappedBy = "venta", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<DetalleVenta> detalles = new ArrayList<>();
}
