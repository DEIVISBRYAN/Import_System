package com.ejemplo.crudpersona;

import com.ejemplo.crudpersona.model.*;
import com.ejemplo.crudpersona.repository.UsuarioRepository;
import com.ejemplo.crudpersona.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.nio.file.*;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="TEST_POSTGRESQL", matches="true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DocumentosDuaIntegrationTest {
    ConfigurableApplicationContext context;
    String schema, url, user, password;
    JdbcTemplate jdbc;
    DBQPDocumentosDuaServiceI docs;
    ERQZCompraServiceI compras;
    Usuario admin;

    @BeforeAll void iniciar() throws Exception {
        Properties props = new Properties();
        try (var reader = Files.newBufferedReader(Path.of(".local/database.properties"))) { props.load(reader); }
        url = props.getProperty("spring.datasource.url"); user = props.getProperty("spring.datasource.username"); password = props.getProperty("spring.datasource.password");
        schema = "verificacion_docs_" + UUID.randomUUID().toString().replace("-", "");
        try (var c = DriverManager.getConnection(url,user,password); var s = c.createStatement()) { s.execute("CREATE SCHEMA " + schema); }
        context = new SpringApplicationBuilder(RPLCSpringAppConfig.class).profiles("postgresql").run("--spring.datasource.url=" + url + "?currentSchema=" + schema,
                "--spring.flyway.default-schema=" + schema,"--app.database.import-sqlite=","--logging.level.root=WARN");
        jdbc = context.getBean(JdbcTemplate.class); docs = context.getBean(DBQPDocumentosDuaServiceI.class); compras = context.getBean(ERQZCompraServiceI.class);
    }
    @AfterAll void cerrar() throws Exception {
        if (context != null) context.close();
        if (schema != null && schema.matches("verificacion_docs_[a-f0-9]{32}"))
            try (var c=DriverManager.getConnection(url,user,password); var s=c.createStatement()) { s.execute("DROP SCHEMA " + schema + " CASCADE"); }
    }
    @BeforeEach void limpiar() {
        EsquemaPruebas.limpiar(jdbc,schema,"documentos_dua","compras_dua","proveedores","productos","usuarios");
        admin = context.getBean(UsuarioRepository.class).save(Usuario.builder().username("admin-docs").nombreCompleto("Administrador de prueba")
                .passwordHash("solo-pruebas").activo(true).rol(Rol.ADMINISTRADOR).build());
        context.getBean(RPLCTerminalSesion.class).setUsuario(admin);
    }
    ERQZArchivoDua foto(int n) throws Exception { return new ERQZArchivoDua("Foto " + n + ".png","image/png",ArchivoDuaTest.foto("png",n)); }
    ERQZArchivoDua pdf() throws Exception { return new ERQZArchivoDua("DUA importación.pdf","application/pdf",ArchivoDuaTest.pdf("Documento DUA de prueba")); }
    Long proveedor() { return jdbc.queryForObject("INSERT INTO proveedores(razon_social,estado) VALUES ('Proveedor documentos','ACTIVO') RETURNING id",Long.class); }
    List<ERQZCompraServiceI.Linea> lineas() { return List.of(new ERQZCompraServiceI.Linea("DOCS-1","Producto con DUA","","LOTE-DOCS","A-01-1",3,BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.TEN)); }

    @Test void guardaSinDigitarCompraNiCambiarStockYRecuperaOriginales() throws Exception {
        var pdf = pdf(); var foto = foto(1); Long id = docs.crear(List.of(pdf,foto));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM compras_dua",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM productos",Integer.class));
        assertNull(docs.listar("").get(0).compraId()); assertEquals(2,docs.archivos(id).size());
        assertEquals(1,docs.listar("importación").size()); assertEquals(1,docs.listar("Foto 1").size());
        assertTrue(docs.listar("%").isEmpty());
        var almacenado = docs.descargar(docs.archivos(id).get(0).id());
        assertArrayEquals(pdf.contenido(),almacenado.contenido()); assertEquals(pdf.huella(),almacenado.huella());
        assertEquals(admin.getId(),jdbc.queryForObject("SELECT creado_por FROM documentos_dua WHERE id=?",Long.class,id));
    }
    @Test void rechazaDuplicadosYRevierteTodoElLoteDeArchivos() throws Exception {
        var imagen = foto(1); Long id = docs.crear(List.of(imagen));
        assertThrows(IllegalArgumentException.class,()->docs.adjuntar(id,List.of(pdf(),imagen)));
        assertEquals(1,docs.archivos(id).size());
        assertThrows(IllegalArgumentException.class,()->docs.crear(List.of(imagen,imagen)));
        assertEquals(1,docs.listar("").size());
        assertThrows(IllegalArgumentException.class,()->docs.crear(List.of()));
    }
    @Test void compraYAdjuntosSeGuardanOSeReviertenJuntos() throws Exception {
        Long proveedor=proveedor(); var imagen=foto(1);
        assertThrows(IllegalArgumentException.class,()->compras.registrarConDocumentos(proveedor,"DUA-ERROR",LocalDate.now(),BigDecimal.ONE,lineas(),null,List.of(imagen,imagen)));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM compras_dua",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM productos",Integer.class));
        Long id=compras.registrarConDocumentos(proveedor,"DUA-COMPRA",LocalDate.now(),BigDecimal.ONE,lineas(),null,List.of(imagen));
        assertEquals(id,docs.listar("DUA-COMPRA").get(0).compraId());
        assertEquals(3,jdbc.queryForObject("SELECT stock_actual FROM productos WHERE sku='DOCS-1'",Integer.class));
    }
    @Test void vinculaDocumentoPrevioSinDuplicarInventario() throws Exception {
        Long documento=docs.crear(List.of(pdf()));
        Long compra=compras.registrarConDocumentos(proveedor(),"DUA-VINCULADA",LocalDate.now(),BigDecimal.ONE,lineas(),documento,List.of(foto(2)));
        assertEquals(compra,docs.listar("").get(0).compraId()); assertEquals(2,docs.archivos(documento).size());
        docs.vincular(documento,compra);
        assertEquals(3,jdbc.queryForObject("SELECT stock_actual FROM productos WHERE sku='DOCS-1'",Integer.class));
        Long otra=compras.registrar(proveedor(),"DUA-OTRA",LocalDate.now(),BigDecimal.ONE,
                List.of(new ERQZCompraServiceI.Linea("DOCS-2","Otro producto","","OTRO-LOTE","A-01-2",1,BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.TEN)));
        assertThrows(IllegalArgumentException.class,()->docs.vincular(documento,otra));
        Long nuevo=docs.crear(List.of(foto(3))); assertThrows(IllegalArgumentException.class,()->docs.vincular(nuevo,compra));
        docs.vincular(nuevo,otra); assertEquals(2,docs.listar("").stream().filter(d->d.compraId()!=null).count());
    }
    @Test void exigeSesionYRolActivoParaLecturaYEscritura() throws Exception {
        Long id=docs.crear(List.of(foto(1))); Long archivo=docs.archivos(id).get(0).id();
        jdbc.update("UPDATE usuarios SET rol='CAJERO' WHERE id=?",admin.getId());
        assertThrows(IllegalStateException.class,()->docs.listar(""));
        assertThrows(IllegalStateException.class,()->docs.descargar(archivo));
        assertThrows(IllegalStateException.class,()->docs.adjuntar(id,List.of(foto(2))));
        jdbc.update("UPDATE usuarios SET rol='ALMACENERO' WHERE id=?",admin.getId()); assertEquals(1,docs.archivos(id).size());
        jdbc.update("UPDATE usuarios SET activo=false WHERE id=?",admin.getId()); assertThrows(IllegalStateException.class,()->docs.listar(""));
        context.getBean(RPLCTerminalSesion.class).setUsuario(null); assertThrows(IllegalStateException.class,()->docs.crear(List.of(foto(3))));
    }
    @Test void dosCajasRespetanElLimiteDeArchivos() throws Exception {
        var iniciales=new ArrayList<ERQZArchivoDua>(); for(int i=0;i<19;i++) iniciales.add(foto(i)); Long id=docs.crear(iniciales);
        var pool=Executors.newFixedThreadPool(2); var inicio=new CountDownLatch(1);
        try {
            Callable<Boolean> uno=()->{inicio.await();try{docs.adjuntar(id,List.of(foto(20)));return true;}catch(IllegalArgumentException e){return false;}};
            Callable<Boolean> dos=()->{inicio.await();try{docs.adjuntar(id,List.of(foto(21)));return true;}catch(IllegalArgumentException e){return false;}};
            var a=pool.submit(uno); var b=pool.submit(dos); inicio.countDown();
            assertNotEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS)); assertEquals(20,docs.archivos(id).size());
        } finally {pool.shutdownNow();}
    }

    DBQPBorradoresDuaServiceI pendientes(){return context.getBean(DBQPBorradoresDuaServiceI.class);}
    List<ERQZCompraServiceI.Linea> varios() {
        return List.of(new ERQZCompraServiceI.Linea("IMP-AUD","Audífonos de ejemplo","","IMP-01","A-01-1",20,new BigDecimal("5"),new BigDecimal("0.9"),new BigDecimal("35")),
                new ERQZCompraServiceI.Linea("IMP-CAR","Cargadores de ejemplo","","IMP-02","B-01-1",15,new BigDecimal("6"),BigDecimal.ONE,new BigDecimal("35")),
                new ERQZCompraServiceI.Linea("IMP-CAB","Cables de ejemplo","","IMP-03","C-01-1",30,new BigDecimal("2"),new BigDecimal("0.3"),new BigDecimal("35")));
    }
    Long guardarEjemplo() throws Exception {
        return pendientes().guardar(null,0,proveedor(),"DUA-EJEMPLO",LocalDate.now(),new BigDecimal("3.75"),varios(),null,List.of(pdf(),foto(1)));
    }
    @Test void registraTresProductosEnBdSinTocarInventarioYLuegoApruebaUnaVez() throws Exception {
        Long id=guardarEjemplo();var d=pendientes().obtener(id);
        assertEquals("PENDIENTE",d.resumen().estado());assertEquals(3,d.lineas().size());assertEquals(65,d.resumen().unidades());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM productos",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM compras_dua",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM movimientos_stock",Integer.class));
        assertEquals(id,docs.listar("DUA-EJEMPLO").get(0).borradorId());
        assertNull(docs.listar("DUA-EJEMPLO").get(0).compraId());
        Long compra=pendientes().aprobar(id,d.resumen().version());
        assertEquals(compra,pendientes().obtener(id).resumen().compraId());
        assertEquals("APROBADA",pendientes().obtener(id).resumen().estado());
        assertEquals(65,jdbc.queryForObject("SELECT sum(stock_actual) FROM productos",Integer.class));
        assertEquals(65,jdbc.queryForObject("SELECT sum(cantidad_disponible) FROM lotes",Integer.class));
        assertEquals(3,jdbc.queryForObject("SELECT count(*) FROM detalle_compra_dua WHERE compra_id=?",Integer.class,compra));
        assertEquals(compra,docs.listar("DUA-EJEMPLO").get(0).compraId());
        assertEquals(admin.getId(),jdbc.queryForObject("SELECT aprobado_por FROM borradores_dua WHERE id=?",Long.class,id));
        assertThrows(IllegalStateException.class,()->pendientes().aprobar(id,0));
        assertThrows(IllegalStateException.class,()->pendientes().eliminar(id,1));
        assertThrows(IllegalStateException.class,()->pendientes().guardar(id,1,d.proveedorId(),"MODIFICADA",LocalDate.now(),BigDecimal.ONE,varios(),d.resumen().documentoId(),List.of()));
        assertThrows(IllegalStateException.class,()->docs.eliminarArchivo(docs.archivos(d.resumen().documentoId()).get(0).id()));
        assertThrows(IllegalStateException.class,()->docs.adjuntar(d.resumen().documentoId(),List.of(foto(5))));
        assertEquals(65,jdbc.queryForObject("SELECT sum(stock_actual) FROM productos",Integer.class));
    }
    @Test void permiteEditarQuitarLineasYEliminarLaDuaPendienteConSusArchivos() throws Exception {
        Long id=guardarEjemplo();var d=pendientes().obtener(id);Long doc=d.resumen().documentoId();
        var cambio=List.of(new ERQZCompraServiceI.Linea("IMP-AUD","Audífonos corregidos","","IMP-EDITADO","A-03-1",12,BigDecimal.TEN,BigDecimal.ONE,BigDecimal.TEN));
        pendientes().guardar(id,0,d.proveedorId(),"DUA-EDITADA",LocalDate.now(),BigDecimal.ONE,cambio,doc,List.of());
        var editada=pendientes().obtener(id);assertEquals(12,editada.resumen().unidades());assertEquals(1,editada.lineas().size());
        assertEquals("Audífonos corregidos",editada.lineas().get(0).descripcion());
        assertThrows(IllegalStateException.class,()->pendientes().eliminar(id,0));
        assertThrows(IllegalStateException.class,()->pendientes().aprobar(id,0));
        docs.eliminarArchivo(docs.archivos(doc).get(1).id());assertEquals(1,docs.archivos(doc).size());
        var actualizado=pendientes().obtener(id);assertTrue(actualizado.resumen().version()>editada.resumen().version());
        pendientes().eliminar(id,actualizado.resumen().version());assertTrue(pendientes().listar("").isEmpty());assertTrue(docs.listar("").isEmpty());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM productos",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM archivos_dua",Integer.class));
    }
    @Test void validaPermisosYEvitaSobrescribirLoEditadoEnOtraCaja() throws Exception {
        Long id=guardarEjemplo();var d=pendientes().obtener(id);
        jdbc.update("UPDATE usuarios SET rol='ALMACENERO' WHERE id=?",admin.getId());assertFalse(pendientes().puedeAprobar());
        assertThrows(IllegalStateException.class,()->pendientes().aprobar(id,0));
        pendientes().guardar(id,0,d.proveedorId(),"DUA-ALMACEN",LocalDate.now(),BigDecimal.ONE,varios(),d.resumen().documentoId(),List.of());
        assertThrows(IllegalStateException.class,()->pendientes().guardar(id,0,d.proveedorId(),"OBSOLETA",LocalDate.now(),BigDecimal.ONE,varios(),d.resumen().documentoId(),List.of()));
        jdbc.update("UPDATE usuarios SET rol='GERENTE' WHERE id=?",admin.getId());assertTrue(pendientes().puedeAprobar());
        jdbc.update("UPDATE usuarios SET activo=false WHERE id=?",admin.getId());assertFalse(pendientes().puedeAprobar());
        assertThrows(IllegalStateException.class,()->pendientes().listar(""));
    }
    @Test void errorEnUnProductoRevierteTodaLaAprobacionYConservaElPendiente() throws Exception {
        Long id=guardarEjemplo();var d=pendientes().obtener(id);
        jdbc.update("UPDATE lineas_borrador_dua SET codigo_barras='REPETIDO' WHERE borrador_id=?",id);
        assertThrows(IllegalArgumentException.class,()->pendientes().aprobar(id,0));
        assertEquals("PENDIENTE",pendientes().obtener(id).resumen().estado());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM productos",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM lotes",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM compras_dua",Integer.class));
        assertNull(docs.listar("").get(0).compraId());assertEquals(2,docs.archivos(d.resumen().documentoId()).size());
    }
    @Test void dosCajasNoApruebanDosVecesLaMismaDua() throws Exception {
        Long id=guardarEjemplo();var pool=Executors.newFixedThreadPool(2);var inicio=new CountDownLatch(1);
        try {
            Callable<Boolean> aprobar=()->{inicio.await();try{pendientes().aprobar(id,0);return true;}catch(IllegalStateException ex){return false;}};
            var a=pool.submit(aprobar);var b=pool.submit(aprobar);inicio.countDown();
            assertNotEquals(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
            assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM compras_dua",Integer.class));
            assertEquals(65,jdbc.queryForObject("SELECT sum(stock_actual) FROM productos",Integer.class));
        }finally{pool.shutdownNow();}
    }
    @Test void documentoNoSePuedeUsarEnDosPendientesNiVincularAntesDeAprobar() throws Exception {
        Long id=guardarEjemplo();var d=pendientes().obtener(id);var documento=d.resumen().documentoId();
        assertThrows(IllegalArgumentException.class,()->pendientes().guardar(null,0,d.proveedorId(),"OTRO",LocalDate.now(),BigDecimal.ONE,varios(),documento,List.of()));
        assertThrows(IllegalArgumentException.class,()->pendientes().guardar(null,0,d.proveedorId(),"dua-ejemplo",LocalDate.now(),BigDecimal.ONE,varios(),null,List.of()));
        Long compra=compras.registrar(proveedor(),"OTRA-COMPRA",LocalDate.now(),BigDecimal.ONE,lineas());
        assertThrows(IllegalStateException.class,()->docs.vincular(documento,compra));
        pendientes().aprobar(id,0);assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM compras_dua",Integer.class));
    }
    @Test void noCambiaProductoExistenteHastaLaAprobacionYSumaElLoteNuevo() {
        Long proveedor=proveedor();compras.registrar(proveedor,"INGRESO-ANTERIOR",LocalDate.now(),BigDecimal.ONE,lineas());
        String nombre=jdbc.queryForObject("SELECT descripcion FROM productos WHERE sku='DOCS-1'",String.class);
        var linea=new ERQZCompraServiceI.Linea("DOCS-1","Nombre corregido","","LOTE-NUEVO","A-04-1",7,BigDecimal.TEN,BigDecimal.ONE,BigDecimal.TEN);
        Long id=pendientes().guardar(null,0,proveedor,"DUA-EXISTENTE",LocalDate.now(),BigDecimal.ONE,List.of(linea),null,List.of());
        assertEquals(nombre,jdbc.queryForObject("SELECT descripcion FROM productos WHERE sku='DOCS-1'",String.class));
        assertEquals(3,jdbc.queryForObject("SELECT stock_actual FROM productos WHERE sku='DOCS-1'",Integer.class));
        pendientes().aprobar(id,0);
        assertEquals(10,jdbc.queryForObject("SELECT stock_actual FROM productos WHERE sku='DOCS-1'",Integer.class));
        assertEquals(10,jdbc.queryForObject("SELECT sum(cantidad_disponible) FROM lotes",Integer.class));
        assertEquals("Nombre corregido",jdbc.queryForObject("SELECT descripcion FROM productos WHERE sku='DOCS-1'",String.class));
    }
    @Test void falloEnAdjuntosNoGuardaCambiosParcialesDelPendiente() throws Exception {
        Long id=guardarEjemplo();var d=pendientes().obtener(id);Long doc=d.resumen().documentoId();
        var imagen=foto(9);
        assertThrows(IllegalArgumentException.class,()->pendientes().guardar(id,0,d.proveedorId(),"NO-GUARDAR",LocalDate.now(),BigDecimal.ONE,lineas(),doc,List.of(imagen,imagen)));
        var despues=pendientes().obtener(id);assertEquals(0,despues.resumen().version());assertEquals(65,despues.resumen().unidades());assertEquals("DUA-EJEMPLO",despues.resumen().dua());
        assertEquals(2,docs.archivos(doc).size());assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM productos",Integer.class));
        assertThrows(IllegalArgumentException.class,()->pendientes().guardar(null,0,d.proveedorId(),"NUEVA-INVALIDA",LocalDate.now(),BigDecimal.ONE,lineas(),null,List.of(imagen,imagen)));
        assertEquals(1,pendientes().listar("").size());assertEquals(1,docs.listar("").size());
    }
    @Test void cualquierRolActivoPuedeVerYEditarSoloSuPerfilSinCambiarPermisos() {
        var servicio=context.getBean(RPLCUsuariosServiceI.class);
        jdbc.update("UPDATE usuarios SET rol='CAJERO' WHERE id=?",admin.getId());
        assertEquals("admin-docs",servicio.miPerfil().usuario());
        var u=servicio.guardarMiPerfil("admin-docs","Nombre actualizado","","");
        assertEquals(admin.getId(),u.getId());assertEquals(Rol.CAJERO,u.getRol());
        assertEquals("Nombre actualizado",servicio.miPerfil().nombre());
        assertThrows(IllegalStateException.class,()->servicio.cambiarRol(admin.getId(),Rol.ADMINISTRADOR));
        jdbc.update("UPDATE usuarios SET activo=false WHERE id=?",admin.getId());
        assertThrows(IllegalStateException.class,servicio::miPerfil);
    }
    @Test void cambioDeClavePropioExigeLaActualYConservaElRol() {
        var servicio=context.getBean(RPLCUsuariosServiceI.class);
        assertThrows(IllegalArgumentException.class,()->servicio.guardarMiPerfil("otro-usuario","Nombre","incorrecta","nueva-clave-segura"));
        assertEquals("admin-docs",servicio.miPerfil().usuario());
        var u=servicio.guardarMiPerfil("mi-usuario","Mi nombre","solo-pruebas","nueva-clave-segura");
        assertEquals(Rol.ADMINISTRADOR,u.getRol());assertTrue(u.getPasswordHash().startsWith("$2"));
        assertEquals(admin.getId(),context.getBean(ERQZAuthService.class).autenticar("mi-usuario","nueva-clave-segura").getId());
        assertThrows(RuntimeException.class,()->context.getBean(ERQZAuthService.class).autenticar("mi-usuario","solo-pruebas"));
    }
}
