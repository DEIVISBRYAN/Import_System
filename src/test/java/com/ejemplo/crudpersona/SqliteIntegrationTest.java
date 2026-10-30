package com.ejemplo.crudpersona;

import com.ejemplo.crudpersona.model.*;
import com.ejemplo.crudpersona.repository.*;
import com.ejemplo.crudpersona.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import java.nio.file.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SqliteIntegrationTest {
    @TempDir static Path folder;
    ConfigurableApplicationContext context;
    JdbcTemplate db;
    long user,supplier,product,purchase,moto,sale;
    String originalHash;
    private void open(){
        context=new SpringApplicationBuilder(RPLCSpringAppConfig.class).run(
                "--app.sqlite.path="+folder.resolve("pruebas.db"),"--spring.config.import=optional:file:./no-config-de-pruebas.properties",
                "--app.database.kind=sqlite","--spring.flyway.locations=classpath:db/sqlite",
                "--spring.jpa.database-platform=org.hibernate.community.dialect.SQLiteDialect","--logging.level.root=WARN");
        db=context.getBean(JdbcTemplate.class);
    }
    @BeforeAll void prepare(){open();}
    @AfterAll void close(){if(context!=null)context.close();}
    ERQZArchivoDua image()throws Exception {
        var image=new java.awt.image.BufferedImage(8,8,java.awt.image.BufferedImage.TYPE_INT_RGB);
        var bytes=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(image,"png",bytes);
        return new ERQZArchivoDua("dua-prueba.png","image/png",bytes.toByteArray());
    }
    @Test @Order(1) void creaArchivoLocalYCuentaSeguraSinServidor()throws Exception {
        assertTrue(Files.isRegularFile(folder.resolve("pruebas.db")));
        assertEquals(1,db.queryForObject("PRAGMA foreign_keys",Integer.class));
        assertEquals("wal",db.queryForObject("PRAGMA journal_mode",String.class));
        String access=Files.readString(folder.resolve("ACCESO_INICIAL.txt"));
        String password=access.lines().filter(l->l.startsWith("Clave inicial: ")).findFirst().orElseThrow().substring(15);
        var admin=context.getBean(ERQZAuthService.class).autenticar("admin",password);
        assertTrue(admin.getPasswordHash().startsWith("$2"));user=admin.getId();originalHash=admin.getPasswordHash();
        context.getBean(RPLCTerminalSesion.class).setUsuario(admin);
        supplier=db.queryForObject("INSERT INTO proveedores(razon_social,estado) VALUES('Proveedor de prueba','ACTIVO') RETURNING id",Long.class);
    }
    @Test @Order(2) void guardaDocumentoBorradorYAprobacionSinDuplicarStock()throws Exception {
        var docs=context.getBean(DBQPDocumentosDuaServiceI.class);long doc=docs.crear(List.of(image()));
        var drafts=context.getBean(DBQPBorradoresDuaServiceI.class);
        long draft=drafts.guardar(null,0,supplier,"DAM-SQLITE",LocalDate.now(),new BigDecimal("3.75"),
                List.of(new ERQZCompraServiceI.Linea("MOTO-SQLITE","Moto prueba","77550001","SQL-LOTE","A-01-1",3,
                        new BigDecimal("100"),new BigDecimal("10"),new BigDecimal("30"))),doc,List.of());
        assertEquals(0,context.getBean(ProductoRepository.class).count());
        var pending=drafts.obtener(draft);assertEquals("PENDIENTE",pending.resumen().estado());
        purchase=drafts.aprobar(draft,pending.resumen().version());
        var p=context.getBean(ProductoRepository.class).findBySku("MOTO-SQLITE").orElseThrow();product=p.getId();
        assertEquals(3,p.getStockActual());
        assertThrows(RuntimeException.class,()->drafts.aprobar(draft,pending.resumen().version()));
        assertEquals(3,context.getBean(ProductoRepository.class).findById(product).orElseThrow().getStockActual());
        assertFalse(docs.listar("DAM-SQLITE").isEmpty());
        var attached=docs.archivos(doc);assertEquals(1,attached.size());
        assertArrayEquals(image().contenido(),docs.descargar(attached.get(0).id()).contenido());
    }
    @Test @Order(3) void vendeFacturaExportaYAnulaConFifo() {
        var box=context.getBean(RPLCCajaServiceI.class);box.abrir(1,new BigDecimal("100"),user);
        var sales=context.getBean(ERQZVentaServiceI.class);
        sale=sales.cobrar(1,user,"BOLETA","12345678","Cliente de prueba","EFECTIVO",List.of(new ERQZVentaServiceI.Linea(product,1,new BigDecimal("500"))));
        assertEquals(2,context.getBean(ProductoRepository.class).findById(product).orElseThrow().getStockActual());
        long receipt=context.getBean(ComprobanteSunatRepository.class).findByVentaId(sale).orElseThrow().getId();
        assertTrue(context.getBean(RPLCComprobantesServiceI.class).generarPdf(receipt).length>100);
        var report=DBQPReporteVentas.crear(context.getBean(VentaRepository.class).findAll(),"Prueba SQLite");
        for(var format:DBQPReporteExportador.Formato.values())assertTrue(DBQPReporteExportador.generar(report,format).length>100);
        sales.anular(sale);assertEquals(3,context.getBean(ProductoRepository.class).findById(product).orElseThrow().getStockActual());
    }
    @Test @Order(4) void registraMotoYExpedienteConArchivo()throws Exception {
        var svc=context.getBean(DBQPSunarpServiceI.class);
        long detail=db.queryForObject("SELECT id FROM detalle_compra_dua WHERE compra_id=?",Long.class,purchase);
        moto=svc.registrar(new DBQPSunarpServiceI.Moto(detail,"LALTE9990T0000001","MOTOR-SQLITE","Marca","Modelo",2026,"Negro"));
        assertEquals(1,svc.motos("lalt").size());
        long file=svc.crearExpediente(moto,new DBQPSunarpServiceI.Tramite("PRIMERA_INSCRIPCION","12345678","Propietario prueba"));
        svc.adjuntar(file,0,"DUA_DAM",image());int version=((Number)svc.expediente(file).get("version")).intValue();
        svc.actualizar(file,new DBQPSunarpServiceI.Seguimiento(version,"PREPARADO","",null,"","Gestor prueba","",true,"","","12345678","Propietario prueba"));
        assertEquals("PREPARADO",svc.expediente(file).get("estado"));
        assertEquals(1,svc.archivos(file).size());assertFalse(svc.historial(moto).isEmpty());
    }
    @Test @Order(5) void generaPedidoRecibeCompletoYConservaTasaManual() {
        var repos=context.getBean(RPLCReposicionServiceI.class);repos.asignarProveedor(product,supplier);
        long order=repos.confirmarPedidos(List.of(new RPLCReposicionServiceI.LineaRevisada(product,supplier,2,BigDecimal.TEN,new BigDecimal("37.5"))),"Prueba local").get(0);
        assertTrue(repos.pdfPedido(order).length>100);assertTrue(repos.excelPedido(order).length>100);assertFalse(repos.listarPedidos().isEmpty());
        var purchases=context.getBean(ERQZCompraServiceI.class);var reception=purchases.prepararRecepcion(order);
        long line=reception.productos().get(0).detalleId();
        purchases.recibirPedido(order,"DAM-RECEPCION-SQLITE",LocalDate.now(),new BigDecimal("3.75"),List.of(new ERQZCompraServiceI.LineaRecepcion(line,2,"LOTE-RECEPCION","B-01-1",BigDecimal.TEN,BigDecimal.ONE,new BigDecimal("30"))));
        assertEquals(5,context.getBean(ProductoRepository.class).findById(product).orElseThrow().getStockActual());
        var rate=context.getBean(DBQPTipoCambioServiceI.class);var before=rate.actual();
        var price=context.getBean(ProductoRepository.class).findById(product).orElseThrow().getPrecioVentaPen();
        rate.guardarManual(before,new BigDecimal("3.90"));
        assertEquals(0,price.compareTo(context.getBean(ProductoRepository.class).findById(product).orElseThrow().getPrecioVentaPen()));
    }
    @Test @Order(6) void persisteTrasReabrirYNoReiniciaLaClave()throws Exception {
        context.close();context=null;open();
        assertEquals(originalHash,context.getBean(UsuarioRepository.class).findById(user).orElseThrow().getPasswordHash());
        assertEquals(5,context.getBean(ProductoRepository.class).findById(product).orElseThrow().getStockActual());
        assertEquals(1,db.queryForObject("SELECT count(*) FROM motos_importadas",Integer.class));
        assertEquals("ok",db.queryForObject("PRAGMA integrity_check",String.class));
        assertTrue(db.queryForList("PRAGMA foreign_key_check").isEmpty());
    }
}
