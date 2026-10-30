package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.model.HistorialPrecio;
import com.ejemplo.crudpersona.model.TipoCambio;
import com.ejemplo.crudpersona.repository.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class DBQPTipoCambioService implements DBQPTipoCambioServiceI {
    private final TipoCambioRepository cambios;
    private final ProductoRepository productos;
    private final HistorialPrecioRepository historial;
    private final JdbcTemplate jdbc;
    private final org.springframework.context.ApplicationEventPublisher eventos;
    public DBQPTipoCambioService(TipoCambioRepository cambios, ProductoRepository productos,
                            HistorialPrecioRepository historial, JdbcTemplate jdbc, org.springframework.context.ApplicationEventPublisher eventos) {
        this.cambios = cambios; this.productos = productos; this.historial = historial; this.jdbc = jdbc;
        this.eventos = eventos;
    }
    public BigDecimal actual() { return cambios.findTopByOrderByFechaDesc().map(TipoCambio::getVenta).orElse(new BigDecimal("3.75")); }
    @Transactional public void guardarManual(BigDecimal esperado, BigDecimal nuevo) {
        validarTasa(nuevo);
        LocalDate hoy = LocalDate.now(java.time.ZoneId.of("America/Lima"));
        jdbc.execute(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT pg_advisory_xact_lock(74021, 1)"));
        if (esperado == null || actual().compareTo(esperado) != 0) throw new IllegalStateException("Otra caja cambió la tasa. Vuelve a abrir Tipo de cambio.");
        TipoCambio registro = cambios.findByFecha(hoy).orElseGet(TipoCambio::new);
        registro.setFecha(hoy); registro.setCompra(nuevo); registro.setVenta(nuevo);
        registro.setOrigen("MANUAL"); cambios.save(registro);
        eventos.publishEvent(new ERQZTipoCambioActualizado());
    }
    @Transactional public void aplicar(BigDecimal esperado, BigDecimal nuevo) {
        validarTasa(nuevo);
        // Serializa el cambio global aun cuando todavia no existe la primera tasa.
        jdbc.execute(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT pg_advisory_xact_lock(74021, 1)"));
        BigDecimal anterior = actual();
        if (esperado == null || anterior.compareTo(esperado) != 0)
            throw new IllegalStateException("Otra caja cambió la tasa. Vuelve a abrir Tipo de cambio.");
        var ids = productos.idsParaActualizar();
        for (Long id : ids) {
            var producto = productos.bloquear(id).orElseThrow();
            BigDecimal previo = producto.getPrecioVentaPen();
            BigDecimal precio = DBQPTipoCambioServiceI.recalcular(previo, anterior, nuevo);
            historial.save(HistorialPrecio.builder().producto(producto).tipoCambioAnterior(anterior)
                    .tipoCambioNuevo(nuevo).precioAnterior(previo).precioNuevo(precio)
                    .diferencia(precio.subtract(previo)).fechaCambio(LocalDateTime.now()).build());
            producto.setPrecioVentaPen(precio);
        }
        TipoCambio cambio = cambios.findByFecha(LocalDate.now()).orElseGet(TipoCambio::new);
        cambio.setFecha(LocalDate.now()); cambio.setCompra(nuevo); cambio.setVenta(nuevo); cambio.setOrigen("MANUAL");
        cambios.save(cambio);
        eventos.publishEvent(new ERQZTipoCambioActualizado());
    }
    private static void validarTasa(BigDecimal tasa) {
        if(tasa==null||tasa.signum()<=0||tasa.compareTo(new BigDecimal("99.9999"))>0||tasa.stripTrailingZeros().scale()>4)
            throw new IllegalArgumentException("Ingresa un tipo de cambio entre 0.0001 y 99.9999, con hasta cuatro decimales.");
    }
}
