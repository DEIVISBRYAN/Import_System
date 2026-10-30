package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.model.*;
import com.ejemplo.crudpersona.repository.ProductoRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@Service
public class ERQZCompraService implements ERQZCompraServiceI {
    private final ProductoRepository productos;
    private final ERQZStockServiceI stock;
    private final JdbcTemplate jdbc;
    private final RPLCTerminalSesion sesion;
    private final DBQPDocumentosDuaServiceI documentos;
    public ERQZCompraService(ProductoRepository productos, ERQZStockServiceI stock, JdbcTemplate jdbc, RPLCTerminalSesion sesion, DBQPDocumentosDuaServiceI documentos) {
        this.productos = productos; this.stock = stock; this.jdbc = jdbc; this.sesion = sesion; this.documentos = documentos;
    }
    @Transactional public Long registrar(Long proveedorId, String dua, LocalDate fecha, BigDecimal tipoCambio, List<Linea> lineas) {
        return registrarConDocumentos(proveedorId, dua, fecha, tipoCambio, lineas, null, List.of());
    }
    @Transactional public Long registrarConDocumentos(Long proveedorId, String dua, LocalDate fecha, BigDecimal tipoCambio, List<Linea> lineas,
                                                     Long documentoId, List<ERQZArchivoDua> archivos) {
        if (proveedorId == null || dua == null || dua.isBlank() || fecha == null || tipoCambio == null || tipoCambio.signum() <= 0)
            throw new IllegalArgumentException("Completa proveedor, DUA, fecha y tipo de cambio.");
        if (lineas == null || lineas.isEmpty()) throw new IllegalArgumentException("Agrega productos a la compra.");
        jdbc.execute(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT pg_advisory_xact_lock(74021, 2)"));
        // NO KEY UPDATE protects supplier data without blocking foreign-key references from new orders.
        var proveedores = jdbc.queryForList(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT razon_social,ruc FROM proveedores WHERE id=? AND estado='ACTIVO' FOR NO KEY UPDATE"), proveedorId);
        if (proveedores.isEmpty()) throw new IllegalArgumentException("El proveedor ya no está activo. Actualiza la compra.");
        if (jdbc.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT count(*) FROM compras_dua WHERE proveedor_id=? AND numero_dua=?"), Long.class, proveedorId, dua.trim()) > 0)
            throw new IllegalArgumentException("Esta DUA ya está registrada para el proveedor.");
        BigDecimal fob = BigDecimal.ZERO, gastos = BigDecimal.ZERO;
        for (Linea l : lineas) {
            if (l.sku() == null || l.sku().isBlank() || l.descripcion() == null || l.descripcion().isBlank() || l.lote() == null || l.lote().isBlank() || l.cantidad() <= 0)
                throw new IllegalArgumentException("Revisa SKU, nombre, lote y cantidad de cada línea.");
            for (BigDecimal valor : new BigDecimal[]{l.fobUsd(), l.gastosUsd(), l.margen()})
                if (valor == null || valor.signum() < 0) throw new IllegalArgumentException("Ingresa costos y margen válidos, mayores o iguales a cero.");
            fob = fob.add(l.fobUsd().multiply(BigDecimal.valueOf(l.cantidad())));
            gastos = gastos.add(l.gastosUsd().multiply(BigDecimal.valueOf(l.cantidad())));
        }
        var proveedor = proveedores.get(0);
        Long id = jdbc.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, """
                INSERT INTO compras_dua(proveedor_id,proveedor,ruc,numero_dua,fecha_ingreso,tipo_cambio,
                    total_fob_usd,total_gastos_usd,total_landed_usd,total_landed_pen)
                VALUES (?,?,?,?,?,?,?,?,?,?) RETURNING id
                """), Long.class, proveedorId, proveedor.get("razon_social"), proveedor.get("ruc"), dua.trim(), fecha.toString(), tipoCambio,
                fob, gastos, fob.add(gastos), fob.add(gastos).multiply(tipoCambio));
        var ids = lineas.stream().map(l -> productos.idPorSku(l.sku().trim()).orElse(null))
                .filter(Objects::nonNull).distinct().sorted().toList();
        ids.forEach(productos::bloquear);
        for (Linea l : lineas) {
            BigDecimal usd = l.fobUsd().add(l.gastosUsd()).setScale(4, RoundingMode.HALF_UP);
            BigDecimal pen = usd.multiply(tipoCambio).setScale(4, RoundingMode.HALF_UP);
            BigDecimal precio = pen.multiply(BigDecimal.ONE.add(l.margen().divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP)))
                    .setScale(2, RoundingMode.HALF_UP);
            Producto producto = productos.findBySkuIgnoreCase(l.sku().trim()).orElse(null);
            if (producto == null) {
                producto = stock.guardarProducto(Producto.builder().sku(l.sku().trim()).descripcion(l.descripcion())
                        .codigoBarras(l.codigoBarras()).stockMinimo(5).precioVentaPen(precio).costoPromedioPen(pen).build(),
                        l.lote(), l.ubicacion(), l.cantidad(), usd);
                jdbc.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "UPDATE lotes SET numero_dua=?,fecha_ingreso=? WHERE producto_id=?"), dua.trim(), fecha.toString(), producto.getId());
            } else {
                if (l.codigoBarras() != null && !l.codigoBarras().isBlank()) {
                    var otro = productos.findByCodigoBarras(l.codigoBarras().trim());
                    if (otro.isPresent() && !otro.get().getId().equals(producto.getId())) throw new IllegalArgumentException("Código de barras duplicado.");
                    producto.setCodigoBarras(l.codigoBarras().trim());
                }
                producto.setDescripcion(l.descripcion().trim()); producto.setPrecioVentaPen(precio);
                stock.agregarLote(producto.getId(), Lote.builder().codigoLote(l.lote()).numeroDua(dua.trim()).ubicacion(l.ubicacion())
                        .cantidadDisponible(l.cantidad()).costoUnitarioUsd(usd).costoUnitarioPen(pen).fechaIngreso(fecha).build());
            }
            Long loteId = jdbc.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT id FROM lotes WHERE producto_id=? AND codigo_lote=? ORDER BY id DESC LIMIT 1"), Long.class, producto.getId(), l.lote().trim());
            jdbc.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, """
                    INSERT INTO detalle_compra_dua(compra_id,producto_id,lote_id,sku,descripcion,codigo_barras,codigo_lote,
                        ubicacion,cantidad,costo_fob_usd,gastos_dua_usd,costo_landed_usd,costo_landed_pen,margen,precio_sugerido_pen)
                    VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    """), id, producto.getId(), loteId, l.sku(), l.descripcion(), l.codigoBarras(), l.lote(), l.ubicacion(), l.cantidad(),
                    l.fobUsd(), l.gastosUsd(), usd, pen, l.margen(), precio);
        }
        documentos.guardarConCompra(documentoId, id, archivos);
        return id;
    }

    @Transactional(readOnly=true) public Recepcion prepararRecepcion(Long pedidoId) {
        var cabecera = cabeceraPedido(pedidoId, false);
        var lineas = jdbc.query(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, """
                SELECT d.id,p.sku,p.descripcion,d.cantidad,COALESCE(d.unitario_usd,0) estimado
                FROM pedidos_reposicion d JOIN productos p ON p.id=d.producto_id
                WHERE d.pedido_compra_id=? ORDER BY d.id
                """), (rs,n) -> new ProductoRecepcion(rs.getLong("id"),rs.getString("sku"),rs.getString("descripcion"),
                rs.getInt("cantidad"),rs.getBigDecimal("estimado")),pedidoId);
        if (lineas.isEmpty() || lineas.stream().anyMatch(l -> l.cantidad() <= 0))
            throw new IllegalArgumentException("El pedido no tiene cantidades válidas para recibir.");
        return new Recepcion(pedidoId,String.format(Locale.ROOT,"PC-%06d",pedidoId),cabecera.nombre(),lineas);
    }

    @Transactional public Long recibirPedido(Long pedidoId, String dua, LocalDate fecha, BigDecimal tipoCambio, List<LineaRecepcion> lineas) {
        jdbc.execute(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT pg_advisory_xact_lock(74021, 2)"));
        var cabecera = cabeceraPedido(pedidoId, true);
        var esperadas = jdbc.query(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT id,producto_id,cantidad FROM pedidos_reposicion WHERE pedido_compra_id=? ORDER BY id"),
                (rs,n) -> new DetallePedido(rs.getLong("id"),rs.getLong("producto_id"),rs.getInt("cantidad")),pedidoId);
        Map<Long,LineaRecepcion> recibidas = new HashMap<>();
        if (lineas == null || lineas.isEmpty()) throw new IllegalArgumentException("Recibe todos los productos del pedido.");
        for (var l : lineas) {
            if (l == null || l.detalleId() == null || recibidas.put(l.detalleId(),l) != null)
                throw new IllegalArgumentException("Hay líneas repetidas o inválidas en la recepción.");
        }
        if (esperadas.isEmpty() || esperadas.size() != recibidas.size())
            throw new IllegalArgumentException("Solo se admiten pedidos completos: incluye todos sus productos.");
        for (var d : esperadas) {
            var l = recibidas.get(d.id());
            if (l == null || l.cantidad() != d.cantidad() || d.cantidad() <= 0)
                throw new IllegalArgumentException("Solo se admiten pedidos completos con las cantidades originales.");
            if (l.ubicacion() == null || l.ubicacion().isBlank()) throw new IllegalArgumentException("Indica una ubicación para cada producto.");
        }
        Map<Long,Producto> bloqueados = new HashMap<>();
        esperadas.stream().map(DetallePedido::productoId).distinct().sorted().forEach(id ->
                bloqueados.put(id,productos.bloquear(id).orElseThrow(() -> new IllegalArgumentException("Producto no encontrado."))));
        List<Linea> compra = esperadas.stream().map(d -> {
            var p=bloqueados.get(d.productoId()); var l=recibidas.get(d.id());
            return new Linea(p.getSku(),p.getDescripcion(),p.getCodigoBarras(),l.lote(),l.ubicacion().trim().toUpperCase(Locale.ROOT),
                    l.cantidad(),l.fobUsd(),l.gastosUsd(),l.margen());
        }).toList();
        Long compraId = registrar(cabecera.proveedorId(),dua,fecha,tipoCambio,compra);
        jdbc.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, """
                UPDATE pedidos_compra SET compra_id=?,estado='RECIBIDO',recibido_en=localtimestamp,recibido_por=?,
                  pdf_documento=NULL,excel_documento=NULL WHERE id=?
                """),compraId,sesion.usuarioId(),pedidoId);
        jdbc.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "UPDATE pedidos_reposicion SET estado='RECIBIDO' WHERE pedido_compra_id=?"),pedidoId);
        return compraId;
    }
    private CabeceraPedido cabeceraPedido(Long id, boolean bloquear) {
        var rows = jdbc.query(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT proveedor_id,proveedor_nombre,estado,compra_id FROM pedidos_compra WHERE id=?"+(bloquear?" FOR UPDATE":"")),
                (rs,n)->new CabeceraPedido(DBQPSqlDialect.nullableLong(rs,"proveedor_id"),rs.getString("proveedor_nombre"),rs.getString("estado"),DBQPSqlDialect.nullableLong(rs,"compra_id")),id);
        if (rows.isEmpty()) throw new IllegalArgumentException("Pedido no encontrado.");
        var p=rows.get(0);
        if (!"PENDIENTE".equals(p.estado()) || p.compraId()!=null) throw new IllegalArgumentException("Este pedido ya fue recibido o no está pendiente.");
        if (p.proveedorId()==null || jdbc.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT count(*) FROM proveedores WHERE id=? AND estado='ACTIVO'"),Long.class,p.proveedorId())==0)
            throw new IllegalArgumentException("El pedido no tiene un proveedor activo vinculado. Revisa el proveedor antes de recibirlo.");
        return p;
    }
    private record CabeceraPedido(Long proveedorId,String nombre,String estado,Long compraId) {}
    private record DetallePedido(Long id,Long productoId,int cantidad) {}
}
