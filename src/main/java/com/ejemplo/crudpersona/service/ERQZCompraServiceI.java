package com.ejemplo.crudpersona.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ERQZCompraServiceI {
    Long registrar(Long proveedorId, String dua, LocalDate fecha, BigDecimal tipoCambio, List<Linea> lineas);
    Long registrarConDocumentos(Long proveedorId, String dua, LocalDate fecha, BigDecimal tipoCambio, List<Linea> lineas,
                               Long documentoId, List<com.ejemplo.crudpersona.model.ERQZArchivoDua> archivos);
    Recepcion prepararRecepcion(Long pedidoId);
    Long recibirPedido(Long pedidoId, String dua, LocalDate fecha, BigDecimal tipoCambio, List<LineaRecepcion> lineas);
    record Recepcion(Long pedidoId, String numero, String proveedor, List<ProductoRecepcion> productos) {
        public Recepcion { productos = List.copyOf(productos); }
    }
    record ProductoRecepcion(Long detalleId, String sku, String producto, int cantidad, BigDecimal estimadoUsd) {}
    record LineaRecepcion(Long detalleId, int cantidad, String lote, String ubicacion,
                          BigDecimal fobUsd, BigDecimal gastosUsd, BigDecimal margen) {}
    record Linea(String sku, String descripcion, String codigoBarras, String lote, String ubicacion,
                 int cantidad, BigDecimal fobUsd, BigDecimal gastosUsd, BigDecimal margen) {}
}
