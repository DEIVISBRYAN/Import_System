package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.model.ERQZArchivoDua;
import com.ejemplo.crudpersona.service.ERQZCompraServiceI.Linea;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface DBQPBorradoresDuaServiceI {
    Long guardar(Long id, int version, Long proveedorId, String dua, LocalDate fecha, BigDecimal cambio,
                 List<Linea> lineas, Long documentoId, List<ERQZArchivoDua> archivos);
    List<Resumen> listar(String busqueda);
    Detalle obtener(Long id);
    Long aprobar(Long id, int version);
    void eliminar(Long id, int version);
    boolean puedeAprobar();

    record Resumen(Long id, String dua, String proveedor, LocalDate fecha, String estado, int version,
                   int productos, int unidades, BigDecimal totalPen, Long documentoId, Long compraId) {}
    record Detalle(Resumen resumen, Long proveedorId, BigDecimal tipoCambio, String creadoPor,
                   String aprobadoPor, List<Linea> lineas) {
        public Detalle { lineas = List.copyOf(lineas); }
    }
}
