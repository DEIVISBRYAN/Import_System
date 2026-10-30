package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.model.CajaTurno;
import java.math.BigDecimal;

public interface RPLCCajaServiceI {
    CajaTurno abrir(int numero, BigDecimal apertura, Long usuario);
    CajaTurno cerrar(int numero, Long turnoId, BigDecimal contado);
}
