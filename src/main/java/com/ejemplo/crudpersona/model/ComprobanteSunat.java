package com.ejemplo.crudpersona.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "comprobantes_sunat")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ComprobanteSunat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tipo_comprobante", nullable = false, length = 20)
    private String tipoComprobante; // FACTURA, BOLETA

    @Column(nullable = false, length = 4)
    private String serie; // B001, F001

    @Column(nullable = false)
    private Integer correlativo;

    @Column(name = "cliente_documento", length = 20)
    private String clienteDocumento;

    @Column(name = "cliente_nombre", length = 150)
    private String clienteNombre;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "estado_sunat", nullable = false, length = 30)
    private String estadoSunat; // ACEPTADO, PENDIENTE, RECHAZADO

    @Column(name = "hash_cpe", length = 100)
    private String hashCpe;

    @Column(name = "fecha_emision", nullable = false)
    private LocalDateTime fechaEmision;
    @Column(name = "venta_id") private Long ventaId;
    private BigDecimal subtotal;
    private BigDecimal igv;
    @Column(name = "metodo_pago") private String metodoPago;
    @Column(name = "emisor_nombre") private String emisorNombre;
    @Column(name = "emisor_ruc") private String emisorRuc;
    @Column(name = "emisor_direccion") private String emisorDireccion;
    @Column(name = "pdf_documento") private byte[] pdfDocumento;
    @OneToMany(mappedBy = "comprobante", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default private List<ComprobanteDetalle> detalles = new ArrayList<>();
}
