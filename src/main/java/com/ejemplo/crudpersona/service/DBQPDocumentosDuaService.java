package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.model.ERQZArchivoDua;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

@Service
public class DBQPDocumentosDuaService implements DBQPDocumentosDuaServiceI {
    private final JdbcTemplate jdbc;
    private final RPLCTerminalSesion sesion;
    public DBQPDocumentosDuaService(JdbcTemplate jdbc, RPLCTerminalSesion sesion) { this.jdbc = jdbc; this.sesion = sesion; }

    private void autorizar() {
        if (sesion.usuarioId() == null || !Boolean.TRUE.equals(jdbc.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, """
                SELECT EXISTS(SELECT 1 FROM usuarios WHERE id=? AND activo=true
                    AND rol IN ('ADMINISTRADOR','GERENTE','ALMACENERO'))
                """), Boolean.class, sesion.usuarioId())))
            throw new IllegalStateException("Tu usuario no tiene permiso para gestionar documentos DUA.");
    }
    private void bloquear(Long id) {
        if (id == null || jdbc.query(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT id FROM documentos_dua WHERE id=? FOR UPDATE"), (rs,n) -> rs.getLong(1), id).isEmpty())
            throw new IllegalArgumentException("Documento DUA no encontrado.");
    }
    @Transactional public Long crear(List<ERQZArchivoDua> archivos) {
        autorizar(); validarLista(archivos);
        Long id = jdbc.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "INSERT INTO documentos_dua(referencia,creado_por) VALUES (?,?) RETURNING id"),
                Long.class, archivos.get(0).nombre(), sesion.usuarioId());
        insertar(id, archivos);
        return id;
    }
    @Transactional public void adjuntar(Long documentoId, List<ERQZArchivoDua> archivos) {
        autorizar(); bloquearEdicion(documentoId); insertar(documentoId, archivos); actualizarVersion(documentoId);
    }
    private void bloquearEdicion(Long id) {
        jdbc.execute(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT pg_advisory_xact_lock(74021, 2)")); bloquear(id);
        if (jdbc.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT compra_id FROM documentos_dua WHERE id=?"),Long.class,id)!=null)
            throw new IllegalStateException("Los documentos de una compra aprobada son de solo lectura.");
    }
    private void actualizarVersion(Long id) {
        jdbc.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "UPDATE borradores_dua SET version=version+1,actualizado_por=?,actualizado_en=localtimestamp WHERE documento_id=? AND estado='PENDIENTE'"),
                sesion.usuarioId(),id);
    }
    @Transactional public void eliminarArchivo(Long archivoId) {
        autorizar(); jdbc.execute(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT pg_advisory_xact_lock(74021, 2)"));
        var ids=jdbc.query(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT documento_id FROM archivos_dua WHERE id=?"),(rs,n)->rs.getLong(1),archivoId);
        if (ids.isEmpty()) throw new IllegalArgumentException("El archivo ya no existe.");
        Long documento=ids.get(0); bloquearEdicion(documento);
        jdbc.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "DELETE FROM archivos_dua WHERE id=?"),archivoId); actualizarVersion(documento);
    }
    @Transactional public void vincular(Long documentoId, Long compraId) {
        autorizar(); vincularInterno(documentoId, compraId);
    }
    private void vincularInterno(Long documentoId, Long compraId) {
        jdbc.execute(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT pg_advisory_xact_lock(74021, 2)"));
        // Lock the purchase before its documents so all terminals acquire locks in the same order.
        if (compraId == null || jdbc.query(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT id FROM compras_dua WHERE id=? FOR UPDATE"), (rs,n) -> rs.getLong(1), compraId).isEmpty())
            throw new IllegalArgumentException("Compra no encontrada.");
        bloquear(documentoId);
        var borradores=jdbc.queryForList(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT estado,compra_id FROM borradores_dua WHERE documento_id=?"),documentoId);
        if (!borradores.isEmpty() && (!"APROBADA".equals(borradores.get(0).get("estado")) || !(borradores.get(0).get("compra_id") instanceof Number vinculada) || vinculada.longValue()!=compraId))
            throw new IllegalStateException("Este documento pertenece a una DUA pendiente. Apruébala desde Pendientes y aprobadas.");
        var actual = jdbc.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT compra_id FROM documentos_dua WHERE id=?"), Long.class, documentoId);
        if (actual != null && !actual.equals(compraId)) throw new IllegalArgumentException("El documento ya está vinculado a otra compra.");
        if (jdbc.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT count(*) FROM documentos_dua WHERE compra_id=? AND id<>?"), Integer.class, compraId, documentoId) > 0)
            throw new IllegalArgumentException("La compra ya tiene un expediente documental. Adjunta los archivos allí.");
        jdbc.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "UPDATE documentos_dua SET compra_id=? WHERE id=?"), compraId, documentoId);
    }
    @Transactional public void guardarConCompra(Long documentoId, Long compraId, List<ERQZArchivoDua> archivos) {
        if (documentoId == null && (archivos == null || archivos.isEmpty())) return;
        autorizar();
        Long id = documentoId == null ? crear(archivos) : documentoId;
        vincularInterno(id, compraId);
        if (documentoId != null && archivos != null && !archivos.isEmpty()) insertar(id, archivos);
    }
    private void validarLista(List<ERQZArchivoDua> archivos) {
        if (archivos == null || archivos.isEmpty()) throw new IllegalArgumentException("Adjunta al menos una foto o un PDF.");
        if (archivos.size() > ERQZArchivoDua.MAX_ARCHIVOS) throw new IllegalArgumentException("Se admiten hasta 20 archivos por DUA.");
        var huellas = new HashSet<String>(); long total = 0;
        for (var archivo : archivos) {
            if (archivo == null) throw new IllegalArgumentException("Hay un archivo inválido.");
            if (!huellas.add(archivo.huella())) throw new IllegalArgumentException("El mismo archivo está repetido.");
            total += archivo.tamano();
        }
        if (total > ERQZArchivoDua.MAX_TOTAL_BYTES) throw new IllegalArgumentException("Los archivos de una DUA no pueden superar 50 MB en total.");
    }
    private void insertar(Long id, List<ERQZArchivoDua> archivos) {
        validarLista(archivos);
        int actuales = jdbc.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT count(*) FROM archivos_dua WHERE documento_id=?"), Integer.class, id);
        long bytes = jdbc.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT COALESCE(sum(octet_length(contenido)),0) FROM archivos_dua WHERE documento_id=?"), Long.class, id);
        if (actuales + archivos.size() > ERQZArchivoDua.MAX_ARCHIVOS || bytes + archivos.stream().mapToLong(ERQZArchivoDua::tamano).sum() > ERQZArchivoDua.MAX_TOTAL_BYTES)
            throw new IllegalArgumentException("El expediente admite hasta 20 archivos y 50 MB en total.");
        for (var archivo : archivos) {
            if (jdbc.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT count(*) FROM archivos_dua WHERE documento_id=? AND sha256=?"), Integer.class, id, archivo.huella()) > 0)
                throw new IllegalArgumentException("Este archivo ya está guardado: " + archivo.nombre());
            jdbc.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "INSERT INTO archivos_dua(documento_id,nombre,tipo,contenido,sha256,creado_por) VALUES (?,?,?,?,?,?)"),
                    id, archivo.nombre(), archivo.tipo(), archivo.contenido(), archivo.huella(), sesion.usuarioId());
        }
    }
    @Transactional(readOnly=true) public List<Documento> listar(String busqueda) {
        autorizar();
        String termino = busqueda == null ? "" : busqueda.strip().toLowerCase(Locale.ROOT);
        String patron = "%" + termino.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return jdbc.query(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, """
                SELECT d.id,d.referencia,d.compra_id,COALESCE(c.numero_dua,b.numero_dua,'') dua,COALESCE(c.proveedor,p.razon_social,'') proveedor,
                    (SELECT count(*) FROM archivos_dua a WHERE a.documento_id=d.id) archivos,d.creado_en,b.id borrador_id
                FROM documentos_dua d LEFT JOIN compras_dua c ON c.id=d.compra_id
                LEFT JOIN borradores_dua b ON b.documento_id=d.id LEFT JOIN proveedores p ON p.id=b.proveedor_id
                WHERE lower(d.referencia || ' ' || COALESCE(c.numero_dua,b.numero_dua,'') || ' ' || COALESCE(c.proveedor,p.razon_social,'')) LIKE ? ESCAPE '!'
                   OR EXISTS(SELECT 1 FROM archivos_dua a WHERE a.documento_id=d.id AND lower(a.nombre) LIKE ? ESCAPE '!')
                ORDER BY d.id DESC LIMIT 200
                """), (rs,n) -> new Documento(rs.getLong("id"),rs.getString("referencia"),DBQPSqlDialect.nullableLong(rs,"compra_id"),
                rs.getString("dua"),rs.getString("proveedor"),rs.getInt("archivos"),rs.getTimestamp("creado_en").toLocalDateTime(),DBQPSqlDialect.nullableLong(rs,"borrador_id")), patron, patron);
    }
    @Transactional(readOnly=true) public List<Adjunto> archivos(Long documentoId) {
        autorizar();
        return jdbc.query(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT id,nombre,tipo,octet_length(contenido) tamano,creado_en FROM archivos_dua WHERE documento_id=? ORDER BY id"),
                (rs,n) -> new Adjunto(rs.getLong("id"),rs.getString("nombre"),rs.getString("tipo"),rs.getInt("tamano"),rs.getTimestamp("creado_en").toLocalDateTime()), documentoId);
    }
    @Transactional(readOnly=true) public ERQZArchivoDua descargar(Long archivoId) {
        autorizar();
        var archivos = jdbc.query(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, "SELECT nombre,tipo,contenido FROM archivos_dua WHERE id=?"),
                (rs,n) -> new ERQZArchivoDua(rs.getString("nombre"),rs.getString("tipo"),rs.getBytes("contenido")), archivoId);
        if (archivos.isEmpty()) throw new IllegalArgumentException("Archivo no encontrado.");
        return archivos.get(0);
    }
    @Transactional(readOnly=true) public List<Compra> comprasSinDocumento() {
        autorizar();
        return jdbc.query(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, """
                SELECT c.id,c.numero_dua,c.proveedor,c.fecha_ingreso FROM compras_dua c
                WHERE NOT EXISTS(SELECT 1 FROM documentos_dua d WHERE d.compra_id=c.id) ORDER BY c.id DESC
                """), (rs,n) -> new Compra(rs.getLong("id"),rs.getString("numero_dua"),rs.getString("proveedor"),rs.getString("fecha_ingreso")));
    }
}
