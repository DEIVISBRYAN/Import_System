package com.ejemplo.crudpersona.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

public record PedidoCompraDocumento(Long id, String proveedor, String rucProveedor, String contacto,
        String empresa, String rucEmpresa, String direccionEmpresa, String fecha, String estado,
        String observaciones, BigDecimal totalUsd, BigDecimal totalPen, List<Linea> lineas) {
    public PedidoCompraDocumento { lineas = List.copyOf(lineas); }
    public String numero() { return String.format(Locale.ROOT, "PC-%06d", id); }
    public record Linea(String sku, String producto, int cantidad, BigDecimal unitarioUsd,
                        BigDecimal unitarioPen, BigDecimal totalUsd, BigDecimal totalPen) {}
}
