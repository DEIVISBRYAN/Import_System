package com.ejemplo.crudpersona.service;

import java.math.BigDecimal;

public interface DBQPTipoCambioServiceI {
    BigDecimal actual();
    void aplicar(BigDecimal esperado, BigDecimal nuevo);
    void guardarManual(BigDecimal esperado, BigDecimal nuevo);
    static BigDecimal recalcular(BigDecimal precio, BigDecimal anterior, BigDecimal nuevo) {
        return (precio == null ? BigDecimal.ZERO : precio).multiply(nuevo)
                .divide(anterior, 2, java.math.RoundingMode.HALF_UP);
    }
}
