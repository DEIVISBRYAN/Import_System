package com.ejemplo.crudpersona.controller;

import com.ejemplo.crudpersona.model.DetalleVenta;
import com.ejemplo.crudpersona.model.Lote;
import com.ejemplo.crudpersona.model.Producto;
import com.ejemplo.crudpersona.model.Venta;
import com.ejemplo.crudpersona.repository.DetalleVentaRepository;
import com.ejemplo.crudpersona.repository.LoteRepository;
import com.ejemplo.crudpersona.repository.ProductoRepository;
import com.ejemplo.crudpersona.repository.VentaRepository;
import com.ejemplo.crudpersona.service.ERQZDatabaseDateNormalizer;
import com.ejemplo.crudpersona.service.ERQZReporteTabular;
import com.ejemplo.crudpersona.service.DBQPReporteVentas;
import com.ejemplo.crudpersona.ui.ExportarMenu;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class DBQPVentasController {

    private final VentaRepository ventaRepository;
    private final DetalleVentaRepository detalleVentaRepository;
    private final ProductoRepository productoRepository;
    private final LoteRepository loteRepository;
    private final ERQZDatabaseDateNormalizer dateNormalizer;
    private final ApplicationContext applicationContext;
    private final com.ejemplo.crudpersona.service.ERQZVentaServiceI ventaService;
    private final com.ejemplo.crudpersona.service.RPLCComprobantesServiceI comprobantesService;

    @FXML private TextField txtBuscar;
    @FXML private ExportarMenu exportarReporte;
    @FXML private Label lblResumenVentas;
    @FXML private DatePicker dpDesde;
    @FXML private DatePicker dpHasta;
    @FXML private TableView<Venta> tablaVentas;
    @FXML private TableColumn<Venta, String> colComprobante;
    @FXML private TableColumn<Venta, String> colCliente;
    @FXML private TableColumn<Venta, String> colDocumento;
    @FXML private TableColumn<Venta, String> colMetodoPago;
    @FXML private TableColumn<Venta, String> colTotal;
    @FXML private TableColumn<Venta, String> colEstado;
    @FXML private TableColumn<Venta, String> colFecha;

    @FXML private Label lblDetalleComprobante;
    @FXML private Label lblDetalleCliente;
    @FXML private Label lblDetalleDocumento;
    @FXML private Label lblDetalleFecha;
    @FXML private Label lblDetalleSubtotal;
    @FXML private Label lblDetalleIgv;
    @FXML private Label lblDetalleTotal;
    @FXML private TableView<DetalleVentaItem> tablaDetalle;
    @FXML private TableColumn<DetalleVentaItem, String> colDetProducto;
    @FXML private TableColumn<DetalleVentaItem, String> colDetCantidad;
    @FXML private TableColumn<DetalleVentaItem, String> colDetImporte;

    private final ObservableList<Venta> ventas = FXCollections.observableArrayList();
    private final ObservableList<DetalleVentaItem> detalle = FXCollections.observableArrayList();

    public DBQPVentasController(VentaRepository ventaRepository,
                            DetalleVentaRepository detalleVentaRepository,
                            ProductoRepository productoRepository,
                            LoteRepository loteRepository,
                            ERQZDatabaseDateNormalizer dateNormalizer,
                            ApplicationContext applicationContext,
                            com.ejemplo.crudpersona.service.ERQZVentaServiceI ventaService,
                            com.ejemplo.crudpersona.service.RPLCComprobantesServiceI comprobantesService) {
        this.ventaRepository = ventaRepository;
        this.detalleVentaRepository = detalleVentaRepository;
        this.productoRepository = productoRepository;
        this.loteRepository = loteRepository;
        this.dateNormalizer = dateNormalizer;
        this.applicationContext = applicationContext;
        this.ventaService = ventaService;
        this.comprobantesService = comprobantesService;
    }

    @FXML
    public void initialize() {
        exportarReporte.setReporte(this::reporteVentas);
        dateNormalizer.normalize();
        colComprobante.setCellValueFactory(data -> new SimpleStringProperty(comprobante(data.getValue())));
        colCliente.setCellValueFactory(data -> new SimpleStringProperty(texto(data.getValue().getClienteNombre())));
        colDocumento.setCellValueFactory(data -> new SimpleStringProperty(texto(data.getValue().getClienteDocumento())));
        colMetodoPago.setCellValueFactory(data -> new SimpleStringProperty(texto(data.getValue().getMetodoPago()).replace("_", " ")));
        colTotal.setCellValueFactory(data -> new SimpleStringProperty(moneda(data.getValue().getTotal())));
        colEstado.setCellValueFactory(data -> new SimpleStringProperty(texto(data.getValue().getEstado())));
        colFecha.setCellValueFactory(data -> new SimpleStringProperty(fecha(data.getValue().getFechaEmision())));

        colDetProducto.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().producto()));
        colDetCantidad.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().cantidad()));
        colDetImporte.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().importe()));

        tablaVentas.setItems(ventas);
        tablaDetalle.setItems(detalle);
        tablaVentas.getSelectionModel().selectedItemProperty().addListener((obs, anterior, venta) -> cargarDetalle(venta));

        cargarVentas();
    }

    public ERQZReporteTabular reporteVentas() {
        return DBQPReporteVentas.crear(List.copyOf(tablaVentas.getItems()), "Filas del historial con los filtros aplicados");
    }

    @FXML
    public void onActualizar() {
        cargarVentas();
    }

    @FXML
    public void onFiltrar() {
        cargarVentas();
    }

    @FXML
    public void onLimpiarFiltros() {
        txtBuscar.clear();
        dpDesde.setValue(null);
        dpHasta.setValue(null);
        cargarVentas();
    }

    @FXML
    public void onVerDetalle() {
        Venta venta = ventaSeleccionada();
        if (venta != null) {
            cargarDetalle(venta);
        }
    }

    @FXML
    public void onReimprimir() {
        Venta venta = ventaSeleccionada();
        if (venta == null) {
            return;
        }

        try {
            List<DBQPComprobantePopupController.ItemComprobante> items = new ArrayList<>();
            for (DetalleVenta detalleVenta : detalleVentaRepository.findByVentaId(venta.getId())) {
                items.add(new DBQPComprobantePopupController.ItemComprobante(
                        enteroSeguro(detalleVenta.getCantidad()),
                        producto(detalleVenta),
                        decimalSeguro(detalleVenta.getPrecioUnitario())
                ));
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/comprobante-popup.fxml"));
            loader.setControllerFactory(applicationContext::getBean);
            Parent root = loader.load();

            DBQPComprobantePopupController controller = loader.getController();
            Stage stage = new Stage();
            controller.setStage(stage);
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle("Reimprimir comprobante");
            stage.setScene(new Scene(root));
            stage.centerOnScreen();

            Long id = comprobantesService.registrarVenta(venta.getId());
            controller.cargarDatos(comprobantesService.documento(id));
            controller.setComprobanteId(id);

            stage.showAndWait();
        } catch (Exception e) {
            mostrarError("No se pudo reimprimir el comprobante", e.getMessage());
        }
    }

    @FXML
    public void onAnularVenta() {
        Venta venta = ventaSeleccionada();
        if (venta == null) {
            return;
        }
        if ("ANULADA".equalsIgnoreCase(texto(venta.getEstado()))) {
            mostrarInfo("Venta anulada", "Esta venta ya fue anulada.");
            return;
        }

        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setHeaderText("Anular venta " + comprobante(venta));
        confirmacion.setContentText("Se devolverá el stock de los productos vendidos. ¿Deseas continuar?");
        if (confirmacion.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        try { ventaService.anular(venta.getId()); }
        catch (Exception e) { mostrarError("No se pudo anular", e.getMessage()); return; }
        cargarVentas();
        mostrarInfo("Venta anulada", "La venta fue anulada y el stock fue devuelto.");
    }

    private void cargarVentas() {
        if (dpDesde.getValue() != null && dpHasta.getValue() != null && dpDesde.getValue().isAfter(dpHasta.getValue())) {
            mostrarError("Fechas inválidas", "La fecha Desde no puede ser posterior a Hasta."); return;
        }
        dateNormalizer.normalize();
        List<Venta> origen = buscarPorFecha();
        String filtro = texto(txtBuscar.getText()).toLowerCase(Locale.ROOT).trim();
        ventas.setAll(origen.stream()
                .filter(venta -> filtro.isBlank() || coincide(venta, filtro))
                .toList());
        BigDecimal vigentes = ventas.stream().filter(v -> !"ANULADA".equalsIgnoreCase(v.getEstado()))
                .map(v -> decimalSeguro(v.getTotal())).reduce(BigDecimal.ZERO, BigDecimal::add);
        lblResumenVentas.setText(ventas.size() + (ventas.size() == 1 ? " venta" : " ventas") + " · Total no anulado: " + moneda(vigentes));

        if (!ventas.isEmpty()) {
            tablaVentas.getSelectionModel().selectFirst();
        } else {
            cargarDetalle(null);
        }
    }

    private List<Venta> buscarPorFecha() {
        LocalDate desde = dpDesde.getValue();
        LocalDate hasta = dpHasta.getValue();

        if (desde == null && hasta == null) {
            return ventaRepository.findAllByOrderByFechaEmisionDesc();
        }

        LocalDateTime inicio = (desde == null ? LocalDate.of(2000, 1, 1) : desde).atStartOfDay();
        LocalDateTime fin = (hasta == null ? LocalDate.now().plusYears(10) : hasta).atTime(LocalTime.MAX);
        return ventaRepository.findByFechaEmisionBetweenOrderByFechaEmisionDesc(inicio, fin);
    }

    private boolean coincide(Venta venta, String filtro) {
        return texto(venta.getClienteNombre()).toLowerCase(Locale.ROOT).contains(filtro)
                || texto(venta.getClienteDocumento()).toLowerCase(Locale.ROOT).contains(filtro)
                || texto(venta.getEstado()).toLowerCase(Locale.ROOT).contains(filtro)
                || comprobante(venta).toLowerCase(Locale.ROOT).contains(filtro)
                || fecha(venta.getFechaEmision()).toLowerCase(Locale.ROOT).contains(filtro);
    }

    private void cargarDetalle(Venta venta) {
        if (venta == null) {
            lblDetalleComprobante.setText("-");
            lblDetalleCliente.setText("-");
            lblDetalleDocumento.setText("-");
            lblDetalleFecha.setText("-");
            lblDetalleSubtotal.setText("S/ 0.00");
            lblDetalleIgv.setText("S/ 0.00");
            lblDetalleTotal.setText("S/ 0.00");
            detalle.clear();
            return;
        }

        lblDetalleComprobante.setText(comprobante(venta));
        lblDetalleCliente.setText(texto(venta.getClienteNombre()));
        lblDetalleDocumento.setText(texto(venta.getClienteDocumento()));
        lblDetalleFecha.setText(fecha(venta.getFechaEmision()));
        lblDetalleSubtotal.setText(moneda(venta.getSubtotal()));
        lblDetalleIgv.setText(moneda(venta.getIgv()));
        lblDetalleTotal.setText(moneda(venta.getTotal()));

        detalle.setAll(detalleVentaRepository.findByVentaId(venta.getId()).stream()
                .map(d -> new DetalleVentaItem(producto(d), String.valueOf(enteroSeguro(d.getCantidad())), moneda(d.getSubtotal())))
                .toList());
    }

    private Venta ventaSeleccionada() {
        Venta venta = tablaVentas.getSelectionModel().getSelectedItem();
        if (venta == null) {
            mostrarInfo("Selecciona una venta", "Primero selecciona una venta del historial.");
        }
        return venta;
    }

    private String comprobante(Venta venta) {
        return texto(venta.getSerie()) + "-" + String.format("%06d", enteroSeguro(venta.getCorrelativo()));
    }

    private String producto(DetalleVenta detalleVenta) {
        Producto producto = detalleVenta.getProducto();
        String nombre = producto == null ? "Producto sin referencia" : texto(producto.getDescripcion());
        if (detalleVenta.getLoteId() == null) return nombre;
        return loteRepository.findById(detalleVenta.getLoteId())
                .map(lote -> nombre + " · " + texto(lote.getCodigoLote()))
                .orElse(nombre);
    }

    private String texto(Object valor) {
        return valor == null ? "" : valor.toString();
    }

    private String fecha(LocalDateTime valor) {
        return valor == null ? "-" : valor.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    private String moneda(BigDecimal valor) {
        return String.format("S/ %.2f", decimalSeguro(valor));
    }

    private BigDecimal decimalSeguro(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private int enteroSeguro(Integer valor) {
        return valor == null ? 0 : valor;
    }

    private void mostrarInfo(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, mensaje);
        alert.setHeaderText(titulo);
        alert.showAndWait();
    }

    private void mostrarError(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.ERROR, mensaje == null ? "Error inesperado." : mensaje);
        alert.setHeaderText(titulo);
        alert.showAndWait();
    }

    public record DetalleVentaItem(String producto, String cantidad, String importe) {
    }
}
