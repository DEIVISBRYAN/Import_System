package com.ejemplo.crudpersona.service;

import java.math.BigDecimal;
import java.util.List;

public interface ERQZVentaServiceI {
    Long cobrar(int caja, Long usuario, String tipo, String documento, String cliente, String pago, List<Linea> lineas);
    void anular(Long ventaId);
    record Linea(Long productoId, int cantidad, BigDecimal precio) {}
}
