package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.model.CajaTurno;
import com.ejemplo.crudpersona.repository.CajaRepository;
import com.ejemplo.crudpersona.repository.CajaTurnoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class RPLCCajaService implements RPLCCajaServiceI {
    private final CajaRepository cajas;
    private final CajaTurnoRepository turnos;
    public RPLCCajaService(CajaRepository cajas, CajaTurnoRepository turnos) { this.cajas = cajas; this.turnos = turnos; }

    @Transactional
    public CajaTurno abrir(int numero, BigDecimal apertura, Long usuario) {
        cajas.bloquear(numero).orElseThrow(() -> new IllegalArgumentException("Caja inexistente"));
        if (apertura == null || apertura.signum() < 0) throw new IllegalArgumentException("Monto de apertura invalido");
        if (turnos.findByNumeroCajaAndEstado(numero, "ABIERTA").isPresent()) throw new IllegalStateException("Esta caja ya tiene un turno abierto.");
        return turnos.save(CajaTurno.builder().numeroCaja(numero).fechaApertura(LocalDateTime.now())
                .montoApertura(apertura).montoEfectivoVentas(BigDecimal.ZERO).montoYapePlin(BigDecimal.ZERO)
                .montoTarjeta(BigDecimal.ZERO).montoMixto(BigDecimal.ZERO).usuarioId(usuario).estado("ABIERTA").build());
    }

    @Transactional
    public CajaTurno cerrar(int numero, Long turnoId, BigDecimal contado) {
        cajas.bloquear(numero).orElseThrow();
        CajaTurno turno = turnos.findByNumeroCajaAndEstado(numero, "ABIERTA")
                .filter(t -> t.getId().equals(turnoId)).orElseThrow(() -> new IllegalStateException("El turno ya cambio. Actualiza la caja."));
        if (contado == null || contado.signum() < 0) throw new IllegalArgumentException("Efectivo contado invalido");
        BigDecimal esperado = turno.getMontoApertura().add(turno.getMontoEfectivoVentas());
        turno.setFechaCierre(LocalDateTime.now());
        turno.setMontoContado(contado); turno.setMontoEsperado(esperado);
        turno.setDiferencia(contado.subtract(esperado)); turno.setEstado("CERRADA");
        return turnos.save(turno);
    }
}
