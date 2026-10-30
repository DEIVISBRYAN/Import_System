package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.model.ERQZArchivoDua;
import java.time.LocalDateTime;
import java.util.List;

public interface DBQPDocumentosDuaServiceI {
    Long crear(List<ERQZArchivoDua> archivos);
    void adjuntar(Long documentoId, List<ERQZArchivoDua> archivos);
    void vincular(Long documentoId, Long compraId);
    void guardarConCompra(Long documentoId, Long compraId, List<ERQZArchivoDua> archivos);
    List<Documento> listar(String busqueda);
    List<Adjunto> archivos(Long documentoId);
    ERQZArchivoDua descargar(Long archivoId);
    void eliminarArchivo(Long archivoId);
    List<Compra> comprasSinDocumento();
    record Documento(Long id, String referencia, Long compraId, String dua, String proveedor, int archivos, LocalDateTime fecha, Long borradorId) {}
    record Adjunto(Long id, String nombre, String tipo, int tamano, LocalDateTime fecha) {}
    record Compra(Long id, String dua, String proveedor, String fecha) {
        @Override public String toString() { return dua + " · " + proveedor + " · " + fecha; }
    }
}
