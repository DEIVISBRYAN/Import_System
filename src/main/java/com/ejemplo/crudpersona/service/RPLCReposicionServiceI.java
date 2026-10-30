package com.ejemplo.crudpersona.service;

import java.math.BigDecimal;
import java.util.List;

public interface RPLCReposicionServiceI {
    String proveedorHabitual(Long productoId);
    List<Proveedor> proveedoresActivos();
    Proveedor proveedorAsignado(Long productoId);
    void asignarProveedor(Long productoId, Long proveedorId);
    void guardarParametros(Long productoId, BigDecimal ventasDia, int diasEntrega, int stockSeguridad, Long proveedorId);
    long urgentes();
    int generar(List<Pedido> pedidos);
    List<Long> confirmarPedidos(List<LineaRevisada> lineas, String observaciones);
    List<ResumenPedido> listarPedidos();
    com.ejemplo.crudpersona.model.PedidoCompraDocumento documentoPedido(Long id);
    byte[] pdfPedido(Long id);
    byte[] excelPedido(Long id);
    record LineaRevisada(Long productoId, Long proveedorId, int cantidad, BigDecimal unitarioUsd, BigDecimal unitarioPen) {}
    record ResumenPedido(Long id, String proveedor, String fecha, String estado, int productos, BigDecimal totalUsd, BigDecimal totalPen, String recepcion) {
        public String numero() { return String.format(java.util.Locale.ROOT, "PC-%06d", id); }
    }
    record Pedido(Long productoId, int cantidad, String proveedor, BigDecimal costoUsd, BigDecimal costoPen) {}
    record Proveedor(Long id, String nombre, String ruc, String contacto) {
        @Override public String toString() { return nombre + (ruc == null || ruc.isBlank() ? "" : " · " + ruc); }
    }
}
