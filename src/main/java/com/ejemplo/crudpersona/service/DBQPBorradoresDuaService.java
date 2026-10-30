package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.model.ERQZArchivoDua;
import com.ejemplo.crudpersona.service.ERQZCompraServiceI.Linea;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class DBQPBorradoresDuaService implements DBQPBorradoresDuaServiceI {
    private final JdbcTemplate jdbc;
    private final RPLCTerminalSesion sesion;
    private final ERQZCompraServiceI compras;
    private final DBQPDocumentosDuaServiceI documentos;

    public DBQPBorradoresDuaService(JdbcTemplate jdbc, RPLCTerminalSesion sesion, ERQZCompraServiceI compras, DBQPDocumentosDuaServiceI documentos) {
        this.jdbc=jdbc; this.sesion=sesion; this.compras=compras; this.documentos=documentos;
    }
    private String rol() {
        var roles=jdbc.query(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT rol FROM usuarios WHERE id=? AND activo=true"), (rs,n)->rs.getString(1),sesion.usuarioId());
        return roles.isEmpty()?"":roles.get(0);
    }
    private void autorizar() {
        if (!Set.of("ADMINISTRADOR","GERENTE","ALMACENERO").contains(rol()))
            throw new IllegalStateException("Tu usuario no tiene permiso para gestionar importaciones pendientes.");
    }
    @Transactional(readOnly=true) public boolean puedeAprobar() { return Set.of("ADMINISTRADOR","GERENTE").contains(rol()); }
    private void bloquear() { jdbc.execute(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT pg_advisory_xact_lock(74021, 2)")); }
    private void pendiente(Long id, int version) {
        var filas=jdbc.queryForList(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT estado,version FROM borradores_dua WHERE id=? FOR UPDATE"),id);
        if (filas.isEmpty()) throw new IllegalArgumentException("El registro ya no existe. Actualiza la lista.");
        if (!"PENDIENTE".equals(filas.get(0).get("estado"))) throw new IllegalStateException("La DUA ya fue aprobada; no se puede modificar ni volver a enviar a stock.");
        if (((Number)filas.get(0).get("version")).intValue()!=version)
            throw new IllegalStateException("Otra caja modificó este registro. Vuelve a abrirlo para revisar los cambios.");
    }
    @Transactional public Long guardar(Long id, int version, Long proveedorId, String dua, LocalDate fecha, BigDecimal cambio,
                                      List<Linea> lineas, Long documentoId, List<ERQZArchivoDua> archivos) {
        autorizar(); bloquear();
        if (id!=null) pendiente(id,version);
        validar(proveedorId,dua,fecha,cambio,lineas);
        long repetidos=jdbc.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT count(*) FROM borradores_dua WHERE proveedor_id=? AND lower(numero_dua)=lower(?) AND id<>?"),
                Long.class,proveedorId,dua.trim(),id==null?-1L:id);
        if (repetidos>0 || jdbc.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT count(*) FROM compras_dua WHERE proveedor_id=? AND lower(trim(numero_dua))=lower(?)"),
                Long.class,proveedorId,dua.trim())>0) throw new IllegalArgumentException("Esta DUA ya está registrada para el proveedor.");
        Long previo=id==null?null:jdbc.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT documento_id FROM borradores_dua WHERE id=?"),Long.class,id);
        if (previo!=null && !previo.equals(documentoId)) throw new IllegalArgumentException("Conserva el expediente de esta DUA; puedes editar sus archivos desde Ver documentos.");
        if (documentoId!=null) {
            var doc=jdbc.queryForList(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT compra_id FROM documentos_dua WHERE id=? FOR UPDATE"),documentoId);
            if (doc.isEmpty() || doc.get(0).get("compra_id")!=null) throw new IllegalArgumentException("El documento no existe o pertenece a una compra aprobada.");
            if (jdbc.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT count(*) FROM borradores_dua WHERE documento_id=? AND id<>?"),Long.class,documentoId,id==null?-1L:id)>0)
                throw new IllegalArgumentException("Este documento ya pertenece a otra importación.");
            if (archivos!=null && !archivos.isEmpty()) documentos.adjuntar(documentoId,archivos);
        } else if (archivos!=null && !archivos.isEmpty()) documentoId=documentos.crear(archivos);
        if (id==null) {
            id=jdbc.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, """
                    INSERT INTO borradores_dua(proveedor_id,numero_dua,fecha,tipo_cambio,documento_id,creado_por,actualizado_por)
                    VALUES (?,?,?,?,?,?,?) RETURNING id
                    """),Long.class,proveedorId,dua.trim(),fecha,cambio,documentoId,sesion.usuarioId(),sesion.usuarioId());
        } else {
            jdbc.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, """
                    UPDATE borradores_dua SET proveedor_id=?,numero_dua=?,fecha=?,tipo_cambio=?,documento_id=?,
                        version=version+1,actualizado_por=?,actualizado_en=localtimestamp WHERE id=?
                    """),proveedorId,dua.trim(),fecha,cambio,documentoId,sesion.usuarioId(),id);
            jdbc.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "DELETE FROM lineas_borrador_dua WHERE borrador_id=?"),id);
        }
        for (var l:lineas) jdbc.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, """
                INSERT INTO lineas_borrador_dua(borrador_id,sku,descripcion,codigo_barras,lote,ubicacion,cantidad,fob_usd,gastos_usd,margen)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """),id,l.sku().trim(),l.descripcion().trim(),l.codigoBarras()==null?"":l.codigoBarras().trim(),l.lote().trim(),
                l.ubicacion().trim().toUpperCase(Locale.ROOT),l.cantidad(),l.fobUsd(),l.gastosUsd(),l.margen());
        return id;
    }
    private void validar(Long proveedor, String dua, LocalDate fecha, BigDecimal cambio, List<Linea> lineas) {
        if (proveedor==null || dua==null || dua.isBlank() || fecha==null || cambio==null || cambio.signum()<=0)
            throw new IllegalArgumentException("Completa proveedor, DUA, fecha y tipo de cambio.");
        if (jdbc.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT count(*) FROM proveedores WHERE id=? AND estado='ACTIVO'"),Long.class,proveedor)==0)
            throw new IllegalArgumentException("Selecciona un proveedor activo.");
        if (lineas==null || lineas.isEmpty() || lineas.size()>500) throw new IllegalArgumentException("Agrega entre 1 y 500 líneas de productos.");
        Set<String> lotes=new HashSet<>(); int unidades=0;
        for (var l:lineas) {
            if (l==null || l.sku()==null || l.sku().isBlank() || l.descripcion()==null || l.descripcion().isBlank()
                    || l.lote()==null || l.lote().isBlank() || l.cantidad()<=0 || l.cantidad()>1_000_000)
                throw new IllegalArgumentException("Revisa SKU, nombre, lote y cantidad de cada producto.");
            if (l.ubicacion()==null || !l.ubicacion().trim().toUpperCase(Locale.ROOT).matches("[A-Z]{1,4}-[0-9]{1,2}-[1-9][0-9]?"))
                throw new IllegalArgumentException("Indica una ubicación válida, por ejemplo A-01-1.");
            if (Integer.parseInt(l.ubicacion().trim().split("-")[1])==0) throw new IllegalArgumentException("El estante debe ser mayor que cero.");
            for (var v:new BigDecimal[]{l.fobUsd(),l.gastosUsd(),l.margen()})
                if (v==null || v.signum()<0) throw new IllegalArgumentException("Costos y margen deben ser mayores o iguales a cero.");
            if (!lotes.add(l.sku().trim().toUpperCase(Locale.ROOT)+"\n"+l.lote().trim().toUpperCase(Locale.ROOT)))
                throw new IllegalArgumentException("Un producto tiene el mismo lote repetido. Edita su cantidad en una sola línea.");
            unidades=Math.addExact(unidades,l.cantidad());
        }
    }
    private static final String RESUMEN="""
            SELECT b.id,b.numero_dua,p.razon_social,b.fecha,b.estado,b.version,b.documento_id,b.compra_id,
                count(l.id) productos,COALESCE(sum(l.cantidad),0) unidades,
                COALESCE(sum((l.fob_usd+l.gastos_usd)*l.cantidad)*b.tipo_cambio,0) total
            FROM borradores_dua b JOIN proveedores p ON p.id=b.proveedor_id
            LEFT JOIN lineas_borrador_dua l ON l.borrador_id=b.id
            """;
    private Resumen resumen(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Resumen(rs.getLong("id"),rs.getString("numero_dua"),rs.getString("razon_social"),java.time.LocalDate.parse(rs.getString("fecha").substring(0,10)),
                rs.getString("estado"),rs.getInt("version"),rs.getInt("productos"),rs.getInt("unidades"),rs.getBigDecimal("total"),
                DBQPSqlDialect.nullableLong(rs,"documento_id"),DBQPSqlDialect.nullableLong(rs,"compra_id"));
    }
    @Transactional(readOnly=true) public List<Resumen> listar(String busqueda) {
        autorizar(); String q=busqueda==null?"":busqueda.trim().toLowerCase(Locale.ROOT);
        String patron="%"+q.replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        return jdbc.query(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, RESUMEN+" WHERE lower(b.numero_dua||' '||p.razon_social) LIKE ? ESCAPE '!' GROUP BY b.id,p.id ORDER BY b.id DESC LIMIT 200"),
                (rs,n)->resumen(rs),patron);
    }
    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Detalle obtener(Long id) {
        autorizar(); var rows=jdbc.query(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, RESUMEN+" WHERE b.id=? GROUP BY b.id,p.id"),(rs,n)->resumen(rs),id);
        if (rows.isEmpty()) throw new IllegalArgumentException("Importación no encontrada.");
        var cab=jdbc.queryForMap(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, """
                SELECT b.proveedor_id,b.tipo_cambio,COALESCE(u.nombre_completo,'') autor,COALESCE(a.nombre_completo,'') aprobador
                FROM borradores_dua b LEFT JOIN usuarios u ON u.id=b.creado_por LEFT JOIN usuarios a ON a.id=b.aprobado_por WHERE b.id=?
                """),id);
        var lineas=jdbc.query(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT * FROM lineas_borrador_dua WHERE borrador_id=? ORDER BY id"),(rs,n)->new Linea(rs.getString("sku"),
                rs.getString("descripcion"),rs.getString("codigo_barras"),rs.getString("lote"),rs.getString("ubicacion"),rs.getInt("cantidad"),
                rs.getBigDecimal("fob_usd"),rs.getBigDecimal("gastos_usd"),rs.getBigDecimal("margen")),id);
        return new Detalle(rows.get(0),((Number)cab.get("proveedor_id")).longValue(),new BigDecimal(cab.get("tipo_cambio").toString()),
                (String)cab.get("autor"),(String)cab.get("aprobador"),lineas);
    }
    @Transactional public Long aprobar(Long id, int version) {
        if (!puedeAprobar()) throw new IllegalStateException("Solo un administrador o gerente puede aprobar el ingreso a stock.");
        bloquear(); pendiente(id,version); var d=obtener(id);
        Long compra=compras.registrar(d.proveedorId(),d.resumen().dua(),d.resumen().fecha(),d.tipoCambio(),d.lineas());
        jdbc.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, """
                UPDATE borradores_dua SET estado='APROBADA',compra_id=?,aprobado_por=?,aprobado_en=localtimestamp,
                    actualizado_por=?,actualizado_en=localtimestamp,version=version+1 WHERE id=?
                """),compra,sesion.usuarioId(),sesion.usuarioId(),id);
        documentos.guardarConCompra(d.resumen().documentoId(),compra,List.of());
        registrarMotosDeCompraParaSunarp(compra, d.resumen().documentoId());
        return compra;
    }
    private void registrarMotosDeCompraParaSunarp(Long compraId, Long documentoDuaId) {
        try {
            var detalles = jdbc.queryForList(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT id,descripcion FROM detalle_compra_dua WHERE compra_id=? ORDER BY id"), compraId);
            for (var det : detalles) {
                String desc = det.get("descripcion") == null ? "" : det.get("descripcion").toString();
                if (!desc.contains("VIN:") || !desc.contains("MOTOR:")) continue;
                Long detId = ((Number) det.get("id")).longValue();
                var mVin = java.util.regex.Pattern.compile("VIN:([A-Z0-9]{6,30})").matcher(desc);
                var mMot = java.util.regex.Pattern.compile("MOTOR:([A-Z0-9-]{4,40})").matcher(desc);
                if (!mVin.find() || !mMot.find()) continue;
                String vin = mVin.group(1);
                String motor = mMot.group(1);
                if (jdbc.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT count(*) FROM motos_importadas WHERE vin=?"), Integer.class, vin) > 0) continue;
                String limpio = desc.replaceAll("\\[.*\\]", "").replace("MOTOCICLETA", "").trim();
                String[] partes = limpio.split("\\s+");
                String marca = partes.length > 0 ? partes[0] : "HONDA";
                String modelo = partes.length > 1 ? partes[1] : "NAVI";
                int anio = 2021;
                String color = "NEGRO";
                for (String p : partes) {
                    if (p.matches("20\\d{2}|19\\d{2}")) anio = Integer.parseInt(p);
                    else if (p.matches("NEGRO|ROJO|AZUL|BLANCO|GRIS|PLATA|VERDE|AMARILLO")) color = p;
                }
                Long motoId = jdbc.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc,
                        "INSERT INTO motos_importadas(detalle_compra_id,vin,motor,marca,modelo,anio,color,creado_por) VALUES (?,?,?,?,?,?,?,?) RETURNING id"),
                        Long.class, detId, vin, motor, marca, modelo, anio, color, sesion.usuarioId());
                jdbc.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc,
                        "INSERT INTO historial_sunarp(moto_id,expediente_id,usuario_id,accion,detalle) VALUES (?,null,?,'REGISTRAR_MOTO','Capturada automaticamente desde DUA aprobada.')"),
                        motoId, sesion.usuarioId());
                if (documentoDuaId != null) {
                    var archivos = documentos.archivos(documentoDuaId);
                    if (!archivos.isEmpty()) {
                        Long expId = jdbc.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc,
                                "INSERT INTO expedientes_sunarp(moto_id,tipo,propietario_documento,propietario_nombre,vendedor_documento,vendedor_nombre,creado_por) VALUES (?,'PRIMERA_INSCRIPCION','20103733015','HONDA DEL PERU S.A','','',?) RETURNING id"),
                                Long.class, motoId, sesion.usuarioId());
                        for (var a : archivos) {
                            var arch = documentos.descargar(a.id());
                            jdbc.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc,
                                    "INSERT INTO archivos_sunarp(expediente_id,categoria,nombre,tipo,contenido,huella,creado_por) VALUES (?,'DUA_DAM',?,?,?,?,?)"),
                                    expId, arch.nombre(), arch.tipo(), arch.contenido(), arch.huella(), sesion.usuarioId());
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }
    @Transactional public void eliminar(Long id, int version) {
        autorizar(); bloquear(); pendiente(id,version);
        Long documento=jdbc.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT documento_id FROM borradores_dua WHERE id=?"),Long.class,id);
        if (documento!=null) {
            var compra=jdbc.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT compra_id FROM documentos_dua WHERE id=? FOR UPDATE"),Long.class,documento);
            if (compra!=null) throw new IllegalStateException("El expediente pertenece a una compra aprobada.");
        }
        jdbc.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "DELETE FROM borradores_dua WHERE id=?"),id);
        if (documento!=null) jdbc.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "DELETE FROM documentos_dua WHERE id=?"),documento);
    }
}
