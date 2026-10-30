package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.model.ERQZArchivoDua;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
public class DBQPSunarpService implements DBQPSunarpServiceI {
    public static final List<String> CATEGORIAS=List.of("DUA_DAM","IDENTIFICACION","FICHA_VEHICULO","COMPROBANTE","ACTA_TRANSFERENCIA","CARGO_PRESENTACION","CONSTANCIA_INSCRIPCION","OTRO");
    final JdbcTemplate db;final RPLCTerminalSesion sesion;
    public DBQPSunarpService(JdbcTemplate db,RPLCTerminalSesion sesion){this.db=db;this.sesion=sesion;}
    public boolean puedeEditar(){acceso(false);return Boolean.TRUE.equals(db.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "SELECT EXISTS(SELECT 1 FROM usuarios WHERE id=? AND activo AND rol IN ('ADMINISTRADOR','GERENTE'))"),Boolean.class,sesion.usuarioId()));}
    private void acceso(boolean editar){
        var id=sesion.usuarioId();if(id==null)throw new IllegalStateException("Inicia sesion para gestionar expedientes.");
        var roles=db.queryForList(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "SELECT rol FROM usuarios WHERE id=? AND activo"),String.class,id);
        if(roles.isEmpty()||!(editar?Set.of("ADMINISTRADOR","GERENTE"):Set.of("ADMINISTRADOR","GERENTE","ALMACENERO","CONTADOR")).contains(roles.get(0)))throw new IllegalStateException("No tienes permiso para esta operacion de SUNARP.");
    }
    static String texto(String s,int max,boolean requerido){String v=s==null?"":s.trim();if(v.length()>max||requerido&&v.isBlank())throw new IllegalArgumentException("Completa los campos obligatorios sin superar su longitud permitida.");return v;}
    static String documento(String s){String v=texto(s,20,true).toUpperCase(Locale.ROOT);if(!v.matches("[A-Z0-9-]{6,20}"))throw new IllegalArgumentException("Revisa el documento del propietario (DNI, RUC o CE).");return v;}
    private Map<String,Object> moto(long id){var r=db.queryForList(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "SELECT * FROM motos_importadas WHERE id=? FOR UPDATE"),id);if(r.isEmpty())throw new IllegalArgumentException("Moto no encontrada.");return r.get(0);}
    private Map<String,Object> bloquear(long id,int version){var r=db.queryForList(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "SELECT * FROM expedientes_sunarp WHERE id=? FOR UPDATE"),id);if(r.isEmpty())throw new IllegalArgumentException("Expediente no encontrado.");var e=r.get(0);if(((Number)e.get("version")).intValue()!=version)throw new IllegalStateException("Otra persona modifico el expediente. Cierra y vuelve a abrirlo.");return e;}
    public List<Map<String,Object>> motos(String q){acceso(false);String find="%"+texto(q,150,false)+"%";return db.queryForList(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "SELECT m.*,c.numero_dua,p.descripcion AS producto FROM motos_importadas m JOIN detalle_compra_dua d ON d.id=m.detalle_compra_id JOIN compras_dua c ON c.id=d.compra_id JOIN productos p ON p.id=d.producto_id WHERE m.vin ILIKE ? OR m.motor ILIKE ? OR m.modelo ILIKE ? OR m.placa ILIKE ? OR m.titular_nombre ILIKE ? ORDER BY m.id DESC"),find,find,find,find,find);}
    public List<Map<String,Object>> compras(){acceso(false);return db.queryForList(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "SELECT d.id,c.numero_dua,d.descripcion,d.cantidad,(SELECT count(*) FROM motos_importadas m WHERE m.detalle_compra_id=d.id) AS registradas FROM detalle_compra_dua d JOIN compras_dua c ON c.id=d.compra_id WHERE c.estado<>'ANULADA' ORDER BY c.id DESC,d.id"));}
    @Transactional public long registrar(Moto b){
        acceso(true);if(b.detalleCompra()==null)throw new IllegalArgumentException("Selecciona una linea de una compra DUA aprobada.");
        var r=db.queryForList(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "SELECT d.*,c.estado FROM detalle_compra_dua d JOIN compras_dua c ON c.id=d.compra_id WHERE d.id=? FOR UPDATE OF d"),b.detalleCompra());if(r.isEmpty()||"ANULADA".equals(r.get(0).get("estado")))throw new IllegalArgumentException("Compra no disponible.");
        long count=db.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "SELECT count(*) FROM motos_importadas WHERE detalle_compra_id=?"),Long.class,b.detalleCompra());if(count>=((Number)r.get(0).get("cantidad")).longValue())throw new IllegalArgumentException("Todas las unidades de esta linea ya tienen un registro individual.");
        String vin=texto(b.vin(),30,true).toUpperCase(Locale.ROOT);if(!vin.matches("[A-Z0-9]{6,30}"))throw new IllegalArgumentException("Revisa el VIN/chasis, sin espacios ni signos.");if(b.anio()<1900||b.anio()>2100)throw new IllegalArgumentException("Anio de modelo no valido.");
        if(db.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "SELECT count(*) FROM motos_importadas WHERE vin=?"),Integer.class,vin)>0)throw new IllegalArgumentException("Este VIN/chasis ya esta registrado.");
        long id=db.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "INSERT INTO motos_importadas(detalle_compra_id,vin,motor,marca,modelo,anio,color,creado_por) VALUES (?,?,?,?,?,?,?,?) RETURNING id"),Long.class,b.detalleCompra(),vin,texto(b.motor(),50,true).toUpperCase(Locale.ROOT),texto(b.marca(),80,true),texto(b.modelo(),100,true),b.anio(),texto(b.color(),60,true),sesion.usuarioId());
        audit(id,null,"REGISTRAR_MOTO","Registro individual; no altera existencias ni acredita inscripcion oficial.");return id;
    }
    @Transactional public long registrarConArchivo(Moto b, String numeroDuaManual, ERQZArchivoDua archivoDua){
        acceso(true);
        Long detalleId = b.detalleCompra();
        if (detalleId == null) {
            String duaNum = (numeroDuaManual == null || numeroDuaManual.isBlank())
                    ? (archivoDua != null ? "DUA-" + archivoDua.nombre().replaceAll("\\.[^.]+$", "").replaceAll("[^A-Za-z0-9-]", "-").toUpperCase(Locale.ROOT) : "")
                    : numeroDuaManual.trim();
            if (duaNum.isBlank()) {
                throw new IllegalArgumentException("Selecciona una compra aprobada o ingresa el número de DUA / adjunta la imagen o PDF de la DUA.");
            }
            Long provId = db.queryForList(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "SELECT id FROM proveedores WHERE estado='ACTIVO' ORDER BY id LIMIT 1"), Long.class)
                    .stream().findFirst().orElseGet(() ->
                        db.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "INSERT INTO proveedores(razon_social,ruc,estado) VALUES ('Importador General','20100000001','ACTIVO') RETURNING id"), Long.class));
            String descProd = (b.marca() == null ? "Moto" : b.marca().trim()) + " " + (b.modelo() == null ? "" : b.modelo().trim());
            String sku = "MOTO-" + texto(b.vin(), 30, true).toUpperCase(Locale.ROOT);
            Long prodId = db.queryForList(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "SELECT id FROM productos WHERE sku=?"), Long.class, sku)
                    .stream().findFirst().orElseGet(() ->
                        db.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "INSERT INTO productos(sku,descripcion,stock_actual,stock_minimo,precio_venta_pen,costo_promedio_pen) VALUES (?,?,1,1,0,0) RETURNING id"), Long.class, sku, descProd.trim()));
            Long loteId = db.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "INSERT INTO lotes(producto_id,codigo_lote,numero_dua,ubicacion,cantidad_disponible,costo_unitario_usd,costo_unitario_pen,fecha_ingreso) VALUES (?,?,?,?,1,0,0,?) RETURNING id"),
                    Long.class, prodId, "L-" + duaNum.substring(0, Math.min(12, duaNum.length())), duaNum, "A-01-1", java.time.LocalDate.now().toString());
            Long compraId = db.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "INSERT INTO compras_dua(proveedor_id,proveedor,ruc,numero_dua,fecha_ingreso,tipo_cambio,total_fob_usd,total_gastos_usd,total_landed_usd,total_landed_pen) VALUES (?,'Importador General','20100000001',?,?,3.75,0,0,0,0) RETURNING id"),
                    Long.class, provId, duaNum, java.time.LocalDate.now().toString());
            detalleId = db.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "INSERT INTO detalle_compra_dua(compra_id,producto_id,lote_id,sku,descripcion,codigo_barras,codigo_lote,ubicacion,cantidad,costo_fob_usd,gastos_dua_usd,costo_landed_usd,costo_landed_pen,margen,precio_sugerido_pen) VALUES (?,?,?,?,?,'',?,'A-01-1',1,0,0,0,0,30,0) RETURNING id"),
                    Long.class, compraId, prodId, loteId, sku, descProd.trim(), "L-" + duaNum.substring(0, Math.min(12, duaNum.length())));
        }
        long motoId = registrar(new Moto(detalleId, b.vin(), b.motor(), b.marca(), b.modelo(), b.anio(), b.color()));
        if (archivoDua != null) {
            long expId = db.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "INSERT INTO expedientes_sunarp(moto_id,tipo,propietario_documento,propietario_nombre,vendedor_documento,vendedor_nombre,creado_por) VALUES (?,'PRIMERA_INSCRIPCION','00000000','Por asignar','','',?) RETURNING id"),
                    Long.class, motoId, sesion.usuarioId());
            db.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "INSERT INTO archivos_sunarp(expediente_id,categoria,nombre,tipo,contenido,huella,creado_por) VALUES (?,'DUA_DAM',?,?,?,?,?)"),
                    expId, archivoDua.nombre(), archivoDua.tipo(), archivoDua.contenido(), archivoDua.huella(), sesion.usuarioId());
            audit(motoId, expId, "ADJUNTAR_DOCUMENTO", "DUA_DAM adjuntada al registrar moto: " + archivoDua.nombre());
        }
        return motoId;
    }
    @Transactional public long crearExpediente(long motoId,Tramite b){
        acceso(true);var m=moto(motoId);if(!Set.of("PRIMERA_INSCRIPCION","TRANSFERENCIA").contains(b.tipo()))throw new IllegalArgumentException("Tipo de tramite no valido.");
        if(db.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "SELECT count(*) FROM expedientes_sunarp WHERE moto_id=? AND estado NOT IN ('INSCRITO','CANCELADO')"),Integer.class,motoId)>0)throw new IllegalStateException("La moto ya tiene un expediente activo.");
        boolean primera=db.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "SELECT EXISTS(SELECT 1 FROM expedientes_sunarp WHERE moto_id=? AND tipo='PRIMERA_INSCRIPCION' AND estado='INSCRITO')"),Boolean.class,motoId);
        if(b.tipo().equals("TRANSFERENCIA")&&!primera)throw new IllegalStateException("Primero registra la constancia de la primera inscripcion.");if(b.tipo().equals("PRIMERA_INSCRIPCION")&&primera)throw new IllegalStateException("La primera inscripcion ya fue informada.");
        String doc=documento(b.documento());if(b.tipo().equals("TRANSFERENCIA")&&doc.equals(m.get("titular_documento")))throw new IllegalArgumentException("El nuevo propietario debe ser distinto del titular actual.");
        long id=db.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "INSERT INTO expedientes_sunarp(moto_id,tipo,propietario_documento,propietario_nombre,vendedor_documento,vendedor_nombre,creado_por) VALUES (?,?,?,?,?,?,?) RETURNING id"),Long.class,motoId,b.tipo(),doc,texto(b.nombre(),150,true),b.tipo().equals("TRANSFERENCIA")?m.get("titular_documento"):"",b.tipo().equals("TRANSFERENCIA")?m.get("titular_nombre"):"",sesion.usuarioId());
        audit(motoId,id,"CREAR_EXPEDIENTE",b.tipo());return id;
    }
    @Transactional public void editar(long id,int version,Moto b){
        acceso(true);var original=moto(id);if(((Number)original.get("version")).intValue()!=version)throw new IllegalStateException("La moto fue modificada. Actualiza la lista.");
        if(db.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "SELECT count(*) FROM expedientes_sunarp WHERE moto_id=? AND estado<>'CANCELADO'"),Integer.class,id)>0)throw new IllegalStateException("No se cambian identificadores con expedientes vigentes. Revisa y cancela el borrador incorrecto antes de corregir la moto.");
        if(b.detalleCompra()==null||((Number)original.get("detalle_compra_id")).longValue()!=b.detalleCompra())throw new IllegalArgumentException("La compra de origen no puede cambiarse.");String vin=texto(b.vin(),30,true).toUpperCase(Locale.ROOT);
        if(!vin.matches("[A-Z0-9]{6,30}")||b.anio()<1900||b.anio()>2100)throw new IllegalArgumentException("Revisa VIN y anio del modelo.");
        if(db.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "SELECT count(*) FROM motos_importadas WHERE vin=? AND id<>?"),Integer.class,vin,id)>0)throw new IllegalArgumentException("El VIN/chasis ya corresponde a otra moto.");
        db.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "UPDATE motos_importadas SET vin=?,motor=?,marca=?,modelo=?,anio=?,color=?,version=version+1 WHERE id=?"),vin,texto(b.motor(),50,true).toUpperCase(Locale.ROOT),texto(b.marca(),80,true),texto(b.modelo(),100,true),b.anio(),texto(b.color(),60,true),id);
        audit(id,null,"CORREGIR_MOTO","VIN anterior: "+original.get("vin")+"; VIN corregido: "+vin+". Motor anterior: "+original.get("motor"));
    }
    public List<Map<String,Object>> expedientes(long motoId){acceso(false);return db.queryForList(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "SELECT * FROM expedientes_sunarp WHERE moto_id=? ORDER BY id DESC"),motoId);}
    public Map<String,Object> expediente(long id){acceso(false);var r=db.queryForList(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "SELECT e.*,m.vin,m.motor,coalesce(nullif(e.placa_resultado,''),m.placa) AS placa,coalesce(nullif(e.partida_resultado,''),m.partida) AS partida,m.marca,m.modelo,c.numero_dua FROM expedientes_sunarp e JOIN motos_importadas m ON m.id=e.moto_id JOIN detalle_compra_dua d ON d.id=m.detalle_compra_id JOIN compras_dua c ON c.id=d.compra_id WHERE e.id=?"),id);if(r.isEmpty())throw new IllegalArgumentException("Expediente no encontrado.");return r.get(0);}
    @Transactional public void actualizar(long id,Seguimiento b){
        acceso(true);var e=bloquear(id,b.version());String before=e.get("estado").toString();
        var transitions=Map.of("BORRADOR",Set.of("BORRADOR","PREPARADO","CANCELADO"),"PREPARADO",Set.of("PREPARADO","BORRADOR","PRESENTADO","CANCELADO"),"PRESENTADO",Set.of("PRESENTADO","OBSERVADO","INSCRITO","CANCELADO"),"OBSERVADO",Set.of("OBSERVADO","PREPARADO","PRESENTADO","INSCRITO","CANCELADO"));
        if(!transitions.getOrDefault(before,Set.of()).contains(b.estado()))throw new IllegalStateException("El expediente esta cerrado o el cambio de estado no corresponde.");
        String title=texto(b.titulo(),50,false),office=texto(b.oficina(),120,false),person=texto(b.responsable(),150,false),notes=texto(b.observaciones(),2000,false);
        String plateResult=texto(b.placa(),20,false).toUpperCase(Locale.ROOT),entryResult=texto(b.partida(),50,false);
        String ownerDoc=documento(b.documento()),ownerName=texto(b.nombre(),150,true);
        if(!"BORRADOR".equals(before)&&(!e.get("propietario_documento").equals(ownerDoc)||!e.get("propietario_nombre").equals(ownerName)))throw new IllegalArgumentException("El propietario solo se corrige en borrador.");
        if("TRANSFERENCIA".equals(e.get("tipo"))&&ownerDoc.equals(e.get("vendedor_documento")))throw new IllegalArgumentException("El nuevo propietario no puede ser el titular anterior.");
        if(!Set.of("BORRADOR","CANCELADO").contains(b.estado())){
            if(!b.revisado()||person.isBlank())throw new IllegalArgumentException("Indica el notario/gestor responsable y confirma la revision documental.");
            String category="PRIMERA_INSCRIPCION".equals(e.get("tipo"))?"DUA_DAM":"ACTA_TRANSFERENCIA";requireFile(id,category);
        }
        if(Set.of("PRESENTADO","OBSERVADO","INSCRITO").contains(b.estado())){if(title.isBlank()||office.isBlank()||b.anioTitulo()==null||b.anioTitulo()<1900||b.anioTitulo()>2100)throw new IllegalArgumentException("Registra numero y anio del titulo y oficina registral.");requireFile(id,"CARGO_PRESENTACION");}
        if(Set.of("OBSERVADO","CANCELADO").contains(b.estado())&&notes.isBlank())throw new IllegalArgumentException("Explica la observacion o el motivo de cancelacion.");
        if("INSCRITO".equals(b.estado())){
            requireFile(id,"CONSTANCIA_INSCRIPCION");String plate=texto(b.placa(),20,true).toUpperCase(Locale.ROOT),entry=texto(b.partida(),50,true);long moto=((Number)e.get("moto_id")).longValue();var m=moto(moto);
            if(db.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "SELECT count(*) FROM motos_importadas WHERE placa=? AND id<>?"),Integer.class,plate,moto)>0)throw new IllegalArgumentException("La placa ya pertenece a otra moto registrada.");
            if("TRANSFERENCIA".equals(e.get("tipo"))&&(!m.get("placa").equals(plate)||!m.get("partida").equals(entry)))throw new IllegalArgumentException("La transferencia debe conservar placa y partida de la moto.");
            db.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "UPDATE motos_importadas SET placa=?,partida=?,titular_documento=?,titular_nombre=?,version=version+1 WHERE id=?"),plate,entry,ownerDoc,ownerName,moto);
        }
        db.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "UPDATE expedientes_sunarp SET estado=?,titulo=?,anio_titulo=?,oficina=?,responsable=?,observaciones=?,revision_documental=?,propietario_documento=?,propietario_nombre=?,placa_resultado=?,partida_resultado=?,version=version+1,actualizado_en=now() WHERE id=?"),b.estado(),title,b.anioTitulo(),office,person,notes,b.revisado(),ownerDoc,ownerName,plateResult,entryResult,id);
        audit(((Number)e.get("moto_id")).longValue(),id,"SEGUIMIENTO_"+b.estado(),"Registro interno por usuario: "+before+" -> "+b.estado()+". "+notes);
    }
    private void requireFile(long id,String category){if(db.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "SELECT count(*) FROM archivos_sunarp WHERE expediente_id=? AND categoria=?"),Integer.class,id,category)==0)throw new IllegalArgumentException("Adjunta el documento: "+category.replace('_',' ')+".");}
    @Transactional public void adjuntar(long id,int version,String category,ERQZArchivoDua file){
        acceso(true);var e=bloquear(id,version);if(Set.of("INSCRITO","CANCELADO").contains(e.get("estado")))throw new IllegalStateException("Los expedientes cerrados son de solo lectura.");
        if(file==null||!CATEGORIAS.contains(category))throw new IllegalArgumentException("Selecciona tipo y archivo PDF, JPG o PNG.");
        var bytes=db.queryForMap(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "SELECT count(*) AS cantidad,coalesce(sum(octet_length(contenido)),0) AS total FROM archivos_sunarp WHERE expediente_id=?"),id);
        if(((Number)bytes.get("cantidad")).intValue()>=20||((Number)bytes.get("total")).longValue()+file.tamano()>ERQZArchivoDua.MAX_TOTAL_BYTES)throw new IllegalArgumentException("Maximo 20 archivos y 50 MB por expediente.");
        if(db.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "SELECT count(*) FROM archivos_sunarp WHERE expediente_id=? AND huella=?"),Integer.class,id,file.huella())>0)throw new IllegalArgumentException("El archivo ya esta adjunto.");
        db.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "INSERT INTO archivos_sunarp(expediente_id,categoria,nombre,tipo,contenido,huella,creado_por) VALUES (?,?,?,?,?,?,?)"),id,category,file.nombre(),file.tipo(),file.contenido(),file.huella(),sesion.usuarioId());db.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "UPDATE expedientes_sunarp SET version=version+1,actualizado_en=now() WHERE id=?"),id);audit(((Number)e.get("moto_id")).longValue(),id,"ADJUNTAR_DOCUMENTO",category+": "+file.nombre());
    }
    public List<Map<String,Object>> archivos(long id){acceso(false);return db.queryForList(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "SELECT id,categoria,nombre,octet_length(contenido) AS bytes,creado_en FROM archivos_sunarp WHERE expediente_id=? ORDER BY id"),id);}
    public ERQZArchivoDua descargar(long id){acceso(false);var r=db.query(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "SELECT nombre,tipo,contenido FROM archivos_sunarp WHERE id=?"),(rs,n)->new ERQZArchivoDua(rs.getString(1),rs.getString(2),rs.getBytes(3)),id);if(r.isEmpty())throw new IllegalArgumentException("Archivo no encontrado.");return r.get(0);}
    public List<Map<String,Object>> historial(long id){acceso(false);return db.queryForList(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "SELECT h.fecha,u.nombre_completo AS usuario,h.accion,h.detalle FROM historial_sunarp h JOIN usuarios u ON u.id=h.usuario_id WHERE moto_id=? ORDER BY h.id DESC"),id);}
    private void audit(long moto,Long expediente,String action,String detail){db.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(db, "INSERT INTO historial_sunarp(moto_id,expediente_id,usuario_id,accion,detalle) VALUES (?,?,?,?,?)"),moto,expediente,sesion.usuarioId(),action,detail);}
}
