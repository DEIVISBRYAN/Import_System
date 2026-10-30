package com.ejemplo.crudpersona.controller;

import com.ejemplo.crudpersona.model.*;
import com.ejemplo.crudpersona.repository.*;
import com.ejemplo.crudpersona.service.ERQZDatabaseDateNormalizer;
import com.ejemplo.crudpersona.service.RPLCInventarioFifoServiceI;
import com.ejemplo.crudpersona.service.ERQZVentaServiceI;
import com.ejemplo.crudpersona.service.RPLCComprobantesServiceI;
import com.ejemplo.crudpersona.service.RPLCTerminalSesion;
import com.ejemplo.crudpersona.service.RPLCInventarioFifoServiceI.DespachoLote;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Component
public class ERQZPuntoVentaController {

    private final RPLCInventarioFifoServiceI inventarioFifoService;
    private final ProductoRepository productoRepository;
    private final VentaRepository ventaRepository;
    private final DetalleVentaRepository detalleVentaRepository;
    private final CajaTurnoRepository cajaTurnoRepository;
    private final ERQZDatabaseDateNormalizer dateNormalizer;
    private final ApplicationContext applicationContext;
    private final ERQZVentaServiceI ventaService;
    private final RPLCComprobantesServiceI comprobantesService;
    private final RPLCTerminalSesion sesion;
    @FXML private Label lblEstadoCaja;
    @FXML private Label lblCambioVenta;

    @FXML private TextField txtEscaner;
    @FXML private VBox vistaVenta;
    @FXML private ToggleButton btnModoLector;
    @FXML private TableView<VentaItem> tablaVenta;
    @FXML private TableColumn<VentaItem, String> colSku;
    @FXML private TableColumn<VentaItem, String> colCodigo;
    @FXML private TableColumn<VentaItem, String> colDescripcion;
    @FXML private TableColumn<VentaItem, String> colLote;
    @FXML private TableColumn<VentaItem, Integer> colCantidad;
    @FXML private TableColumn<VentaItem, BigDecimal> colPrecio;
    @FXML private TableColumn<VentaItem, BigDecimal> colSubtotal;
    @FXML private TableColumn<VentaItem, Void> colAccion;

    @FXML private RadioButton rbBoleta;
    @FXML private RadioButton rbFactura;
    @FXML private RadioButton rbEfectivo;
    @FXML private RadioButton rbYapePlin;
    @FXML private RadioButton rbTarjeta;
    @FXML private RadioButton rbMixto;
    @FXML private Label lblSubtotal;
    @FXML private Label lblIgv;
    @FXML private Label lblTotalPEN;
    @FXML private Label lblTotalUSD;
    @FXML private Label lblResumenBase;
    @FXML private Label lblStockAviso;
    @FXML private TextField txtClienteDocumento;
    @FXML private TextField txtClienteNombre;
    @FXML private FlowPane boxBotonesRapidos;
    @FXML private Button btnCobrar;

    private BigDecimal tipoCambioActual;
    private final com.ejemplo.crudpersona.service.DBQPTipoCambioServiceI cambios;
    private ObservableList<VentaItem> itemsVenta = FXCollections.observableArrayList();
    private final ToggleGroup grupoComprobante = new ToggleGroup();
    private final ToggleGroup grupoPago = new ToggleGroup();

    public ERQZPuntoVentaController(RPLCInventarioFifoServiceI inventarioFifoService, ProductoRepository productoRepository,
                                VentaRepository ventaRepository, DetalleVentaRepository detalleVentaRepository,
                                CajaTurnoRepository cajaTurnoRepository, ERQZDatabaseDateNormalizer dateNormalizer,
                                ApplicationContext applicationContext, ERQZVentaServiceI ventaService,
                                RPLCComprobantesServiceI comprobantesService, RPLCTerminalSesion sesion, com.ejemplo.crudpersona.service.DBQPTipoCambioServiceI cambios) {
        this.inventarioFifoService = inventarioFifoService;
        this.productoRepository = productoRepository;
        this.ventaRepository = ventaRepository;
        this.detalleVentaRepository = detalleVentaRepository;
        this.cajaTurnoRepository = cajaTurnoRepository;
        this.dateNormalizer = dateNormalizer;
        this.applicationContext = applicationContext;
        this.ventaService = ventaService;
        this.comprobantesService = comprobantesService;
        this.sesion = sesion;
        this.cambios = cambios;
    }

