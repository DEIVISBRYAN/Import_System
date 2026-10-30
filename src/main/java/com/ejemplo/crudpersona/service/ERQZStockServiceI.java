package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.model.Lote;
import com.ejemplo.crudpersona.model.Producto;
import java.math.BigDecimal;
import java.util.Map;

public interface ERQZStockServiceI {
    Producto guardarProducto(Producto datos, String lote, String ubicacion, int inicial, BigDecimal costoUsd);
    void agregarLote(Long productoId, Lote datos);
    int ajustar(Long productoId, Long lotePreferido, int cantidad, String motivo);
    void editarLote(Long loteId, int esperado, int cantidad, String ubicacion);
    void moverLote(Long loteId, String ubicacion);
    void vaciar(Map<Long, Integer> cantidadesEsperadas);
}
