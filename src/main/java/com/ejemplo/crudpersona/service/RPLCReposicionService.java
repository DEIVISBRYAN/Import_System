package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.repository.ProductoRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.math.RoundingMode;
import com.ejemplo.crudpersona.model.PedidoCompraDocumento;
import java.util.*;

@Service
public class RPLCReposicionService implements RPLCReposicionServiceI {
    private final ProductoRepository productos;
    private final JdbcTemplate jdbc;
    public RPLCReposicionService(ProductoRepository productos, JdbcTemplate jdbc) { this.productos = productos; this.jdbc = jdbc; }
    public String proveedorHabitual(Long id) {
        var encontrados = jdbc.queryForList(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, """
                SELECT p.razon_social FROM proveedores p JOIN compras_dua c ON c.proveedor_id=p.id
                JOIN detalle_compra_dua d ON d.compra_id=c.id
                WHERE d.producto_id=? AND p.estado='ACTIVO' ORDER BY c.id DESC LIMIT 1
                """), String.class, id);
        return encontrados.isEmpty() ? "" : encontrados.get(0);
    }
    public List<Proveedor> proveedoresActivos() {
        return jdbc.query(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT id,razon_social,COALESCE(ruc,'') ruc,COALESCE(contacto,'') contacto FROM proveedores WHERE estado='ACTIVO' ORDER BY razon_social,id"),
                (rs, n) -> new Proveedor(rs.getLong("id"), rs.getString("razon_social"), rs.getString("ruc"), rs.getString("contacto")));
    }
    public Proveedor proveedorAsignado(Long productoId) {
        var resultado = jdbc.query(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, """
                SELECT p.id,p.razon_social,COALESCE(p.ruc,'') ruc,COALESCE(p.contacto,'') contacto
                FROM proveedores p WHERE p.estado='ACTIVO' AND p.id = CASE
                  WHEN EXISTS(SELECT 1 FROM reposicion_config WHERE producto_id=?)
                  THEN (SELECT proveedor_id FROM reposicion_config WHERE producto_id=?)
                  ELSE (SELECT c.proveedor_id FROM compras_dua c JOIN detalle_compra_dua d ON d.compra_id=c.id
                        JOIN proveedores habitual ON habitual.id=c.proveedor_id
                        WHERE d.producto_id=? AND habitual.estado='ACTIVO' ORDER BY c.id DESC LIMIT 1) END
                """), (rs, n) -> new Proveedor(rs.getLong("id"), rs.getString("razon_social"), rs.getString("ruc"), rs.getString("contacto")), productoId, productoId, productoId);
        return resultado.isEmpty() ? null : resultado.get(0);
    }
    private String validarProveedor(Long id) {
        if (id == null) return "";
        var nombres = jdbc.queryForList(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT razon_social FROM proveedores WHERE id=? AND estado='ACTIVO'"), String.class, id);
        if (nombres.isEmpty()) throw new IllegalArgumentException("El proveedor ya no está activo. Selecciona otro proveedor.");
        return nombres.get(0);
    }
    @Transactional public void asignarProveedor(Long productoId, Long proveedorId) {
        productos.bloquear(productoId).orElseThrow(() -> new IllegalArgumentException("Producto no encontrado."));
        if (proveedorId == null) throw new IllegalArgumentException("Selecciona un proveedor activo.");
        String nombre = validarProveedor(proveedorId);
        jdbc.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, """
                INSERT INTO reposicion_config(producto_id,ventas_dia,dias_entrega,stock_seguridad,proveedor,proveedor_id)
                VALUES (?,NULL,30,10,?,?)
                ON CONFLICT(producto_id) DO UPDATE SET proveedor=excluded.proveedor,proveedor_id=excluded.proveedor_id
                """), productoId, nombre, proveedorId);
    }
    @Transactional public void guardarParametros(Long productoId, java.math.BigDecimal ventasDia, int diasEntrega, int seguridad, Long proveedorId) {
        productos.bloquear(productoId).orElseThrow(() -> new IllegalArgumentException("Producto no encontrado."));
        if (ventasDia == null || ventasDia.signum() < 0 || diasEntrega < 0 || seguridad < 0)
            throw new IllegalArgumentException("Las ventas, los días y el stock de seguridad no pueden ser negativos.");
        String nombre = validarProveedor(proveedorId);
        jdbc.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, """
                INSERT INTO reposicion_config(producto_id,ventas_dia,dias_entrega,stock_seguridad,proveedor,proveedor_id)
                VALUES (?,?,?,?,?,?) ON CONFLICT(producto_id) DO UPDATE SET ventas_dia=excluded.ventas_dia,
                    dias_entrega=excluded.dias_entrega,stock_seguridad=excluded.stock_seguridad,
                    proveedor=excluded.proveedor,proveedor_id=excluded.proveedor_id
                """), productoId, ventasDia, diasEntrega, seguridad, nombre, proveedorId);
    }
    public long urgentes() {
        return jdbc.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, """
                SELECT count(*) FROM productos p LEFT JOIN reposicion_config c ON c.producto_id=p.id
                LEFT JOIN (SELECT d.producto_id,round(SUM(d.cantidad)/30.0,2) ventas FROM detalle_venta d JOIN ventas v ON v.id=d.venta_id
                           WHERE v.estado='COMPLETADA' AND v.fecha_emision>=? GROUP BY d.producto_id) v ON v.producto_id=p.id
                WHERE p.stock_actual<=ceil((CASE WHEN c.ventas_dia>0 THEN c.ventas_dia ELSE COALESCE(v.ventas,0) END)
                                          *COALESCE(c.dias_entrega,30)+COALESCE(c.stock_seguridad,10))
                  AND p.stock_actual<ceil((CASE WHEN c.ventas_dia>0 THEN c.ventas_dia ELSE COALESCE(v.ventas,0) END)
                                         *(COALESCE(c.dias_entrega,30)+30)+COALESCE(c.stock_seguridad,10))
                """), Long.class, LocalDateTime.now().minusDays(30).toString().replace('T', ' '));
    }
    @Transactional public int generar(List<Pedido> pedidos) {
        var ordenados = pedidos.stream().sorted(Comparator.comparing(Pedido::productoId)).toList();
        ordenados.stream().map(Pedido::productoId).distinct().forEach(id -> productos.bloquear(id).orElseThrow());
        List<LineaRevisada> revisadas = new ArrayList<>();
        for (Pedido p : ordenados) {
            if (p.cantidad() <= 0 || p.proveedor() == null || p.proveedor().isBlank())
                throw new IllegalArgumentException("Configura proveedor y cantidad antes de generar el pedido.");
            Proveedor asignado = proveedorAsignado(p.productoId());
            if (asignado == null) throw new IllegalArgumentException("Asigna un proveedor activo al producto antes de generar el pedido.");
            if (jdbc.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT count(*) FROM pedidos_reposicion WHERE producto_id=? AND estado='PENDIENTE'"), Long.class, p.productoId()) > 0) continue;
            revisadas.add(new LineaRevisada(p.productoId(), asignado.id(), p.cantidad(),
                    p.costoUsd().divide(BigDecimal.valueOf(p.cantidad()),6,RoundingMode.HALF_UP),
                    p.costoPen().divide(BigDecimal.valueOf(p.cantidad()),6,RoundingMode.HALF_UP)));
        }
        if (!revisadas.isEmpty()) confirmarPedidos(revisadas, "");
        return revisadas.size();
    }

    @Transactional public List<Long> confirmarPedidos(List<LineaRevisada> lineas, String observaciones) {
        if (lineas == null || lineas.isEmpty()) throw new IllegalArgumentException("Agrega al menos un producto al pedido.");
        Set<Long> ids = new HashSet<>();
        for (var l : lineas) {
            if (l == null || l.productoId() == null || l.proveedorId() == null || !ids.add(l.productoId()))
                throw new IllegalArgumentException("Hay productos repetidos o sin proveedor.");
            if (l.cantidad() < 1 || l.cantidad() > 1_000_000 || l.unitarioUsd() == null || l.unitarioPen() == null
                    || l.unitarioUsd().signum() < 0 || l.unitarioPen().signum() < 0)
                throw new IllegalArgumentException("La cantidad debe ser de 1 a 1000000 y los costos no pueden ser negativos.");
        }
        if (observaciones != null && observaciones.length() > 2000) throw new IllegalArgumentException("Las observaciones admiten hasta 2000 caracteres.");
        var ordenadas = lineas.stream().sorted(Comparator.comparing(LineaRevisada::productoId)).toList();
        var productosBloqueados = new HashMap<Long, com.ejemplo.crudpersona.model.Producto>();
        for (var l : ordenadas) productosBloqueados.put(l.productoId(), productos.bloquear(l.productoId())
                .orElseThrow(() -> new IllegalArgumentException("El producto ya no existe.")));
        Map<Long, List<LineaRevisada>> grupos = new TreeMap<>();
        Map<Long, Proveedor> proveedores = new HashMap<>();
        for (var l : ordenadas) {
            var p = productosBloqueados.get(l.productoId());
            Proveedor asignado = proveedorAsignado(l.productoId());
            if (asignado == null || !l.proveedorId().equals(asignado.id()))
                throw new IllegalArgumentException("El proveedor de " + p.getDescripcion() + " cambió o está inactivo. Cierra y vuelve a revisar el pedido.");
            if (jdbc.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT count(*) FROM pedidos_reposicion WHERE producto_id=? AND estado='PENDIENTE'"), Long.class, l.productoId()) > 0)
                throw new IllegalArgumentException(p.getDescripcion() + " ya tiene un pedido pendiente. Revísalo en Pedidos.");
            proveedores.put(asignado.id(), asignado);
            grupos.computeIfAbsent(asignado.id(), id -> new ArrayList<>()).add(l);
        }
        List<Long> creados = new ArrayList<>();
        for (var grupo : grupos.entrySet()) {
            var proveedor = proveedores.get(grupo.getKey());
            BigDecimal usd = grupo.getValue().stream().map(l -> importe(l.unitarioUsd(),l.cantidad())).reduce(BigDecimal.ZERO,BigDecimal::add);
            BigDecimal pen = grupo.getValue().stream().map(l -> importe(l.unitarioPen(),l.cantidad())).reduce(BigDecimal.ZERO,BigDecimal::add);
            Long id = jdbc.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, """
                    INSERT INTO pedidos_compra(proveedor_id,proveedor_nombre,proveedor_ruc,proveedor_contacto,
                      empresa_nombre,empresa_ruc,empresa_direccion,observaciones,total_usd,total_pen)
                    SELECT ?,?,?,?,razon_social,ruc,direccion,?,?,? FROM datos_empresa WHERE id=1 RETURNING id
                    """), Long.class, proveedor.id(),proveedor.nombre(),proveedor.ruc(),proveedor.contacto(),
                    observaciones == null ? "" : observaciones.trim(),usd,pen);
            if (id == null) throw new IllegalStateException("Configura los datos de la empresa antes de generar pedidos.");
            for (var l : grupo.getValue()) {
                var p = productosBloqueados.get(l.productoId());
                jdbc.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, """
                        INSERT INTO pedidos_reposicion(producto_id,cantidad,proveedor,costo_usd,costo_pen,pedido_compra_id,
                          producto_nombre,producto_sku,unitario_usd,unitario_pen) VALUES (?,?,?,?,?,?,?,?,?,?)
                        """),p.getId(),l.cantidad(),proveedor.nombre(),importe(l.unitarioUsd(),l.cantidad()),importe(l.unitarioPen(),l.cantidad()),
                        id,p.getDescripcion(),p.getSku(),l.unitarioUsd().setScale(6,RoundingMode.HALF_UP),l.unitarioPen().setScale(6,RoundingMode.HALF_UP));
            }
            creados.add(id);
        }
        return List.copyOf(creados);
    }
    private BigDecimal importe(BigDecimal unitario, int cantidad) {
        return unitario.setScale(6,RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(cantidad)).setScale(2,RoundingMode.HALF_UP);
    }
    public List<ResumenPedido> listarPedidos() {
        return jdbc.query(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, """
                SELECT p.*, c.numero_dua,c.fecha_ingreso, (SELECT count(*) FROM pedidos_reposicion d WHERE d.pedido_compra_id=p.id) productos
                FROM pedidos_compra p LEFT JOIN compras_dua c ON c.id=p.compra_id ORDER BY p.id DESC
                """), (rs,n) -> new ResumenPedido(rs.getLong("id"),rs.getString("proveedor_nombre"),rs.getString("fecha"),
                rs.getString("estado"),rs.getInt("productos"),rs.getBigDecimal("total_usd"),rs.getBigDecimal("total_pen"),
                rs.getObject("compra_id")==null?"":"Recibido: "+rs.getString("fecha_ingreso")+" · DUA "+rs.getString("numero_dua")));
    }
    @Transactional(readOnly=true) public PedidoCompraDocumento documentoPedido(Long id) {
        var lineas = jdbc.query(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, """
                SELECT * FROM pedidos_reposicion WHERE pedido_compra_id=? ORDER BY id
                """),(rs,n) -> new PedidoCompraDocumento.Linea(rs.getString("producto_sku"),rs.getString("producto_nombre"),rs.getInt("cantidad"),
                valor(rs.getBigDecimal("unitario_usd")),valor(rs.getBigDecimal("unitario_pen")),valor(rs.getBigDecimal("costo_usd")),valor(rs.getBigDecimal("costo_pen"))),id);
        var encontrados = jdbc.query(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT * FROM pedidos_compra WHERE id=?"),(rs,n) -> new PedidoCompraDocumento(id,
                rs.getString("proveedor_nombre"),rs.getString("proveedor_ruc"),rs.getString("proveedor_contacto"),rs.getString("empresa_nombre"),
                rs.getString("empresa_ruc"),rs.getString("empresa_direccion"),rs.getString("fecha"),rs.getString("estado"),rs.getString("observaciones"),
                rs.getBigDecimal("total_usd"),rs.getBigDecimal("total_pen"),lineas),id);
        if (encontrados.isEmpty()) throw new IllegalArgumentException("Pedido no encontrado.");
        return encontrados.get(0);
    }
    private BigDecimal valor(BigDecimal value) { return value == null ? BigDecimal.ZERO : value; }
    @Transactional public byte[] pdfPedido(Long id) { return archivoPedido(id,true); }
    @Transactional public byte[] excelPedido(Long id) { return archivoPedido(id,false); }
    private byte[] archivoPedido(Long id, boolean pdf) {
        String columna = pdf ? "pdf_documento" : "excel_documento";
        var guardados = jdbc.query(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT " + columna + " FROM pedidos_compra WHERE id=? FOR UPDATE"),(rs,n) -> rs.getBytes(1),id);
        if (guardados.isEmpty()) throw new IllegalArgumentException("Pedido no encontrado.");
        if (guardados.get(0) != null) return guardados.get(0);
        var documento = documentoPedido(id);
        byte[] bytes = pdf ? ERQZPedidoCompraExportador.pdf(documento) : ERQZPedidoCompraExportador.excel(documento);
        jdbc.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "UPDATE pedidos_compra SET " + columna + "=? WHERE id=?"),bytes,id);
        return bytes;
    }
}
