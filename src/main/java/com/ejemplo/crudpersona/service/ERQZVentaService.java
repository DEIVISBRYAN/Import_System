package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.model.*;
import com.ejemplo.crudpersona.repository.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
public class ERQZVentaService implements ERQZVentaServiceI {
    private final CajaRepository cajas;
    private final CajaTurnoRepository turnos;
    private final ProductoRepository productos;
    private final LoteRepository lotes;
    private final VentaRepository ventas;
    private final DetalleVentaRepository detalles;
    private final RPLCInventarioFifoServiceI fifo;
    private final RPLCComprobantesServiceI comprobantes;
    private final ComprobanteSunatRepository documentos;
    private final JdbcTemplate jdbc;
    public ERQZVentaService(CajaRepository cajas, CajaTurnoRepository turnos, ProductoRepository productos,
                        LoteRepository lotes, VentaRepository ventas, DetalleVentaRepository detalles,
                        RPLCInventarioFifoServiceI fifo, RPLCComprobantesServiceI comprobantes,
                        ComprobanteSunatRepository documentos, JdbcTemplate jdbc) {
        this.cajas = cajas; this.turnos = turnos; this.productos = productos; this.lotes = lotes;
        this.ventas = ventas; this.detalles = detalles; this.fifo = fifo;
        this.comprobantes = comprobantes; this.documentos = documentos; this.jdbc = jdbc;
    }

    @Transactional
    public Long cobrar(int caja, Long usuario, String tipo, String documento, String cliente, String pago, List<Linea> lineas) {
        if (lineas == null || lineas.isEmpty()) throw new IllegalArgumentException("Agrega productos a la venta.");
        if (!Set.of("BOLETA", "FACTURA").contains(tipo) || !Set.of("EFECTIVO", "YAPE_PLIN", "TARJETA", "MIXTO").contains(pago))
            throw new IllegalArgumentException("Comprobante o pago invalido.");
        if (documento == null || (tipo.equals("FACTURA") ? !documento.matches("\\d{11}") : !documento.isBlank() && !documento.matches("\\d{8}")))
            throw new IllegalArgumentException("Revisa el DNI o RUC del cliente.");
        cajas.bloquear(caja).orElseThrow();
        CajaTurno turno = turnos.findByNumeroCajaAndEstado(caja, "ABIERTA")
                .orElseThrow(() -> new IllegalStateException("La caja " + caja + " esta cerrada."));
        // Orden estable de bloqueos evita vender las mismas unidades desde dos terminales.
        lineas.stream().map(Linea::productoId).distinct().sorted().forEach(id -> productos.bloquear(id).orElseThrow());
        BigDecimal total = BigDecimal.ZERO;
        for (Linea l : lineas) {
            if (l.cantidad() <= 0 || l.precio() == null || l.precio().signum() < 0) throw new IllegalArgumentException("Cantidad o precio invalido.");
            total = total.add(l.precio().multiply(BigDecimal.valueOf(l.cantidad())));
        }
        total = total.setScale(2, RoundingMode.HALF_UP);
        BigDecimal base = total.divide(new BigDecimal("1.18"), 2, RoundingMode.HALF_UP);
        String serie = tipo.equals("FACTURA") ? "F001" : "B001";
        Integer numero = jdbc.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "UPDATE secuencias_comprobante SET siguiente = siguiente + 1 WHERE serie = ? RETURNING siguiente - 1"), Integer.class, serie);
        Venta venta = Venta.builder().fechaEmision(LocalDateTime.now()).tipoComprobante(tipo).serie(serie).correlativo(numero)
                .clienteDocumento(documento).clienteNombre(cliente == null || cliente.isBlank() ? "Cliente mostrador" : cliente)
                .subtotal(base).igv(total.subtract(base)).total(total).metodoPago(pago).cajaTurnoId(turno.getId())
                .usuarioId(usuario).estado("COMPLETADA").build();
        for (Linea l : lineas) {
            Producto producto = productos.findById(l.productoId()).orElseThrow();
            for (var despacho : fifo.despacharFifoConDetalle(l.productoId(), l.cantidad())) {
                venta.getDetalles().add(DetalleVenta.builder().venta(venta).producto(producto).loteId(despacho.loteId())
                        .cantidad(despacho.cantidad()).precioUnitario(l.precio())
                        .subtotal(l.precio().multiply(BigDecimal.valueOf(despacho.cantidad()))).build());
            }
        }
        ventas.saveAndFlush(venta);
        actualizarTurno(turno, pago, total);
        turnos.save(turno);
        comprobantes.registrarVenta(venta.getId());
        return venta.getId();
    }

    @Transactional
    public void anular(Long ventaId) {
        Integer caja = jdbc.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT t.numero_caja FROM caja_turno t JOIN ventas v ON v.caja_turno_id=t.id WHERE v.id=?"), Integer.class, ventaId);
        cajas.bloquear(caja).orElseThrow();
        Venta venta = ventas.bloquear(ventaId).orElseThrow();
        if ("ANULADA".equals(venta.getEstado())) return;
        var documento = documentos.findByVentaId(ventaId)
                .or(() -> documentos.findBySerieAndCorrelativo(venta.getSerie(), venta.getCorrelativo()));
        if (documento.isPresent() && "ACEPTADO".equals(documento.get().getEstadoSunat()))
            throw new IllegalStateException("Un comprobante aceptado requiere nota de credito; no se puede anular localmente.");
        CajaTurno turno = turnos.findById(venta.getCajaTurnoId()).orElseThrow();
        if (!"ABIERTA".equals(turno.getEstado())) throw new IllegalStateException("El turno original esta cerrado. La devolucion requiere un registro de ajuste independiente.");
        jdbc.queryForList(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT DISTINCT producto_id FROM detalle_venta WHERE venta_id=? ORDER BY producto_id"), Long.class, ventaId)
                .forEach(id -> productos.bloquear(id).orElseThrow());
        List<DetalleVenta> items = detalles.findByVentaId(ventaId);
        for (DetalleVenta d : items) {
            Producto p = productos.findById(d.getProducto().getId()).orElseThrow();
            p.setStockActual(p.getStockActual() + d.getCantidad());
            if (d.getLoteId() != null) {
                Lote lote = lotes.findById(d.getLoteId()).orElseThrow();
                lote.setCantidadDisponible(lote.getCantidadDisponible() + d.getCantidad());
            }
        }
        venta.setEstado("ANULADA");
        actualizarTurno(turno, venta.getMetodoPago(), venta.getTotal().negate());
        comprobantes.registrarVenta(ventaId);
    }
    private void actualizarTurno(CajaTurno t, String pago, BigDecimal total) {
        switch (pago) {
            case "EFECTIVO" -> t.setMontoEfectivoVentas(cero(t.getMontoEfectivoVentas()).add(total));
            case "YAPE_PLIN" -> t.setMontoYapePlin(cero(t.getMontoYapePlin()).add(total));
            case "TARJETA" -> t.setMontoTarjeta(cero(t.getMontoTarjeta()).add(total));
            default -> t.setMontoMixto(cero(t.getMontoMixto()).add(total));
        }
    }
    private BigDecimal cero(BigDecimal v) { return v == null ? BigDecimal.ZERO : v; }
}
