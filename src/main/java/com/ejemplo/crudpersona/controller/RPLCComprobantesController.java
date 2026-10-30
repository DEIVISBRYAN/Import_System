package com.ejemplo.crudpersona.controller;

import com.ejemplo.crudpersona.model.ComprobanteSunat;
import com.ejemplo.crudpersona.repository.ComprobanteSunatRepository;
import com.ejemplo.crudpersona.service.RPLCComprobantesServiceI;
import com.ejemplo.crudpersona.ui.PdfDescarga;
import com.ejemplo.crudpersona.ui.ExportarMenu;
import com.ejemplo.crudpersona.service.ERQZReporteTabular;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Component
public class RPLCComprobantesController {
    private final ComprobanteSunatRepository repo;
    private final RPLCComprobantesServiceI service;
    private final ApplicationContext context;
    @FXML private TableView<ComprobanteSunat> tablaComprobantes;
    @FXML private TableColumn<ComprobanteSunat, String> colTipo, colDoc, colCliente, colEstado, colFecha, colSerie;
    @FXML private TableColumn<ComprobanteSunat, BigDecimal> colTotal;
    @FXML private TextField txtBuscar;
    @FXML private Label lblResumen;
    @FXML private ExportarMenu exportarReporte;
    private final ObservableList<ComprobanteSunat> comprobantes = FXCollections.observableArrayList();
    public RPLCComprobantesController(ComprobanteSunatRepository repo, RPLCComprobantesServiceI service, ApplicationContext context) {
        this.repo = repo; this.service = service; this.context = context;
    }
    @FXML public void initialize() {
        exportarReporte.setReporte(this::reporteComprobantes);
        colTipo.setCellValueFactory(new PropertyValueFactory<>("tipoComprobante"));
        colSerie.setCellValueFactory(d -> new SimpleStringProperty(numero(d.getValue())));
        colDoc.setCellValueFactory(new PropertyValueFactory<>("clienteDocumento"));
        colCliente.setCellValueFactory(new PropertyValueFactory<>("clienteNombre"));
        colTotal.setCellValueFactory(new PropertyValueFactory<>("total"));
        colTotal.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(BigDecimal value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty || value == null ? null : String.format(Locale.ROOT, "S/ %.2f", value));
            }
        });
        colEstado.setCellValueFactory(new PropertyValueFactory<>("estadoSunat"));
        colFecha.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getFechaEmision().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))));
        tablaComprobantes.setItems(comprobantes);
        service.sincronizarVentas(); cargar();
    }
    public ERQZReporteTabular reporteComprobantes() {
        return new ERQZReporteTabular("Reporte de comprobantes", "Filas según los filtros aplicados. Este listado no acredita el envío ni la aceptación de SUNAT.",
                java.util.List.of("Tipo", "Comprobante", "Fecha", "Cliente", "DNI / RUC", "Total PEN", "Estado SUNAT"),
                tablaComprobantes.getItems().stream().map(c -> ERQZReporteTabular.fila(c.getTipoComprobante(), numero(c), c.getFechaEmision(),
                        c.getClienteNombre(), c.getClienteDocumento(), c.getTotal(), c.getEstadoSunat())).toList());
    }
    @FXML public void onBuscar() { cargar(); }
    @FXML public void onActualizar() { service.sincronizarVentas(); cargar(); }
    @FXML public void onDescargarPdf() {
        var c = seleccionado(); if (c == null) return;
        try {
            if (PdfDescarga.guardar(tablaComprobantes.getScene().getWindow(), c.getId(), numero(c), service))
                info("PDF descargado y guardado en PostgreSQL.");
        } catch (Exception e) { error(e); }
    }
    @FXML public void onGuardarPendientes() {
        try {
            service.sincronizarVentas();
            int cantidad = 0;
            for (var c : repo.findAll()) {
                if ("PENDIENTE".equals(c.getEstadoSunat()) && c.getVentaId() != null) { service.generarPdf(c.getId()); cantidad++; }
            }
            cargar(); info(cantidad + " comprobantes conservados para el envio posterior. No se enviaron a SUNAT.");
        } catch (Exception e) { error(e); }
    }
    @FXML public void onVer() {
        var c = seleccionado(); if (c == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/comprobante-popup.fxml"));
            loader.setControllerFactory(context::getBean);
            var root = loader.<javafx.scene.Parent>load();
            DBQPComprobantePopupController controller = loader.getController();
            Stage stage = new Stage(); stage.initOwner(tablaComprobantes.getScene().getWindow());
            stage.initModality(Modality.WINDOW_MODAL); stage.setTitle(numero(c)); stage.setScene(new Scene(root));
            controller.setStage(stage); controller.cargarDatos(service.documento(c.getId())); controller.setComprobanteId(c.getId());
            stage.showAndWait();
        } catch (Exception e) { error(e); }
    }
    @FXML public void onDatosEmpresa() {
        var empresa = service.datosEmpresa();
        TextField nombre = new TextField(empresa.getRazonSocial()), ruc = new TextField(empresa.getRuc()), direccion = new TextField(empresa.getDireccion());
        Label validacion = new Label(); validacion.setWrapText(true); validacion.setStyle("-fx-text-fill: #dc244b;");
        Dialog<Void> dialog = new Dialog<>(); dialog.initOwner(tablaComprobantes.getScene().getWindow()); dialog.setTitle("Datos de empresa");
        dialog.getDialogPane().getStyleClass().add("app-dialog");
        dialog.getDialogPane().setContent(new VBox(10, new Label("Razon social *"), nombre, new Label("RUC *"), ruc, new Label("Direccion"), direccion, validacion));
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button guardar = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK); guardar.setText("Guardar");
        guardar.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try { service.guardarEmpresa(nombre.getText(), ruc.getText(), direccion.getText()); }
            catch (Exception e) { validacion.setText(e.getMessage()); event.consume(); }
        });
        dialog.showAndWait(); cargar();
    }
    private void cargar() {
        String filtro = texto(txtBuscar.getText()).toLowerCase(Locale.ROOT).trim();
        var todos = repo.findAllByOrderByFechaEmisionDescIdDesc();
        comprobantes.setAll(todos.stream().filter(c -> (numero(c) + " " + texto(c.getClienteNombre()) + " " + texto(c.getClienteDocumento()) + " " + c.getTipoComprobante() + " " + c.getEstadoSunat()).toLowerCase(Locale.ROOT).contains(filtro)).toList());
        lblResumen.setText(todos.size() + " comprobantes · " + todos.stream().filter(c -> "PENDIENTE".equals(c.getEstadoSunat())).count() + " pendientes de envio");
    }
    private ComprobanteSunat seleccionado() {
        var c = tablaComprobantes.getSelectionModel().getSelectedItem();
        if (c == null) info("Selecciona un comprobante."); return c;
    }
    private String numero(ComprobanteSunat c) { return c.getSerie() + "-" + String.format("%06d", c.getCorrelativo()); }
    private String texto(String s) { return s == null ? "" : s; }
    private void info(String mensaje) { new Alert(Alert.AlertType.INFORMATION, mensaje).showAndWait(); }
    private void error(Exception e) { new Alert(Alert.AlertType.ERROR, texto(e.getMessage())).showAndWait(); }
}
