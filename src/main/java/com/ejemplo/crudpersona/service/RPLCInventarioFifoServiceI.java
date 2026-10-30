package com.ejemplo.crudpersona.service;

import java.util.List;

/**
 * Contrato del servicio de despacho de inventario con política FIFO.
 * Al declarar la interfaz, Spring AOP puede usar proxies JDK (java.lang.reflect.Proxy)
 * en lugar de CGLIB, lo cual es compatible con el sistema de módulos JPMS.
 */
public interface RPLCInventarioFifoServiceI {

    /**
     * Descuenta stock usando primero los lotes más antiguos (FIFO).
     * @param productoId ID del producto a despachar
     * @param cantidadADespachar cantidad de unidades a descontar
     */
    void despacharFifo(Long productoId, int cantidadADespachar);

    /**
     * Descuenta stock FIFO y devuelve qué lotes fueron afectados.
     */
    List<DespachoLote> despacharFifoConDetalle(Long productoId, int cantidadADespachar);

    record DespachoLote(Long loteId, String codigoLote, int cantidad) {
    }
}
