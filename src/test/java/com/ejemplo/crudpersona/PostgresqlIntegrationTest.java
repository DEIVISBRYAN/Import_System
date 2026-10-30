package com.ejemplo.crudpersona;

import com.ejemplo.crudpersona.model.*;
import com.ejemplo.crudpersona.repository.*;
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
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "TEST_POSTGRESQL", matches = "true")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PostgresqlIntegrationTest {
    private ConfigurableApplicationContext context;
    private String schema, url, user, password;
    private JdbcTemplate jdbc;
    private ProductoRepository productos;
    private LoteRepository lotes;
    private ERQZVentaServiceI ventas;
    private RPLCCajaServiceI cajas;
    private RPLCComprobantesServiceI documentos;

    @BeforeAll void iniciar() throws Exception {
        Properties props = new Properties();
        try (var reader = Files.newBufferedReader(Path.of(".local/database.properties"))) { props.load(reader); }
        url = props.getProperty("spring.datasource.url"); user = props.getProperty("spring.datasource.username"); password = props.getProperty("spring.datasource.password");
        schema = "verificacion_" + UUID.randomUUID().toString().replace("-", "");
        try (var c = DriverManager.getConnection(url, user, password); var s = c.createStatement()) { s.execute("CREATE SCHEMA " + schema); }
        context = new SpringApplicationBuilder(RPLCSpringAppConfig.class).profiles("postgresql").run("--spring.datasource.url=" + url + "?currentSchema=" + schema,
                "--spring.flyway.default-schema=" + schema, "--app.database.import-sqlite=", "--logging.level.root=WARN");
        jdbc = context.getBean(JdbcTemplate.class); productos = context.getBean(ProductoRepository.class); lotes = context.getBean(LoteRepository.class);
        ventas = context.getBean(ERQZVentaServiceI.class); cajas = context.getBean(RPLCCajaServiceI.class); documentos = context.getBean(RPLCComprobantesServiceI.class);
    }
    @AfterAll void terminar() throws Exception {
        if (context != null) context.close();
        if (schema != null && schema.matches("verificacion_[a-f0-9]{32}"))
            try (var c = DriverManager.getConnection(url, user, password); var s = c.createStatement()) { s.execute("DROP SCHEMA " + schema + " CASCADE"); }
    }
    @BeforeEach void limpiar() {
        EsquemaPruebas.limpiar(jdbc,schema,"pedidos_compra","comprobante_detalle","comprobantes_sunat","detalle_venta","ventas","caja_turno","lotes","productos","usuarios","proveedores","compras_dua","tipo_cambio");
        jdbc.update("UPDATE secuencias_comprobante SET siguiente=1");
        context.getBean(RPLCTerminalSesion.class).setUsuario(null);
    }
    private Producto producto(String sku, int cantidad) {
        Producto p = productos.save(Producto.builder().sku(sku).descripcion("Producto " + sku).stockActual(cantidad).stockMinimo(1)
                .precioVentaPen(new BigDecimal("11.80")).costoPromedioPen(new BigDecimal("5")).build());
        lotes.save(Lote.builder().producto(p).codigoLote("L-" + sku).cantidadInicial(cantidad).cantidadDisponible(cantidad)
                .costoUnitarioPen(new BigDecimal("5")).costoUnitarioUsd(new BigDecimal("1.3")).fechaIngreso(LocalDate.now()).ubicacion("A-01-1").build());
        return p;
    }
    private Long cobrar(int caja, Producto p, int cantidad) {
        return ventas.cobrar(caja, null, "BOLETA", "45892134", "Cliente de prueba", "EFECTIVO",
                List.of(new ERQZVentaServiceI.Linea(p.getId(), cantidad, new BigDecimal("11.80"))));
    }
    @Test void tresCajasYVentaAtomica() {
        for (int n = 1; n <= 3; n++) cajas.abrir(n, new BigDecimal("100"), null);
        Producto p = producto("P1", 7);
        for (int n = 1; n <= 3; n++) cobrar(n, p, 1);
        assertEquals(4, productos.findById(p.getId()).orElseThrow().getStockActual());
        var turnos = context.getBean(CajaTurnoRepository.class);
        for (int n = 1; n <= 3; n++) assertEquals(0, turnos.findByNumeroCajaAndEstado(n, "ABIERTA").orElseThrow().getMontoEfectivoVentas().compareTo(new BigDecimal("11.80")));
        var registros = context.getBean(VentaRepository.class).findAll();
        assertEquals(3, registros.stream().map(Venta::getCorrelativo).distinct().count());
        assertEquals(new BigDecimal("10.00"), registros.get(0).getSubtotal());
        assertEquals(new BigDecimal("1.80"), registros.get(0).getIgv());
        var repo = context.getBean(ComprobanteSunatRepository.class);
        assertEquals(3, repo.count()); documentos.sincronizarVentas(); documentos.sincronizarVentas(); assertEquals(3, repo.count());
    }
    @Test void noSobrevendeEntreTerminales() throws Exception {
        for (int n = 1; n <= 3; n++) cajas.abrir(n, BigDecimal.ZERO, null);
        Producto p = producto("ULTIMO", 1);
        ExecutorService pool = Executors.newFixedThreadPool(3);
        CountDownLatch inicio = new CountDownLatch(1);
        List<Future<Boolean>> resultados = new ArrayList<>();
        try {
            for (int n = 1; n <= 3; n++) {
                int caja = n;
                resultados.add(pool.submit(() -> { inicio.await(); try { cobrar(caja, p, 1); return true; } catch (RuntimeException e) { return false; } }));
            }
            inicio.countDown(); int exitos = 0;
            for (var r : resultados) if (r.get(30, TimeUnit.SECONDS)) exitos++;
            assertEquals(1, exitos); assertEquals(0, productos.findById(p.getId()).orElseThrow().getStockActual());
            assertEquals(1, context.getBean(VentaRepository.class).count());
            assertEquals(2, jdbc.queryForObject("SELECT siguiente FROM secuencias_comprobante WHERE serie='B001'", Integer.class));
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(5, TimeUnit.SECONDS)); }
    }
    @Test void fallaRevierteStockCajaVentaYCorrelativo() {
        cajas.abrir(1, BigDecimal.ZERO, null);
        Producto suficiente = producto("SI", 5), insuficiente = producto("NO", 0);
        assertThrows(RuntimeException.class, () -> ventas.cobrar(1, null, "BOLETA", "", "", "EFECTIVO", List.of(
                new ERQZVentaServiceI.Linea(suficiente.getId(), 2, new BigDecimal("11.80")),
                new ERQZVentaServiceI.Linea(insuficiente.getId(), 1, new BigDecimal("11.80")))));
        assertEquals(5, productos.findById(suficiente.getId()).orElseThrow().getStockActual());
        assertEquals(5, lotes.findByProductoIdOrderByFechaIngresoAsc(suficiente.getId()).get(0).getCantidadDisponible());
        assertEquals(0, context.getBean(VentaRepository.class).count());
        assertEquals(1, jdbc.queryForObject("SELECT siguiente FROM secuencias_comprobante WHERE serie='B001'", Integer.class));
        assertEquals(0, context.getBean(CajaTurnoRepository.class).findByNumeroCajaAndEstado(1, "ABIERTA").orElseThrow().getMontoEfectivoVentas().signum());
    }
    @Test void anularUnaSolaVezYCierreAislado() {
        var turno1 = cajas.abrir(1, new BigDecimal("100"), null); cajas.abrir(2, new BigDecimal("200"), null);
        Producto p = producto("DEV", 3); Long id = cobrar(1, p, 2);
        ventas.anular(id); ventas.anular(id);
        assertEquals(3, productos.findById(p.getId()).orElseThrow().getStockActual());
        assertEquals("ANULADA", context.getBean(ComprobanteSunatRepository.class).findByVentaId(id).orElseThrow().getEstadoSunat());
        assertEquals(0, cajas.cerrar(1, turno1.getId(), new BigDecimal("100")).getDiferencia().signum());
        assertTrue(context.getBean(CajaTurnoRepository.class).findByNumeroCajaAndEstado(2, "ABIERTA").isPresent());
        assertThrows(RuntimeException.class, () -> cobrar(1, p, 1));
    }
    @Test void pdfPersistidoYMultipagina() throws Exception {
        cajas.abrir(1, BigDecimal.ZERO, null); Long venta = cobrar(1, producto("PDF", 1), 1);
        Long id = documentos.registrarVenta(venta); byte[] bytes = documentos.generarPdf(id);
        assertArrayEquals(bytes, documentos.generarPdf(id));
        assertTrue(bytes.length > 1000); assertEquals("%PDF", new String(bytes, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        assertNotNull(context.getBean(ComprobanteSunatRepository.class).findById(id).orElseThrow().getPdfDocumento());
        Path dir = Path.of("target/pdf-check"); Files.createDirectories(dir); Files.write(dir.resolve("comprobante.pdf"), bytes);
        var lineas = java.util.stream.IntStream.rangeClosed(1, 150).mapToObj(n -> new ComprobanteDocumento.Linea(1,
                "Articulo " + n + " - Audifonos, cargador y accesorios de importacion", BigDecimal.TEN, BigDecimal.TEN)).toList();
        var muestra = new ComprobanteDocumento("F001-000150", "FACTURA", LocalDateTime.now(), "Importadora Peru", "", "Lima",
                "Cliente de verificacion", "20554433221", "TARJETA", new BigDecimal("1271.19"), new BigDecimal("228.81"),
                new BigDecimal("1500"), "PENDIENTE", lineas);
        Files.write(dir.resolve("multipagina.pdf"), DBQPComprobantePdf.generar(muestra));
    }
    @Test void conservaAceptacionPreviaYSuReferencia() {
        cajas.abrir(1, BigDecimal.ZERO, null); Long venta = cobrar(1, producto("CDR", 2), 1);
        var repo = context.getBean(ComprobanteSunatRepository.class);
        var c = repo.findByVentaId(venta).orElseThrow();
        c.setVentaId(null); c.setEstadoSunat("ACEPTADO"); c.setHashCpe("REFERENCIA-CDR-DE-PRUEBA"); repo.save(c);
        assertThrows(RuntimeException.class, () -> ventas.anular(venta));
        Long id = documentos.registrarVenta(venta);
        var vinculado = repo.findById(id).orElseThrow();
        assertEquals("ACEPTADO", vinculado.getEstadoSunat());
        assertEquals("REFERENCIA-CDR-DE-PRUEBA", vinculado.getHashCpe());
        assertEquals(venta, vinculado.getVentaId());
    }
    @Test void usuariosNormalizadosClaveSeguraYAdministradorProtegido() {
        var repo = context.getBean(UsuarioRepository.class);
        var administrador = repo.save(Usuario.builder().username("Administrador").nombreCompleto("Prueba admin")
                .passwordHash("clave-legacy").rol(Rol.ADMINISTRADOR).activo(true).build());
        var auth = context.getBean(ERQZAuthService.class);
        assertEquals(administrador.getId(), auth.autenticar(" ADMINISTRADOR ", "clave-legacy").getId());
        context.getBean(RPLCTerminalSesion.class).setUsuario(administrador);
        var servicio = context.getBean(RPLCUsuariosServiceI.class);
        var cajero = servicio.crear(" CAJERO.Prueba ", "Caja prueba", "prueba-temporal", Rol.CAJERO);
        assertEquals("cajero.prueba", cajero.getUsername());
        assertTrue(cajero.getPasswordHash().startsWith("$2"));
        assertEquals(cajero.getId(), auth.autenticar("CAJERO.PRUEBA", "prueba-temporal").getId());
        assertThrows(RuntimeException.class, () -> auth.autenticar("cajero.prueba", "incorrecta"));
        assertThrows(RuntimeException.class, () -> servicio.crear("CAJERO.PRUEBA", "Repetido", "otra", Rol.CAJERO));
        servicio.cambiarClave(cajero.getId(), "clave-nueva");
        assertThrows(RuntimeException.class, () -> auth.autenticar("cajero.prueba", "prueba-temporal"));
        auth.autenticar("cajero.prueba", "clave-nueva");
        servicio.cambiarEstado(cajero.getId());
        assertThrows(RuntimeException.class, () -> auth.autenticar("cajero.prueba", "clave-nueva"));
        assertThrows(RuntimeException.class, () -> servicio.cambiarEstado(administrador.getId()));
        assertThrows(RuntimeException.class, () -> servicio.cambiarRol(administrador.getId(), Rol.CAJERO));
    }
    @Test void tipoCambioRecalculaDeVerdadYConservaHistorial() {
        Producto p = producto("TC", 5);
        var servicio = context.getBean(DBQPTipoCambioServiceI.class);
        assertEquals(0, new BigDecimal("3.75").compareTo(servicio.actual()));
        servicio.aplicar(new BigDecimal("3.75"), new BigDecimal("4.00"));
        assertEquals(0, new BigDecimal("12.59").compareTo(productos.findById(p.getId()).orElseThrow().getPrecioVentaPen()));
        var historia = context.getBean(HistorialPrecioRepository.class).findAll();
        assertEquals(1, historia.size()); assertEquals(0, historia.get(0).getDiferencia().compareTo(new BigDecimal("0.79")));
        assertThrows(RuntimeException.class, () -> servicio.aplicar(new BigDecimal("3.75"), new BigDecimal("5")));
        assertEquals(1, context.getBean(HistorialPrecioRepository.class).count());
        assertThrows(RuntimeException.class, () -> servicio.aplicar(new BigDecimal("4"), BigDecimal.ZERO));
    }
    @Test void tipoCambioManualSoloActualizaDolarYNoModificaStockPreciosNiVentas() {
        Producto p = producto("MANUAL", 5);
        var servicio = context.getBean(DBQPTipoCambioServiceI.class);
        var hoy = LocalDate.now(java.time.ZoneId.of("America/Lima"));
        servicio.guardarManual(new BigDecimal("3.75"), new BigDecimal("3.43"));
        servicio.guardarManual(new BigDecimal("3.43"), new BigDecimal("3.43"));
        assertEquals(0, new BigDecimal("3.43").compareTo(servicio.actual()));
        assertEquals(0, new BigDecimal("11.80").compareTo(productos.findById(p.getId()).orElseThrow().getPrecioVentaPen()));
        assertEquals(5, productos.findById(p.getId()).orElseThrow().getStockActual());
        assertEquals(0, context.getBean(HistorialPrecioRepository.class).count());
        assertEquals(1, context.getBean(TipoCambioRepository.class).count());
        assertEquals("MANUAL", context.getBean(TipoCambioRepository.class).findByFecha(hoy).orElseThrow().getOrigen());
        assertThrows(RuntimeException.class, () -> servicio.guardarManual(new BigDecimal("3.75"), new BigDecimal("3.50")));
        assertThrows(RuntimeException.class, () -> servicio.guardarManual(new BigDecimal("3.43"), BigDecimal.ZERO));
        assertThrows(RuntimeException.class, () -> servicio.guardarManual(new BigDecimal("3.43"), new BigDecimal("3.12345")));
    }
    private long motoSunarp(){
        var user=context.getBean(UsuarioRepository.class).save(Usuario.builder().username("gestor-prueba").nombreCompleto("Gestor de prueba").passwordHash("solo-pruebas").activo(true).rol(Rol.ADMINISTRADOR).build());
        context.getBean(RPLCTerminalSesion.class).setUsuario(user);
        long supplier=jdbc.queryForObject("INSERT INTO proveedores(razon_social) VALUES ('Proveedor motos de prueba') RETURNING id",Long.class);
        long purchase=context.getBean(ERQZCompraServiceI.class).registrar(supplier,"DAM-PRUEBA-MOTOS",LocalDate.now(),new BigDecimal("3.75"),List.of(new ERQZCompraServiceI.Linea("MOTO-REG","Moto nueva de prueba","","MOTO-LOTE","A-01-1",2,new BigDecimal("500"),new BigDecimal("20"),new BigDecimal("30"))));
        long detail=jdbc.queryForObject("SELECT id FROM detalle_compra_dua WHERE compra_id=?",Long.class,purchase);
        return context.getBean(DBQPSunarpServiceI.class).registrar(new DBQPSunarpServiceI.Moto(detail,"LALTE9990R0000001","MOTOR-001","Marca demo","Modelo demo",2026,"Negro"));
    }
    private DBQPSunarpServiceI.Seguimiento seguimiento(DBQPSunarpServiceI svc,long expediente,String state){var e=svc.expediente(expediente);return new DBQPSunarpServiceI.Seguimiento(((Number)e.get("version")).intValue(),state,"TITULO-DEMO",2026,"Oficina de prueba","Gestor autorizado de prueba","Prueba de seguimiento interno, sin envio oficial",true,"ABC-123","PARTIDA-DEMO",e.get("propietario_documento").toString(),e.get("propietario_nombre").toString());}
    private void adjunto(DBQPSunarpServiceI svc,long expediente,String category)throws Exception{svc.adjuntar(expediente,((Number)svc.expediente(expediente).get("version")).intValue(),category,new ERQZArchivoDua(category+".pdf","application/pdf",ArchivoDuaTest.pdf(category+" expediente "+expediente+" ejemplo sin validez")));}
    @Test void motosSeIndividualizanSinDuplicarStockNiVin(){
        long moto=motoSunarp();var svc=context.getBean(DBQPSunarpServiceI.class);var m=svc.motos("LALTE9990R0000001").get(0);long detail=((Number)m.get("detalle_compra_id")).longValue();
        assertEquals(2,jdbc.queryForObject("SELECT stock_actual FROM productos WHERE sku='MOTO-REG'",Integer.class));
        assertThrows(IllegalArgumentException.class,()->svc.registrar(new DBQPSunarpServiceI.Moto(detail,"LALTE9990R0000001","DUP","Demo","Demo",2026,"Negro")));
        svc.editar(moto,0,new DBQPSunarpServiceI.Moto(detail,"LALTE9990R0000001","MOTOR-001-CORREGIDO","Demo","Demo",2026,"Negro"));
        svc.registrar(new DBQPSunarpServiceI.Moto(detail,"LALTE9990R0000002","MOTOR-002","Demo","Demo",2026,"Blanco"));
        assertThrows(IllegalArgumentException.class,()->svc.registrar(new DBQPSunarpServiceI.Moto(detail,"LALTE9990R0000003","EXCESO","Demo","Demo",2026,"Negro")));
        assertEquals(2,jdbc.queryForObject("SELECT stock_actual FROM productos WHERE sku='MOTO-REG'",Integer.class));
    }
    @Test void primeraInscripcionYTransferenciaExigenSustentoYConservanHistoria()throws Exception{
        long moto=motoSunarp();var svc=context.getBean(DBQPSunarpServiceI.class);
        assertThrows(IllegalStateException.class,()->svc.crearExpediente(moto,new DBQPSunarpServiceI.Tramite("TRANSFERENCIA","87654321","Comprador")));
        long first=svc.crearExpediente(moto,new DBQPSunarpServiceI.Tramite("PRIMERA_INSCRIPCION","12345678","Propietario inicial"));
        assertThrows(IllegalArgumentException.class,()->svc.actualizar(first,seguimiento(svc,first,"PREPARADO")));
        adjunto(svc,first,"DUA_DAM");svc.actualizar(first,seguimiento(svc,first,"PREPARADO"));
        assertThrows(IllegalArgumentException.class,()->svc.actualizar(first,seguimiento(svc,first,"PRESENTADO")));
        adjunto(svc,first,"CARGO_PRESENTACION");svc.actualizar(first,seguimiento(svc,first,"PRESENTADO"));
        assertThrows(IllegalArgumentException.class,()->svc.actualizar(first,seguimiento(svc,first,"INSCRITO")));
        adjunto(svc,first,"CONSTANCIA_INSCRIPCION");svc.actualizar(first,seguimiento(svc,first,"INSCRITO"));
        assertEquals("12345678",svc.motos("").get(0).get("titular_documento"));
        assertThrows(IllegalStateException.class,()->svc.actualizar(first,seguimiento(svc,first,"BORRADOR")));
        assertThrows(IllegalArgumentException.class,()->svc.crearExpediente(moto,new DBQPSunarpServiceI.Tramite("TRANSFERENCIA","12345678","Mismo titular")));
        long transfer=svc.crearExpediente(moto,new DBQPSunarpServiceI.Tramite("TRANSFERENCIA","87654321","Nuevo propietario"));
        adjunto(svc,transfer,"ACTA_TRANSFERENCIA");svc.actualizar(transfer,seguimiento(svc,transfer,"PREPARADO"));adjunto(svc,transfer,"CARGO_PRESENTACION");svc.actualizar(transfer,seguimiento(svc,transfer,"PRESENTADO"));adjunto(svc,transfer,"CONSTANCIA_INSCRIPCION");svc.actualizar(transfer,seguimiento(svc,transfer,"INSCRITO"));
        assertEquals("87654321",svc.motos("").get(0).get("titular_documento"));assertEquals("12345678",svc.expediente(transfer).get("vendedor_documento"));assertEquals(2,svc.expedientes(moto).size());assertTrue(svc.historial(moto).size()>=12);
        assertEquals(2,jdbc.queryForObject("SELECT stock_actual FROM productos WHERE sku='MOTO-REG'",Integer.class));
    }
    @Test void expedientesRechazanCambiosViejosYUsuariosSinPermiso(){
        long moto=motoSunarp();var svc=context.getBean(DBQPSunarpServiceI.class);long expediente=svc.crearExpediente(moto,new DBQPSunarpServiceI.Tramite("PRIMERA_INSCRIPCION","12345678","Propietario inicial"));var stale=seguimiento(svc,expediente,"BORRADOR");svc.actualizar(expediente,stale);assertThrows(IllegalStateException.class,()->svc.actualizar(expediente,stale));
        assertEquals("ABC-123",svc.expediente(expediente).get("placa"));assertEquals("",svc.motos("").get(0).get("placa"));
        long user=context.getBean(RPLCTerminalSesion.class).usuarioId();jdbc.update("UPDATE usuarios SET rol='CONTADOR' WHERE id=?",user);assertFalse(svc.puedeEditar());assertEquals(1,svc.motos("").size());assertThrows(IllegalStateException.class,()->svc.crearExpediente(moto,new DBQPSunarpServiceI.Tramite("TRANSFERENCIA","87654321","Comprador")));
        jdbc.update("UPDATE usuarios SET activo=false WHERE id=?",user);assertThrows(IllegalStateException.class,()->svc.motos(""));
    }
    @Test void ajustesYUbicacionesConservanStockMotivoYRechazanDatosViejos() {
        var servicio = context.getBean(ERQZStockServiceI.class);
        Producto p = producto("AJUSTE", 5);
        Long lote = lotes.findByProductoIdOrderByFechaIngresoAsc(p.getId()).get(0).getId();
        assertEquals(3, servicio.ajustar(p.getId(), lote, -2, "Merma de prueba"));
        assertEquals(3, lotes.findById(lote).orElseThrow().getCantidadDisponible());
        assertEquals("Merma de prueba", jdbc.queryForObject("SELECT motivo FROM movimientos_stock LIMIT 1", String.class));
        assertThrows(RuntimeException.class, () -> servicio.editarLote(lote, 5, 4, "B-01-1"));
        assertThrows(RuntimeException.class, () -> servicio.ajustar(p.getId(), lote, -9, "Inválido"));
        servicio.editarLote(lote, 3, 1, "B-02-2");
        assertEquals(1, productos.findById(p.getId()).orElseThrow().getStockActual());
        assertEquals("B-02-2", lotes.findById(lote).orElseThrow().getUbicacion());
        servicio.moverLote(lote, "C-03-1");
        assertEquals(1, productos.findById(p.getId()).orElseThrow().getStockActual());
        servicio.vaciar(Map.of(lote, 1));
        assertEquals(0, productos.findById(p.getId()).orElseThrow().getStockActual());
        assertEquals(2, servicio.ajustar(p.getId(), lote, 2, "Reposición física"));
    }
    @Test void productoYComprasSonAtomicosYSinDuplicados() {
        var stock = context.getBean(ERQZStockServiceI.class);
        Producto invalido = Producto.builder().sku("ROTO").descripcion("No debe guardarse").stockMinimo(1)
                .precioVentaPen(BigDecimal.TEN).costoPromedioPen(BigDecimal.ONE).build();
        assertThrows(RuntimeException.class, () -> stock.guardarProducto(invalido, "L1", "ubicación incorrecta", 2, BigDecimal.ONE));
        assertEquals(0, productos.count());
        Long proveedor = jdbc.queryForObject("INSERT INTO proveedores(razon_social,estado) VALUES ('Proveedor prueba','ACTIVO') RETURNING id", Long.class);
        var compras = context.getBean(ERQZCompraServiceI.class);
        var bien = new ERQZCompraServiceI.Linea("COMPRA", "Producto importado", "991001", "L-C1", "A-01-1", 4,
                new BigDecimal("5"), BigDecimal.ONE, new BigDecimal("35"));
        var mal = new ERQZCompraServiceI.Linea("COMPRA2", "Producto repetido", "991001", "L-C2", "A-01-1", 2,
                BigDecimal.TEN, BigDecimal.ZERO, new BigDecimal("35"));
        assertThrows(RuntimeException.class, () -> compras.registrar(proveedor, "DUA-FALLA", LocalDate.now(), new BigDecimal("3.75"), List.of(bien, mal)));
        assertEquals(0, productos.count());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM compras_dua", Integer.class));
        Long id = compras.registrar(proveedor, "DUA-OK", LocalDate.now(), new BigDecimal("3.75"), List.of(bien));
        assertNotNull(id);
        Producto p = productos.findBySku("COMPRA").orElseThrow();
        assertEquals(4, p.getStockActual()); assertEquals(0, new BigDecimal("30.38").compareTo(p.getPrecioVentaPen()));
        assertEquals(1, lotes.count());
        assertThrows(RuntimeException.class, () -> compras.registrar(proveedor, "DUA-OK", LocalDate.now(), new BigDecimal("3.75"), List.of(bien)));
        assertEquals(4, productos.findById(p.getId()).orElseThrow().getStockActual());
        var segundo = new ERQZCompraServiceI.Linea("compra", "Producto importado", "991001", "L-C3", "A-02-1", 2,
                new BigDecimal("7"), BigDecimal.ONE, new BigDecimal("35"));
        compras.registrar(proveedor, "DUA-OTRA", LocalDate.now(), new BigDecimal("3.75"), List.of(segundo));
        assertEquals(6, productos.findById(p.getId()).orElseThrow().getStockActual());
        assertEquals(6, lotes.findByProductoIdOrderByFechaIngresoAsc(p.getId()).stream().mapToInt(Lote::getCantidadDisponible).sum());
    }
    @Test void facturaYPdfDescargadoContienenLosDatosYNoSobrescribenConErrores() throws Exception {
        cajas.abrir(1, BigDecimal.ZERO, null); Producto p = producto("FACTURA", 2);
        Long venta = ventas.cobrar(1, null, "FACTURA", "20554433221", "Cliente empresa", "TARJETA",
                List.of(new ERQZVentaServiceI.Linea(p.getId(), 1, new BigDecimal("11.80"))));
        Long id = documentos.registrarVenta(venta); byte[] pdf = documentos.generarPdf(id);
        Path destino = Path.of("target/pdf-check/descarga-factura.pdf"); Files.createDirectories(destino.getParent());
        com.ejemplo.crudpersona.ui.PdfDescarga.guardarEn(destino, pdf);
        assertArrayEquals(pdf, Files.readAllBytes(destino));
        assertThrows(java.io.IOException.class, () -> com.ejemplo.crudpersona.ui.PdfDescarga.guardarEn(destino, new byte[]{1, 2}));
        assertArrayEquals(pdf, Files.readAllBytes(destino));
        var reader = new com.lowagie.text.pdf.PdfReader(pdf);
        try {
            String texto = new com.lowagie.text.pdf.parser.PdfTextExtractor(reader).getTextFromPage(1);
            assertTrue(texto.contains("F001-000001")); assertTrue(texto.contains("20554433221")); assertTrue(texto.contains("11.80"));
        } finally { reader.close(); }
    }
    @Test void reposicionUsaProveedoresRealesYNoDuplicaPedidos() {
        Producto p = producto("REP", 0); var servicio = context.getBean(RPLCReposicionServiceI.class);
        assertEquals("", servicio.proveedorHabitual(p.getId())); assertEquals(1, servicio.urgentes());
        Long proveedor = jdbc.queryForObject("INSERT INTO proveedores(razon_social,estado) VALUES ('Proveedor real','ACTIVO') RETURNING id", Long.class);
        servicio.guardarParametros(p.getId(), BigDecimal.ONE, 30, 10, proveedor);
        assertEquals(1, servicio.urgentes());
        var pedido = new RPLCReposicionServiceI.Pedido(p.getId(), 70, "Proveedor real", BigDecimal.TEN, new BigDecimal("37.50"));
        assertEquals(1, servicio.generar(List.of(pedido))); assertEquals(0, servicio.generar(List.of(pedido)));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM pedidos_reposicion", Integer.class));
        assertThrows(RuntimeException.class, () -> servicio.generar(List.of(new RPLCReposicionServiceI.Pedido(p.getId(), 4, "", BigDecimal.ONE, BigDecimal.ONE))));
    }
    @Test void asignacionDeProveedorPersisteSinCambiarParametrosNiStock() {
        Producto p = producto("ASIGNACION", 8);
        var servicio = context.getBean(RPLCReposicionServiceI.class);
        Long primero = jdbc.queryForObject("INSERT INTO proveedores(razon_social,ruc,estado) VALUES ('Proveedor A','20111111111','ACTIVO') RETURNING id", Long.class);
        Long segundo = jdbc.queryForObject("INSERT INTO proveedores(razon_social,ruc,estado) VALUES ('Proveedor B','20222222222','ACTIVO') RETURNING id", Long.class);
        Long inactivo = jdbc.queryForObject("INSERT INTO proveedores(razon_social,estado) VALUES ('Proveedor inactivo','INACTIVO') RETURNING id", Long.class);
        assertNull(servicio.proveedorAsignado(p.getId())); assertEquals(1, servicio.urgentes());
        assertEquals(2, servicio.proveedoresActivos().size());
        servicio.asignarProveedor(p.getId(), primero);
        assertEquals(primero, servicio.proveedorAsignado(p.getId()).id());
        assertNull(jdbc.queryForObject("SELECT ventas_dia FROM reposicion_config WHERE producto_id=?", BigDecimal.class, p.getId()));
        servicio.guardarParametros(p.getId(), new BigDecimal("1.5"), 7, 2, primero);
        servicio.asignarProveedor(p.getId(), segundo);
        assertEquals(segundo, servicio.proveedorAsignado(p.getId()).id());
        assertEquals(7, jdbc.queryForObject("SELECT dias_entrega FROM reposicion_config WHERE producto_id=?", Integer.class, p.getId()));
        assertEquals(2, jdbc.queryForObject("SELECT stock_seguridad FROM reposicion_config WHERE producto_id=?", Integer.class, p.getId()));
        assertEquals(0, new BigDecimal("1.5").compareTo(jdbc.queryForObject("SELECT ventas_dia FROM reposicion_config WHERE producto_id=?", BigDecimal.class, p.getId())));
        jdbc.update("UPDATE proveedores SET razon_social='Proveedor B actualizado' WHERE id=?", segundo);
        assertEquals("Proveedor B actualizado", servicio.proveedorAsignado(p.getId()).nombre());
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update("DELETE FROM proveedores WHERE id=?", segundo));
        assertThrows(RuntimeException.class, () -> servicio.asignarProveedor(p.getId(), inactivo));
        assertThrows(RuntimeException.class, () -> servicio.asignarProveedor(p.getId(), null));
        assertThrows(RuntimeException.class, () -> servicio.guardarParametros(p.getId(), BigDecimal.ONE.negate(), 3, 2, primero));
        assertEquals(segundo, servicio.proveedorAsignado(p.getId()).id());
        assertEquals(8, productos.findById(p.getId()).orElseThrow().getStockActual());
        jdbc.update("UPDATE proveedores SET estado='INACTIVO' WHERE id=?", segundo);
        assertNull(servicio.proveedorAsignado(p.getId())); assertEquals(1, servicio.urgentes());
        servicio.guardarParametros(p.getId(), BigDecimal.ONE, 30, 10, null);
        assertNull(servicio.proveedorAsignado(p.getId()));
    }
    @Test void pedidosAgrupadosConCantidadesRevisadasPersistenPdfYExcel() throws Exception {
        var servicio=context.getBean(RPLCReposicionServiceI.class);
        Long a=jdbc.queryForObject("INSERT INTO proveedores(razon_social,ruc,estado) VALUES ('Proveedor agrupado','20123456789','ACTIVO') RETURNING id",Long.class);
        Long b=jdbc.queryForObject("INSERT INTO proveedores(razon_social,estado) VALUES ('Otro proveedor','ACTIVO') RETURNING id",Long.class);
        var p=producto("GRUPO-1",8); var q=producto("GRUPO-2",2); var r=producto("GRUPO-3",0);
        servicio.asignarProveedor(p.getId(),a); servicio.asignarProveedor(q.getId(),a); servicio.asignarProveedor(r.getId(),b);
        var lineas=List.of(new RPLCReposicionServiceI.LineaRevisada(p.getId(),a,17,new BigDecimal("1.333333"),new BigDecimal("5")),
                new RPLCReposicionServiceI.LineaRevisada(q.getId(),a,3,new BigDecimal("2.5"),new BigDecimal("9.375")),
                new RPLCReposicionServiceI.LineaRevisada(r.getId(),b,5,BigDecimal.ONE,new BigDecimal("3.75")));
        var ids=servicio.confirmarPedidos(lineas,"Entregar en almacén principal");
        assertEquals(2,ids.size()); assertEquals(2,servicio.listarPedidos().size());
        var doc=servicio.documentoPedido(ids.get(0)); assertEquals(2,doc.lineas().size()); assertEquals(17,doc.lineas().get(0).cantidad());
        assertEquals(0,new BigDecimal("30.17").compareTo(doc.totalUsd())); assertEquals(0,new BigDecimal("113.13").compareTo(doc.totalPen()));
        assertEquals(8,productos.findById(p.getId()).orElseThrow().getStockActual());
        assertThrows(IllegalArgumentException.class,()->servicio.confirmarPedidos(lineas,"Duplicado"));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM pedidos_compra",Integer.class));
        jdbc.update("UPDATE proveedores SET razon_social='Nombre nuevo' WHERE id=?",a);
        jdbc.update("UPDATE productos SET descripcion='Producto cambiado',costo_promedio_pen=999 WHERE id=?",p.getId());
        assertEquals("Proveedor agrupado",servicio.documentoPedido(doc.id()).proveedor());
        assertEquals("Producto GRUPO-1",servicio.documentoPedido(doc.id()).lineas().get(0).producto());
        byte[] pdf=servicio.pdfPedido(doc.id()), xlsx=servicio.excelPedido(doc.id());
        assertArrayEquals(pdf,servicio.pdfPedido(doc.id())); assertArrayEquals(xlsx,servicio.excelPedido(doc.id()));
        assertArrayEquals(pdf,jdbc.queryForObject("SELECT pdf_documento FROM pedidos_compra WHERE id=?",byte[].class,doc.id()));
        assertArrayEquals(xlsx,jdbc.queryForObject("SELECT excel_documento FROM pedidos_compra WHERE id=?",byte[].class,doc.id()));
        var reader=new com.lowagie.text.pdf.PdfReader(pdf);
        try { String text=new com.lowagie.text.pdf.parser.PdfTextExtractor(reader).getTextFromPage(1);
            assertTrue(text.contains("PC-000001")); assertTrue(text.contains("Proveedor agrupado")); assertTrue(text.contains("30.17")); assertTrue(text.contains("113.13"));
        } finally {reader.close();}
        try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(xlsx))) {
            var sheet=book.getSheetAt(0);
            assertEquals(17,sheet.getRow(9).getCell(2).getNumericCellValue());
            assertEquals("ROUND(C10*D10,2)",sheet.getRow(9).getCell(4).getCellFormula());
            assertEquals(30.17,sheet.getRow(11).getCell(4).getNumericCellValue(),.001);
            assertEquals(113.13,sheet.getRow(11).getCell(6).getNumericCellValue(),.001);
        }
        var output=Path.of("target/pedidos-preview"); Files.createDirectories(output);
        Files.write(output.resolve("pedido.pdf"),pdf); Files.write(output.resolve("pedido.xlsx"),xlsx);
    }
    @Test void pedidoRechazaCambiosDeProveedorYRevierteTodo() {
        var servicio=context.getBean(RPLCReposicionServiceI.class);
        Long a=jdbc.queryForObject("INSERT INTO proveedores(razon_social,estado) VALUES ('A','ACTIVO') RETURNING id",Long.class);
        Long b=jdbc.queryForObject("INSERT INTO proveedores(razon_social,estado) VALUES ('B','ACTIVO') RETURNING id",Long.class);
        var p=producto("REV-1",1); var q=producto("REV-2",2); servicio.asignarProveedor(p.getId(),a); servicio.asignarProveedor(q.getId(),a);
        var primera=new RPLCReposicionServiceI.LineaRevisada(p.getId(),a,10,BigDecimal.ONE,BigDecimal.TEN);
        var segunda=new RPLCReposicionServiceI.LineaRevisada(q.getId(),a,5,BigDecimal.ONE,BigDecimal.TEN);
        servicio.asignarProveedor(q.getId(),b);
        assertThrows(IllegalArgumentException.class,()->servicio.confirmarPedidos(List.of(primera,segunda),""));
        assertTrue(servicio.listarPedidos().isEmpty());
        assertThrows(IllegalArgumentException.class,()->servicio.confirmarPedidos(List.of(primera,primera),""));
        assertThrows(IllegalArgumentException.class,()->servicio.confirmarPedidos(List.of(new RPLCReposicionServiceI.LineaRevisada(p.getId(),a,0,BigDecimal.ONE,BigDecimal.TEN)),""));
        jdbc.update("UPDATE proveedores SET estado='INACTIVO' WHERE id=?",a);
        assertThrows(IllegalArgumentException.class,()->servicio.confirmarPedidos(List.of(primera),""));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM pedidos_reposicion",Integer.class));
        assertEquals(1,productos.findById(p.getId()).orElseThrow().getStockActual());
    }
    @Test void dosTerminalesNoDuplicanElPedidoPendiente() throws Exception {
        var servicio=context.getBean(RPLCReposicionServiceI.class); var p=producto("PED-CONCURRENTE",5);
        Long a=jdbc.queryForObject("INSERT INTO proveedores(razon_social,estado) VALUES ('A','ACTIVO') RETURNING id",Long.class);
        servicio.asignarProveedor(p.getId(),a);
        var lineas=List.of(new RPLCReposicionServiceI.LineaRevisada(p.getId(),a,6,BigDecimal.ONE,BigDecimal.TEN));
        var pool=Executors.newFixedThreadPool(2); var inicio=new CountDownLatch(1);
        try {
            Callable<Boolean> crear=()->{inicio.await(); try {servicio.confirmarPedidos(lineas,"");return true;} catch(IllegalArgumentException e){return false;}};
            var uno=pool.submit(crear);var dos=pool.submit(crear); inicio.countDown();
            assertNotEquals(uno.get(20,TimeUnit.SECONDS),dos.get(20,TimeUnit.SECONDS));
            assertEquals(1,servicio.listarPedidos().size());
        } finally {pool.shutdownNow();}
    }
    @Test void recibirPedidoCompletoConectaCompraLotesStockYUsuarioUnaSolaVez() throws Exception {
        var servicio=context.getBean(RPLCReposicionServiceI.class); var compras=context.getBean(ERQZCompraServiceI.class);
        Long proveedor=jdbc.queryForObject("INSERT INTO proveedores(razon_social,estado) VALUES ('Proveedor recepción','ACTIVO') RETURNING id",Long.class);
        var p=producto("REC-1",2); var q=producto("REC-2",1);
        servicio.asignarProveedor(p.getId(),proveedor); servicio.asignarProveedor(q.getId(),proveedor);
        Long pedido=servicio.confirmarPedidos(List.of(
                new RPLCReposicionServiceI.LineaRevisada(p.getId(),proveedor,7,BigDecimal.ONE,new BigDecimal("3.75")),
                new RPLCReposicionServiceI.LineaRevisada(q.getId(),proveedor,3,BigDecimal.ONE,new BigDecimal("3.75"))),"").get(0);
        var preparacion=compras.prepararRecepcion(pedido); assertEquals(2,preparacion.productos().size());
        servicio.pdfPedido(pedido); servicio.excelPedido(pedido);
        var usuario=context.getBean(UsuarioRepository.class).save(Usuario.builder().username("recepcion").nombreCompleto("Almacén")
                .rol(Rol.ALMACENERO).activo(true).passwordHash("prueba").build());
        context.getBean(RPLCTerminalSesion.class).setUsuario(usuario);
        jdbc.update("UPDATE productos SET sku='REC-1-NUEVO',descripcion='Nombre actual' WHERE id=?",p.getId());
        var entradas=preparacion.productos().stream().map(l->new ERQZCompraServiceI.LineaRecepcion(l.detalleId(),l.cantidad(),
                "REC-"+l.detalleId(),"B-02-1",new BigDecimal("2"),new BigDecimal("0.5"),new BigDecimal("35"))).toList();
        Long compra=compras.recibirPedido(pedido,"DUA-RECEPCION",LocalDate.now(),new BigDecimal("3.75"),entradas);
        assertEquals(9,productos.findById(p.getId()).orElseThrow().getStockActual()); assertEquals(4,productos.findById(q.getId()).orElseThrow().getStockActual());
        assertEquals("Nombre actual",productos.findById(p.getId()).orElseThrow().getDescripcion());
        assertEquals(compra,jdbc.queryForObject("SELECT compra_id FROM pedidos_compra WHERE id=?",Long.class,pedido));
        assertEquals(usuario.getId(),jdbc.queryForObject("SELECT recibido_por FROM pedidos_compra WHERE id=?",Long.class,pedido));
        assertNotNull(jdbc.queryForObject("SELECT recibido_en FROM pedidos_compra WHERE id=?",java.sql.Timestamp.class,pedido));
        assertEquals("RECIBIDO",servicio.documentoPedido(pedido).estado());
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM pedidos_reposicion WHERE pedido_compra_id=? AND estado='RECIBIDO'",Integer.class,pedido));
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM detalle_compra_dua WHERE compra_id=?",Integer.class,compra));
        assertEquals(0,new BigDecimal("25").compareTo(jdbc.queryForObject("SELECT total_landed_usd FROM compras_dua WHERE id=?",BigDecimal.class,compra)));
        assertEquals(10,jdbc.queryForObject("SELECT sum(cantidad) FROM movimientos_stock WHERE usuario_id=?",Integer.class,usuario.getId()));
        assertNull(jdbc.queryForObject("SELECT pdf_documento FROM pedidos_compra WHERE id=?",byte[].class,pedido));
        var reader=new com.lowagie.text.pdf.PdfReader(servicio.pdfPedido(pedido));
        try { assertTrue(new com.lowagie.text.pdf.parser.PdfTextExtractor(reader).getTextFromPage(1).contains("RECIBIDO")); }
        finally {reader.close();}
        assertTrue(servicio.listarPedidos().get(0).recepcion().contains("DUA-RECEPCION"));
        assertThrows(IllegalArgumentException.class,()->compras.recibirPedido(pedido,"OTRA-DUA",LocalDate.now(),new BigDecimal("3.75"),entradas));
        assertThrows(IllegalArgumentException.class,()->compras.prepararRecepcion(pedido));
        assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM compras_dua",Integer.class));
        assertEquals(9,productos.findById(p.getId()).orElseThrow().getStockActual());
        assertEquals(1,servicio.confirmarPedidos(List.of(new RPLCReposicionServiceI.LineaRevisada(p.getId(),proveedor,1,BigDecimal.ONE,new BigDecimal("3.75"))),"").size());
    }
    @Test void recepcionRechazaParcialesDuplicadosYOtrosProductos() {
        var s=context.getBean(RPLCReposicionServiceI.class); var c=context.getBean(ERQZCompraServiceI.class);
        Long proveedor=jdbc.queryForObject("INSERT INTO proveedores(razon_social,estado) VALUES ('Proveedor completo','ACTIVO') RETURNING id",Long.class);
        var p=producto("COMPLETO-1",1); var q=producto("COMPLETO-2",1);
        s.asignarProveedor(p.getId(),proveedor); s.asignarProveedor(q.getId(),proveedor);
        Long id=s.confirmarPedidos(List.of(new RPLCReposicionServiceI.LineaRevisada(p.getId(),proveedor,4,BigDecimal.ONE,BigDecimal.TEN),
                new RPLCReposicionServiceI.LineaRevisada(q.getId(),proveedor,6,BigDecimal.ONE,BigDecimal.TEN)),"").get(0);
        var filas=c.prepararRecepcion(id).productos();
        var a=new ERQZCompraServiceI.LineaRecepcion(filas.get(0).detalleId(),4,"NUEVO-1","A-02-1",BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.TEN);
        var b=new ERQZCompraServiceI.LineaRecepcion(filas.get(1).detalleId(),6,"NUEVO-2","A-02-2",BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.TEN);
        assertThrows(IllegalArgumentException.class,()->c.recibirPedido(id,"COMPLETA",LocalDate.now(),BigDecimal.ONE,List.of(a)));
        assertThrows(IllegalArgumentException.class,()->c.recibirPedido(id,"COMPLETA",LocalDate.now(),BigDecimal.ONE,List.of(a,a)));
        assertThrows(IllegalArgumentException.class,()->c.recibirPedido(id,"COMPLETA",LocalDate.now(),BigDecimal.ONE,List.of(a,
                new ERQZCompraServiceI.LineaRecepcion(b.detalleId(),5,b.lote(),b.ubicacion(),b.fobUsd(),b.gastosUsd(),b.margen()))));
        assertThrows(IllegalArgumentException.class,()->c.recibirPedido(id,"COMPLETA",LocalDate.now(),BigDecimal.ONE,List.of(a,
                new ERQZCompraServiceI.LineaRecepcion(Long.MAX_VALUE,6,b.lote(),b.ubicacion(),b.fobUsd(),b.gastosUsd(),b.margen()))));
        jdbc.update("UPDATE proveedores SET estado='INACTIVO' WHERE id=?",proveedor);
        assertThrows(IllegalArgumentException.class,()->c.recibirPedido(id,"COMPLETA",LocalDate.now(),BigDecimal.ONE,List.of(a,b)));
        assertEquals("PENDIENTE",s.documentoPedido(id).estado());
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM compras_dua",Integer.class));
        assertEquals(1,productos.findById(p.getId()).orElseThrow().getStockActual());
    }
    @Test void errorEnSegundoLoteRevierteTodaLaRecepcion() {
        var s=context.getBean(RPLCReposicionServiceI.class); var c=context.getBean(ERQZCompraServiceI.class);
        Long proveedor=jdbc.queryForObject("INSERT INTO proveedores(razon_social,estado) VALUES ('Proveedor reversión','ACTIVO') RETURNING id",Long.class);
        var p=producto("REV-REC-1",2); var q=producto("REV-REC-2",3); s.asignarProveedor(p.getId(),proveedor);s.asignarProveedor(q.getId(),proveedor);
        Long pedido=s.confirmarPedidos(List.of(new RPLCReposicionServiceI.LineaRevisada(p.getId(),proveedor,4,BigDecimal.ONE,BigDecimal.TEN),
                new RPLCReposicionServiceI.LineaRevisada(q.getId(),proveedor,6,BigDecimal.ONE,BigDecimal.TEN)),"").get(0);
        var l=c.prepararRecepcion(pedido).productos();
        var entradas=List.of(new ERQZCompraServiceI.LineaRecepcion(l.get(0).detalleId(),4,"LOTE-VALIDO","A-02-1",BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.TEN),
                new ERQZCompraServiceI.LineaRecepcion(l.get(1).detalleId(),6,"L-REV-REC-2","A-02-2",BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.TEN));
        assertThrows(IllegalArgumentException.class,()->c.recibirPedido(pedido,"REVERSIBLE",LocalDate.now(),new BigDecimal("3.75"),entradas));
        assertEquals("PENDIENTE",s.documentoPedido(pedido).estado()); assertNull(jdbc.queryForObject("SELECT compra_id FROM pedidos_compra WHERE id=?",Long.class,pedido));
        assertEquals(2,productos.findById(p.getId()).orElseThrow().getStockActual()); assertEquals(3,productos.findById(q.getId()).orElseThrow().getStockActual());
        assertEquals(2,jdbc.queryForObject("SELECT count(*) FROM lotes",Integer.class)); assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM compras_dua",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT count(*) FROM movimientos_stock",Integer.class));
    }
    @Test void dosCajasNoRecibenDosVecesElMismoPedido() throws Exception {
        var s=context.getBean(RPLCReposicionServiceI.class); var c=context.getBean(ERQZCompraServiceI.class);
        Long proveedor=jdbc.queryForObject("INSERT INTO proveedores(razon_social,estado) VALUES ('Proveedor concurrencia','ACTIVO') RETURNING id",Long.class);
        var p=producto("REC-CONCURRENTE",2); s.asignarProveedor(p.getId(),proveedor);
        Long pedido=s.confirmarPedidos(List.of(new RPLCReposicionServiceI.LineaRevisada(p.getId(),proveedor,8,BigDecimal.ONE,BigDecimal.TEN)),"").get(0);
        var l=c.prepararRecepcion(pedido).productos().get(0);
        var entradas=List.of(new ERQZCompraServiceI.LineaRecepcion(l.detalleId(),8,"REC-CONC","A-02-1",BigDecimal.ONE,BigDecimal.ZERO,BigDecimal.TEN));
        var pool=Executors.newFixedThreadPool(2); var inicio=new CountDownLatch(1);
        try {
            Callable<Boolean> recibir=()->{inicio.await();try{c.recibirPedido(pedido,"REC-CONC-DUA",LocalDate.now(),new BigDecimal("3.75"),entradas);return true;}catch(IllegalArgumentException e){return false;}};
            var a=pool.submit(recibir);var b=pool.submit(recibir);inicio.countDown();assertNotEquals(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
            assertEquals(10,productos.findById(p.getId()).orElseThrow().getStockActual()); assertEquals(1,jdbc.queryForObject("SELECT count(*) FROM compras_dua",Integer.class));
        } finally {pool.shutdownNow();}
    }
    @Test void exportaPedidosLargosConCabecerasRepetidas() throws Exception {
        var lineas=new ArrayList<PedidoCompraDocumento.Linea>();
        for(int i=0;i<150;i++) lineas.add(new PedidoCompraDocumento.Linea("SKU-"+i,"Producto largo para revisar el pedido de importación número "+i,2,
                BigDecimal.ONE,new BigDecimal("3.75"),new BigDecimal("2"),new BigDecimal("7.50")));
        var p=new PedidoCompraDocumento(99L,"Proveedor de prueba","20123456789","Ventas","Importadora","20987654321","Almacén principal",
                "2026-10-14 10:00:00","PENDIENTE","Entrega coordinada",new BigDecimal("300"),new BigDecimal("1125"),lineas);
        byte[] pdf=ERQZPedidoCompraExportador.pdf(p); var reader=new com.lowagie.text.pdf.PdfReader(pdf);
        try { assertTrue(reader.getNumberOfPages()>3);
            var extractor=new com.lowagie.text.pdf.parser.PdfTextExtractor(reader); var texto=new StringBuilder();
            for(int i=1;i<=reader.getNumberOfPages();i++) texto.append(extractor.getTextFromPage(i));
            assertTrue(texto.toString().contains("1125.00")); assertTrue(texto.toString().contains("SKU-149"));
            assertTrue(extractor.getTextFromPage(2).contains("Unit. USD")); }
        finally {reader.close();}
        try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(ERQZPedidoCompraExportador.excel(p)))) {
            assertEquals(300,book.getSheetAt(0).getRow(159).getCell(4).getNumericCellValue(),.001);
        }
        Files.createDirectories(Path.of("target/pedidos-preview")); Files.write(Path.of("target/pedidos-preview/pedido-largo.pdf"),pdf);
    }
}
