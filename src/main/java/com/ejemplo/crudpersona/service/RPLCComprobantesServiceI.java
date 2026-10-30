package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.model.*;

public interface RPLCComprobantesServiceI {
    Long registrarVenta(Long ventaId);
    void sincronizarVentas();
    ComprobanteDocumento documento(Long id);
    byte[] generarPdf(Long id);
    DatosEmpresa datosEmpresa();
    void guardarEmpresa(String nombre, String ruc, String direccion);
}
