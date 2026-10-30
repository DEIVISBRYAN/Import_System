package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.model.*;
import com.ejemplo.crudpersona.repository.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
public class ERQZStockService implements ERQZStockServiceI {
    private final ProductoRepository productos;
    private final LoteRepository lotes;
    private final UbicacionAlmacenRepository ubicaciones;
    private final JdbcTemplate jdbc;
    private final RPLCTerminalSesion sesion;
    public ERQZStockService(ProductoRepository productos, LoteRepository lotes, UbicacionAlmacenRepository ubicaciones,
                        JdbcTemplate jdbc, RPLCTerminalSesion sesion) {
        this.productos = productos; this.lotes = lotes; this.ubicaciones = ubicaciones; this.jdbc = jdbc; this.sesion = sesion;
    }
    private void noNegativo(BigDecimal valor, String campo) {
        if (valor == null || valor.signum() < 0) throw new IllegalArgumentException(campo + " debe ser mayor o igual a cero.");
    }
    private Producto bloquear(Long id) { return productos.bloquear(id).orElseThrow(() -> new IllegalArgumentException("Producto no encontrado.")); }
    @Transactional public Producto guardarProducto(Producto datos, String codigoLote, String ubicacion, int inicial, BigDecimal costoUsd) {
        if (datos.getSku() == null || datos.getSku().isBlank() || datos.getDescripcion() == null || datos.getDescripcion().isBlank())
            throw new IllegalArgumentException("Ingresa nombre del producto y SKU.");
        if (inicial < 0 || datos.getStockMinimo() == null || datos.getStockMinimo() < 0)
            throw new IllegalArgumentException("El stock y el mínimo no pueden ser negativos.");
        noNegativo(datos.getPrecioVentaPen(), "El precio"); noNegativo(datos.getCostoPromedioPen(), "El costo"); noNegativo(costoUsd, "El costo USD");
        Producto producto = datos.getId() == null ? new Producto() : bloquear(datos.getId());
        String sku = datos.getSku().trim();
        boolean duplicado = productos.findBySkuIgnoreCase(sku).filter(p -> !p.getId().equals(datos.getId())).isPresent();
        if (duplicado) throw new IllegalArgumentException("Ya existe un producto con ese SKU.");
        String barras = datos.getCodigoBarras() == null || datos.getCodigoBarras().isBlank() ? null : datos.getCodigoBarras().trim();
        if (barras != null && productos.findByCodigoBarras(barras).filter(p -> !p.getId().equals(datos.getId())).isPresent())
            throw new IllegalArgumentException("Ya existe un producto con ese código de barras.");
        producto.setSku(sku); producto.setDescripcion(datos.getDescripcion().trim()); producto.setCodigoBarras(barras);
        producto.setStockMinimo(datos.getStockMinimo()); producto.setPrecioVentaPen(datos.getPrecioVentaPen());
        producto.setCostoPromedioPen(datos.getCostoPromedioPen());
        boolean nuevo = producto.getId() == null;
        if (nuevo) producto.setStockActual(inicial);
        producto = productos.saveAndFlush(producto);
        if (nuevo) {
            String codigo = codigoLote == null || codigoLote.isBlank() ? "L-" + producto.getId() : codigoLote.trim();
            Lote lote = lotes.save(Lote.builder().producto(producto).codigoLote(codigo).ubicacion(guardarUbicacion(ubicacion))
                    .cantidadInicial(inicial).cantidadDisponible(inicial).costoUnitarioPen(datos.getCostoPromedioPen())
                    .costoUnitarioUsd(costoUsd).fechaIngreso(LocalDate.now()).build());
            movimiento(producto, lote, inicial, "Stock inicial");
        } else {
            lotes.findByProductoIdOrderByFechaIngresoAsc(producto.getId()).stream().findFirst()
                    .ifPresent(l -> l.setUbicacion(guardarUbicacion(ubicacion)));
        }
        return producto;
    }
    @Transactional public void agregarLote(Long productoId, Lote datos) {
        Producto producto = bloquear(productoId);
        if (datos.getCodigoLote() == null || datos.getCodigoLote().isBlank() || datos.getCantidadDisponible() == null || datos.getCantidadDisponible() <= 0)
            throw new IllegalArgumentException("Ingresa código de lote y cantidad mayor que cero.");
        if (lotes.findByProductoIdOrderByFechaIngresoAsc(productoId).stream().anyMatch(l -> l.getCodigoLote().equalsIgnoreCase(datos.getCodigoLote().trim())))
            throw new IllegalArgumentException("Este producto ya tiene ese código de lote.");
        noNegativo(datos.getCostoUnitarioPen(), "El costo PEN"); noNegativo(datos.getCostoUnitarioUsd(), "El costo USD");
        int anterior = producto.getStockActual(), cantidad = datos.getCantidadDisponible(), total = Math.addExact(anterior, cantidad);
        BigDecimal costo = producto.getCostoPromedioPen() == null ? BigDecimal.ZERO : producto.getCostoPromedioPen();
        producto.setCostoPromedioPen(costo.multiply(BigDecimal.valueOf(anterior))
                .add(datos.getCostoUnitarioPen().multiply(BigDecimal.valueOf(cantidad))).divide(BigDecimal.valueOf(total), 4, RoundingMode.HALF_UP));
        datos.setId(null); datos.setProducto(producto); datos.setCodigoLote(datos.getCodigoLote().trim());
        datos.setUbicacion(guardarUbicacion(datos.getUbicacion())); datos.setCantidadInicial(cantidad);
        if (datos.getFechaIngreso() == null) datos.setFechaIngreso(LocalDate.now());
        Lote lote = lotes.save(datos); producto.setStockActual(total);
        movimiento(producto, lote, cantidad, "Ingreso de lote");
    }
    @Transactional public int ajustar(Long productoId, Long lotePreferido, int cantidad, String motivo) {
        Producto producto = bloquear(productoId);
        if (cantidad == 0 || motivo == null || motivo.isBlank()) throw new IllegalArgumentException("Ingresa una cantidad diferente de cero y un motivo.");
        int nuevo = Math.addExact(producto.getStockActual(), cantidad);
        if (nuevo < 0) throw new IllegalArgumentException("No puedes dejar el stock por debajo de cero.");
        var lista = new ArrayList<>(lotes.findByProductoIdOrderByFechaIngresoAsc(productoId));
        lista.sort(Comparator.comparing(l -> !Objects.equals(l.getId(), lotePreferido)));
        if (cantidad > 0) {
            Lote lote = lista.stream().findFirst().orElseGet(() -> lotes.save(Lote.builder().producto(producto)
                    .codigoLote("AJ-" + UUID.randomUUID().toString().substring(0, 8)).cantidadInicial(0).cantidadDisponible(0)
                    .costoUnitarioPen(producto.getCostoPromedioPen()).costoUnitarioUsd(BigDecimal.ZERO)
                    .fechaIngreso(LocalDate.now()).build()));
            lote.setCantidadDisponible(Math.addExact(lote.getCantidadDisponible(), cantidad));
            lote.setCantidadInicial(Math.addExact(lote.getCantidadInicial(), cantidad));
            movimiento(producto, lote, cantidad, motivo);
        } else {
            long pendiente = -(long) cantidad;
            for (Lote lote : lista) {
                int retiro = (int) Math.min(lote.getCantidadDisponible(), pendiente);
                if (retiro > 0) { lote.setCantidadDisponible(lote.getCantidadDisponible() - retiro); movimiento(producto, lote, -retiro, motivo); }
                pendiente -= retiro;
                if (pendiente == 0) break;
            }
            if (pendiente != 0) throw new IllegalArgumentException("Los lotes no alcanzan. Revisa las cantidades antes de ajustar.");
        }
        producto.setStockActual(nuevo); return nuevo;
    }
    private Lote loteBloqueado(Long loteId) {
        Long productoId = jdbc.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT producto_id FROM lotes WHERE id=?"), Long.class, loteId);
        bloquear(productoId);
        return lotes.findById(loteId).orElseThrow(() -> new IllegalArgumentException("Lote no encontrado."));
    }
    @Transactional public void editarLote(Long id, int esperado, int cantidad, String ubicacion) {
        Lote lote = loteBloqueado(id);
        if (lote.getCantidadDisponible() != esperado) throw new IllegalStateException("El stock cambió desde otra caja. Actualiza la vista antes de guardar.");
        if (cantidad < 0 || cantidad > lote.getCantidadInicial()) throw new IllegalArgumentException("Cantidad fuera del rango inicial del lote.");
        int diferencia = cantidad - lote.getCantidadDisponible();
        Producto producto = lote.getProducto();
        producto.setStockActual(Math.addExact(producto.getStockActual(), diferencia));
        lote.setCantidadDisponible(cantidad); lote.setUbicacion(guardarUbicacion(ubicacion));
        movimiento(producto, lote, diferencia, "Conteo de ubicación");
    }
    @Transactional public void moverLote(Long id, String ubicacion) {
        loteBloqueado(id).setUbicacion(guardarUbicacion(ubicacion));
    }
    @Transactional public void vaciar(Map<Long, Integer> esperados) {
        var ids = esperados.keySet().stream().map(id -> jdbc.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT producto_id FROM lotes WHERE id=?"), Long.class, id)).distinct().sorted().toList();
        ids.forEach(this::bloquear);
        for (var entrada : esperados.entrySet()) {
            Lote lote = lotes.findById(entrada.getKey()).orElseThrow();
            if (!lote.getCantidadDisponible().equals(entrada.getValue())) throw new IllegalStateException("El stock cambió. Actualiza la ubicación antes de vaciar.");
            Producto producto = lote.getProducto(); int cantidad = lote.getCantidadDisponible();
            lote.setCantidadDisponible(0); producto.setStockActual(producto.getStockActual() - cantidad);
            movimiento(producto, lote, -cantidad, "Vaciado de ubicación");
        }
    }
    private String guardarUbicacion(String codigo) {
        if (codigo == null || codigo.isBlank()) return null;
        String normal = codigo.trim().toUpperCase(Locale.ROOT);
        if (!normal.matches("[A-Z]{1,4}-\\d{1,2}-[1-9]\\d?")) throw new IllegalArgumentException("Usa una ubicación válida, por ejemplo A-01-1.");
        String[] partes = normal.split("-"); int anaquel = Integer.parseInt(partes[1]), nivel = Integer.parseInt(partes[2]);
        if (anaquel <= 0) throw new IllegalArgumentException("El anaquel debe ser mayor que cero.");
        normal = partes[0] + "-" + String.format(Locale.ROOT, "%02d", anaquel) + "-" + nivel;
        var ubicacion = ubicaciones.findByCodigo(normal).orElseGet(UbicacionAlmacen::new);
        ubicacion.setCodigo(normal); ubicacion.setPasillo(partes[0]); ubicacion.setAnaquel(anaquel); ubicacion.setNivel(nivel); ubicacion.setActivo(true);
        ubicaciones.save(ubicacion); return normal;
    }
    private void movimiento(Producto producto, Lote lote, int cantidad, String motivo) {
        if (cantidad == 0) return;
        jdbc.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "INSERT INTO movimientos_stock(producto_id,lote_id,cantidad,motivo,usuario_id) VALUES (?,?,?,?,?)"),
                producto.getId(), lote.getId(), cantidad, motivo.trim(), sesion.usuarioId());
    }
}