    @FXML
    public void initialize() {
        tipoCambioActual = cambios.actual();
        lblCambioVenta.setText(tipoCambioActual.toPlainString());
        dateNormalizer.normalize();
        colSku.setCellValueFactory(new PropertyValueFactory<>("sku"));
        colCodigo.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCodigo()));
        colDescripcion.setCellValueFactory(new PropertyValueFactory<>("descripcion"));
        colLote.setCellValueFactory(new PropertyValueFactory<>("lote"));
        colCantidad.setCellValueFactory(new PropertyValueFactory<>("cantidad"));
        colPrecio.setCellValueFactory(new PropertyValueFactory<>("precio"));
        colSubtotal.setCellValueFactory(new PropertyValueFactory<>("subtotal"));
        configurarOpciones();
        boolean abierta = cajaTurnoActualId() != null;
        lblEstadoCaja.setText("Caja " + sesion.numeroCaja() + (abierta ? " abierta" : " cerrada"));
        lblEstadoCaja.getStyleClass().setAll(abierta ? "chip-exito" : "chip-alerta");
        cargarResumenBase();
        cargarBotonesRapidos();

        colCantidad.setCellFactory(param -> new TableCell<>() {
            private final Button menos = new Button("-");
            private final Button mas = new Button("+");
            private final Label cantidad = new Label("1");
            private final HBox box = new HBox(6, menos, cantidad, mas);
            {
                box.setAlignment(javafx.geometry.Pos.CENTER);
                menos.getStyleClass().add("qty-button");
                mas.getStyleClass().add("qty-button");
                cantidad.getStyleClass().add("qty-label");
                menos.setOnAction(event -> cambiarCantidad(getTableView().getItems().get(getIndex()), -1));
                mas.setOnAction(event -> cambiarCantidad(getTableView().getItems().get(getIndex()), 1));
            }
            @Override
            protected void updateItem(Integer item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getIndex() < 0 || getIndex() >= getTableView().getItems().size()) {
                    setGraphic(null);
                    return;
                }
                cantidad.setText(String.valueOf(getTableView().getItems().get(getIndex()).getCantidad()));
                setGraphic(box);
            }
        });

        colAccion.setCellFactory(param -> new TableCell<>() {
            private final Button btn = new Button("Quitar");
            {
                btn.getStyleClass().add("btn-danger-soft");
                btn.setOnAction(event -> {
                    VentaItem item = getTableView().getItems().get(getIndex());
                    itemsVenta.remove(item);
                    calcularTotales();
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        tablaVenta.setItems(itemsVenta);
        txtEscaner.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.TAB && !txtEscaner.getText().isBlank()) {
                e.consume(); agregarDesdeBusqueda();
            }
        });
        vistaVenta.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.F8) { e.consume(); txtEscaner.requestFocus(); txtEscaner.selectAll(); }
        });
        Platform.runLater(() -> txtEscaner.requestFocus());
    }

    @org.springframework.transaction.event.TransactionalEventListener
    public void alActualizarDolar(com.ejemplo.crudpersona.service.ERQZTipoCambioActualizado evento) {
        if (lblCambioVenta != null && lblCambioVenta.getScene() != null) Platform.runLater(() -> {
            tipoCambioActual = cambios.actual(); lblCambioVenta.setText(tipoCambioActual.toPlainString()); calcularTotales();
        });
    }

    @FXML public void onModoLector() {
        txtEscaner.setPromptText(btnModoLector.isSelected() ? "Código de barras o SKU" : "Código de barras, SKU o producto");
        txtEscaner.requestFocus(); txtEscaner.selectAll();
    }

    private void cambiarCantidad(VentaItem item, int delta) {
        int nuevaCantidad = item.getCantidad() + delta;
        if (nuevaCantidad <= 0) {
            itemsVenta.remove(item);
        } else if (nuevaCantidad > stockDisponible(item.getProducto())) {
            mostrarAdvertenciaStock("Stock insuficiente para " + item.getDescripcion());
            return;
        } else {
            item.setCantidad(nuevaCantidad);
        }
        tablaVenta.refresh();
        calcularTotales();
    }

    private void configurarOpciones() {
        rbBoleta.setToggleGroup(grupoComprobante);
        rbFactura.setToggleGroup(grupoComprobante);
        rbBoleta.setSelected(true);

        rbEfectivo.setToggleGroup(grupoPago);
        rbYapePlin.setToggleGroup(grupoPago);
        rbTarjeta.setToggleGroup(grupoPago);
        rbMixto.setToggleGroup(grupoPago);
        rbEfectivo.setSelected(true);

        grupoComprobante.selectedToggleProperty().addListener((obs, anterior, actual) -> actualizarBotonCobro());
        actualizarBotonCobro();
    }

    private void cargarResumenBase() {
        long productos = productoRepository.count();
        lblResumenBase.setText(productos + " productos registrados");
    }

    private void cargarBotonesRapidos() {
        boxBotonesRapidos.getChildren().clear();
        List<Producto> productos = productosMasVendidos();
        for (Producto producto : productos.stream().limit(3).toList()) {
            Button boton = new Button(nombreCorto(producto.getDescripcion()) + " · " + stockDisponible(producto));
            boton.getStyleClass().add("btn-secundario");
            boton.setOnAction(event -> { agregarProductoAVenta(producto); txtEscaner.requestFocus(); });
            boxBotonesRapidos.getChildren().add(boton);
        }
    }

    @FXML
    public void onEscanearEnter(ActionEvent event) {
        agregarDesdeBusqueda();
    }

    @FXML
    public void onAgregarBusqueda() {
        agregarDesdeBusqueda();
    }

    private void agregarDesdeBusqueda() {
        String codigo = txtEscaner.getText().trim();
        if (codigo.isEmpty()) return;
        try {
            Optional<Producto> optProd = buscarProducto(codigo);
            if (optProd.isPresent()) {
                Producto p = optProd.get();
                agregarProductoAVenta(p);
                txtEscaner.clear();
            } else {
                mostrarAdvertenciaStock("Código o producto no encontrado: " + codigo);
                txtEscaner.selectAll();
            }
        } catch (RuntimeException ex) { mostrarAdvertenciaStock("No se pudo consultar el producto. Revisa la conexión e intenta otra vez."); }
        txtEscaner.requestFocus();
    }

    private Optional<Producto> buscarProducto(String valor) {
        Optional<Producto> producto = productoRepository.findByCodigoBarras(valor);
        if (producto.isEmpty()) {
            producto = productoRepository.findBySkuIgnoreCase(valor);
        }
        if (producto.isEmpty() && !btnModoLector.isSelected()) {
            List<Producto> encontrados = productoRepository.findByDescripcionContainingIgnoreCase(valor);
            if (encontrados.size() == 1) {
                producto = Optional.of(encontrados.get(0));
            } else if (encontrados.size() > 1) {
                producto = elegirProducto(encontrados);
            }
        }
        return producto;
    }

    private Optional<Producto> elegirProducto(List<Producto> productos) {
        ChoiceDialog<Producto> dialog = new ChoiceDialog<>(productos.get(0), productos);
        dialog.setTitle("Seleccionar producto");
        dialog.setHeaderText("Hay varios productos con ese nombre");
        dialog.setContentText("Producto:");
        return dialog.showAndWait();
    }

    private void agregarProductoAVenta(Producto p) {
        if (stockDisponible(p) <= 0) {
            mostrarAdvertenciaStock("Sin stock disponible para " + p.getDescripcion());
            return;
        }
        // Verificar si ya existe en la lista para sumar cantidad
        for (VentaItem item : itemsVenta) {
            if (item.getProducto().getId().equals(p.getId())) {
                if (item.getCantidad() + 1 > stockDisponible(p)) {
                    mostrarAdvertenciaStock("Stock insuficiente para " + p.getDescripcion());
                    return;
                }
                item.setCantidad(item.getCantidad() + 1);
                tablaVenta.refresh();
                calcularTotales();
                ocultarAvisoStock();
                return;
            }
        }
        // Si no existe, agregarlo como nuevo registro
        VentaItem nuevo = new VentaItem(p, 1, p.getPrecioVentaPen());
        itemsVenta.add(nuevo);
        calcularTotales();
        ocultarAvisoStock();
    }

    private void calcularTotales() {
        BigDecimal total = BigDecimal.ZERO;
        for (VentaItem item : itemsVenta) {
            total = total.add(item.getSubtotal());
        }

        BigDecimal subtotal = total.divide(new BigDecimal("1.18"), 2, RoundingMode.HALF_UP);
        BigDecimal igv = total.subtract(subtotal);
        BigDecimal totalUSD = total.divide(tipoCambioActual, 2, RoundingMode.HALF_UP);

        lblSubtotal.setText(String.format("S/ %.2f", subtotal));
        lblIgv.setText(String.format("S/ %.2f", igv));
        lblTotalPEN.setText(String.format("S/ %.2f", total));
        lblTotalUSD.setText(String.format("US$ %.2f", totalUSD));
    }

    @FXML
    public void onCobrar() {
        if (itemsVenta.isEmpty()) return;
        dateNormalizer.normalize();
        if (cajaTurnoActualId() == null) {
            mostrarError("Caja cerrada", "Abre una caja antes de registrar ventas.");
            return;
        }
        if (!validarCliente()) return;
        if (!validarStockVenta()) return;

        try {
            String metodoPago = rbEfectivo.isSelected() ? "EFECTIVO" :
                               rbYapePlin.isSelected() ? "YAPE_PLIN" :
                               rbTarjeta.isSelected() ? "TARJETA" : "MIXTO";

            String tipoComprobante = rbBoleta.isSelected() ? "BOLETA" : "FACTURA";

            String documento = textoClienteDocumento();
            String cliente = textoClienteNombre();
            Long ventaId = ventaService.cobrar(sesion.numeroCaja(), sesion.usuarioId(), tipoComprobante,
                    documento, cliente, metodoPago, itemsVenta.stream().map(item ->
                        new ERQZVentaServiceI.Linea(item.getProducto().getId(), item.getCantidad(), item.getPrecio())).toList());
            Long comprobanteId = comprobantesService.registrarVenta(ventaId);
            // La venta ya esta guardada. Limpiar antes de abrir evita un cobro doble si falla la vista.
            onCancelar();
            cargarBotonesRapidos();

            // Mostrar popup
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/comprobante-popup.fxml"));
            loader.setControllerFactory(applicationContext::getBean);
            Parent root = loader.load();

            DBQPComprobantePopupController controller = loader.getController();
            Stage stage = new Stage();
            controller.setStage(stage);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Comprobante Emitido");
            stage.setScene(new Scene(root));
            stage.centerOnScreen();

            controller.cargarDatos(comprobantesService.documento(comprobanteId));
            controller.setComprobanteId(comprobanteId);

            stage.showAndWait();

            onCancelar();

        } catch (Exception e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR, "Error al procesar venta: " + e.getMessage());
            alert.show();
        }
    }

    @FXML public void onCancelar() {
        itemsVenta.clear();
        calcularTotales();
        txtEscaner.clear(); ocultarAvisoStock();
        txtEscaner.requestFocus();
    }

    private boolean validarCliente() {
        String documento = textoClienteDocumento();
        if (rbFactura.isSelected() && !documento.matches("\\d{11}")) {
            mostrarError("Factura requiere RUC", "Ingresa un RUC de 11 dígitos para emitir factura.");
            return false;
        }
        if (rbBoleta.isSelected() && !documento.isBlank() && !documento.matches("\\d{8}")) {
            mostrarError("DNI inválido", "Para boleta ingresa un DNI de 8 dígitos o deja cliente mostrador.");
            return false;
        }
        return true;
    }

    private boolean validarStockVenta() {
        for (VentaItem item : itemsVenta) {
            if (item.getCantidad() > stockDisponible(item.getProducto())) {
                mostrarAdvertenciaStock("Stock insuficiente para " + item.getDescripcion());
                return false;
            }
        }
        return true;
    }

    private Long cajaTurnoActualId() {
        dateNormalizer.normalize();
        return cajaTurnoRepository.findByNumeroCajaAndEstado(sesion.numeroCaja(), "ABIERTA")
                .map(CajaTurno::getId)
                .orElse(null);
    }

    private List<Producto> productosMasVendidos() {
        List<Producto> productos = new ArrayList<>();
        for (Object[] fila : detalleVentaRepository.findProductosMasVendidos()) {
            Long id = (Long) fila[0];
            productoRepository.findById(id).ifPresent(productos::add);
        }
        if (productos.size() < 3) {
            productoRepository.findAll().stream()
                    .filter(p -> productos.stream().noneMatch(actual -> actual.getId().equals(p.getId())))
                    .sorted(Comparator.comparing(Producto::getDescripcion))
                    .forEach(productos::add);
        }
        return productos;
    }

    private int stockDisponible(Producto producto) {
        return productoRepository.findById(producto.getId())
                .map(p -> p.getStockActual() == null ? 0 : p.getStockActual()).orElse(0);
    }

    private String textoClienteDocumento() {
        return txtClienteDocumento.getText() == null ? "" : txtClienteDocumento.getText().trim();
    }

    private String textoClienteNombre() {
        String nombre = txtClienteNombre.getText() == null ? "" : txtClienteNombre.getText().trim();
        return nombre.isBlank() ? "Cliente mostrador" : nombre;
    }

    private String nombreCorto(String descripcion) {
        if (descripcion == null || descripcion.isBlank()) {
            return "Producto";
        }
        return descripcion.length() > 18 ? descripcion.substring(0, 18) + "..." : descripcion;
    }

    private BigDecimal decimal(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private void actualizarBotonCobro() {
        if (btnCobrar != null) {
            btnCobrar.setText(rbFactura != null && rbFactura.isSelected() ? "Pagar y guardar (Factura)" : "Pagar y guardar (Boleta)");
        }
    }

    private void mostrarAdvertenciaStock(String mensaje) {
        lblStockAviso.setText(mensaje);
        lblStockAviso.setVisible(true);
        lblStockAviso.setManaged(true);
    }

    private void ocultarAvisoStock() {
        lblStockAviso.setVisible(false);
        lblStockAviso.setManaged(false);
    }

    private void mostrarError(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.ERROR, mensaje);
        alert.setHeaderText(titulo);
        alert.showAndWait();
    }

    // --- Clase Modelo Interna para la TableView ---
    public static class VentaItem {
        private Producto producto;
        private SimpleStringProperty sku;
        private SimpleStringProperty descripcion;
        private SimpleStringProperty lote;
        private SimpleIntegerProperty cantidad;
        private SimpleObjectProperty<BigDecimal> precio;
        private SimpleObjectProperty<BigDecimal> subtotal;

        public VentaItem(Producto p, int cantidad, BigDecimal precio) {
            this.producto = p;
            this.sku = new SimpleStringProperty(p.getSku() == null ? "-" : p.getSku());
            this.descripcion = new SimpleStringProperty(p.getDescripcion() == null ? "Producto" : p.getDescripcion());
            this.lote = new SimpleStringProperty("AUTO-FIFO");
            this.cantidad = new SimpleIntegerProperty(cantidad);
            this.precio = new SimpleObjectProperty<>(precio == null ? BigDecimal.ZERO : precio);
            this.subtotal = new SimpleObjectProperty<>(this.precio.get().multiply(new BigDecimal(cantidad)));
        }

        public Producto getProducto() { return producto; }
        public String getSku() { return sku.get(); }
        public String getCodigo() { return producto.getCodigoBarras() == null ? "" : producto.getCodigoBarras(); }
        public String getDescripcion() { return descripcion.get(); }
        public String getLote() { return lote.get(); }
        public int getCantidad() { return cantidad.get(); }
        public BigDecimal getPrecio() { return precio.get(); }
        public BigDecimal getSubtotal() { return subtotal.get(); }

        public void setCantidad(int cant) {
            this.cantidad.set(cant);
            this.subtotal.set(this.precio.get().multiply(new BigDecimal(cant)));
        }
    }
}
