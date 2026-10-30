package com.ejemplo.crudpersona.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record ComprobanteDocumento(String numero, String tipo, LocalDateTime fecha,
        String empresa, String rucEmpresa, String direccionEmpresa, String cliente,
        String documentoCliente, String metodoPago, BigDecimal subtotal, BigDecimal igv,
        BigDecimal total, String estado, List<Linea> lineas) {
    public ComprobanteDocumento {
        lineas = List.copyOf(lineas);
    }

    public record Linea(int cantidad, String descripcion, BigDecimal precio, BigDecimal importe) {
    }
}
