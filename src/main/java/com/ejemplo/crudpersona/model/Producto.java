package com.ejemplo.crudpersona.model;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "productos")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "codigo_barras", unique = true, length = 60)
    private String codigoBarras;

    @Column(nullable = false, length = 40)
    private String sku;

    @Column(nullable = false, length = 150)
    private String descripcion;

    @Column(name = "stock_actual", nullable = false)
    private Integer stockActual;

    @Column(name = "stock_minimo", nullable = false)
    private Integer stockMinimo;

    @Column(name = "precio_venta_pen", nullable = false, precision = 12, scale = 2)
    private BigDecimal precioVentaPen;

    @Column(name = "costo_promedio_pen", precision = 12, scale = 2)
    private BigDecimal costoPromedioPen;

    @OneToMany(mappedBy = "producto", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Lote> lotes = new ArrayList<>();

    @Override
    public String toString() {
        return descripcion + " · " + sku + " · Stock: " + stockActual;
    }
}
