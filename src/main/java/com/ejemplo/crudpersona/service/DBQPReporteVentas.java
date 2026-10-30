package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.model.Venta;
import java.math.BigDecimal;
import java.util.List;

public final class DBQPReporteVentas {
    private DBQPReporteVentas() {}
    public static ERQZReporteTabular crear(List<Venta> ventas, String alcance) {
        BigDecimal total = ventas.stream().filter(v -> !"ANULADA".equalsIgnoreCase(v.getEstado()))
                .map(v -> v.getTotal() == null ? BigDecimal.ZERO : v.getTotal()).reduce(BigDecimal.ZERO, BigDecimal::add);
        long anuladas = ventas.stream().filter(v -> "ANULADA".equalsIgnoreCase(v.getEstado())).count();
        return new ERQZReporteTabular("Reporte de ventas", alcance + " | Registros: " + ventas.size() + " | Anuladas: " + anuladas
                + " | Total no anulado PEN: " + total.toPlainString() + ". Las anuladas se excluyen de este total.",
                List.of("Tipo", "Comprobante", "Fecha", "Cliente", "DNI / RUC", "Pago", "Base PEN", "IGV PEN", "Total PEN", "Estado"),
                ventas.stream().map(v -> ERQZReporteTabular.fila(v.getTipoComprobante(), v.getSerie() + "-" + String.format(java.util.Locale.ROOT, "%06d", v.getCorrelativo()),
                        v.getFechaEmision(), v.getClienteNombre(), v.getClienteDocumento(), v.getMetodoPago(), v.getSubtotal(), v.getIgv(), v.getTotal(), v.getEstado())).toList());
    }
}
