package com.ejemplo.crudpersona.controller;

import com.ejemplo.crudpersona.model.ComprobanteDocumento;
import com.ejemplo.crudpersona.ui.ComprobanteImpresion;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.concurrent.Task;
import javafx.print.PrinterJob;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

@Component
@org.springframework.context.annotation.Scope("prototype")
public class DBQPComprobantePopupController {
    private final com.ejemplo.crudpersona.service.RPLCComprobantesServiceI comprobantes;
    private Long comprobanteId;
    public DBQPComprobantePopupController(com.ejemplo.crudpersona.service.RPLCComprobantesServiceI comprobantes) {
        this.comprobantes = comprobantes;
    }
    public void setComprobanteId(Long id) { this.comprobanteId = id; }

    @FXML public void onDescargarPdf() {
        if (comprobanteId == null || documento == null) { errorDescarga("Primero guarda el comprobante."); return; }
        try {
            if (com.ejemplo.crudpersona.ui.PdfDescarga.guardar(stage, comprobanteId, documento.numero(), comprobantes)) {
                lblEstadoImpresion.setText("PDF descargado y conservado en la base de datos.");
                lblEstadoImpresion.setVisible(true); lblEstadoImpresion.setManaged(true);
            }
        } catch (Exception e) { errorDescarga(e.getMessage()); }
    }

    @FXML private Label lblNumeroComprobante;
    @FXML private Label lblFechaHora;
    @FXML private Label lblClienteDocumento;
    @FXML private Label lblMetodoPago;
    @FXML private TableView<ItemComprobante> tablaItems;
    @FXML private TableColumn<ItemComprobante, String> colCantidad;
    @FXML private TableColumn<ItemComprobante, String> colDescripcion;
    @FXML private TableColumn<ItemComprobante, String> colPrecio;
    @FXML private Label lblOpGravada;
    @FXML private Label lblIgv;
    @FXML private Label lblTotal;
    @FXML private Button btnPagarGuardar;
    @FXML private Button btnImprimir;
    @FXML private Button btnCerrar;
    @FXML private Label lblEmpresa;
    @FXML private Label lblRucEmpresa;
    @FXML private Label lblTipoComprobante;
    @FXML private Label lblClienteNombre;
    @FXML private Label lblEstadoImpresion;

    private Stage stage;
    private ComprobanteDocumento documento;

    @FXML
    public void initialize() {
        colCantidad.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getCantidad()));
        colDescripcion.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDescripcion()));
        colPrecio.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getPrecio()));
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void cargarDatos(String numeroComprobante, String clienteDocumento, String metodoPago,
                           List<ItemComprobante> items, BigDecimal subtotal, BigDecimal igv, BigDecimal total) {
        cargarDatos(new ComprobanteDocumento(numeroComprobante,
                numeroComprobante.startsWith("F") ? "FACTURA" : "BOLETA", LocalDateTime.now(),
                com.ejemplo.crudpersona.ui.Marca.NOMBRE, "", "", "Cliente mostrador", clienteDocumento, metodoPago,
                subtotal, igv, total, "PENDIENTE", items.stream().map(item ->
                    new ComprobanteDocumento.Linea(item.cantidad, item.descripcion, item.precio,
                            item.precio.multiply(BigDecimal.valueOf(item.cantidad)))).toList()));
    }

    public void cargarDatos(ComprobanteDocumento datos) {
        documento = datos;
        lblEmpresa.setText(datos.empresa());
        lblRucEmpresa.setText("RUC: " + (datos.rucEmpresa().isBlank() ? "Por configurar" : datos.rucEmpresa()));
        lblTipoComprobante.setText(datos.tipo());
        lblNumeroComprobante.setText(datos.numero());
        lblFechaHora.setText(datos.fecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        lblClienteNombre.setText(datos.cliente());
        lblClienteDocumento.setText(("FACTURA".equals(datos.tipo()) ? "RUC: " : "DNI: ") + datos.documentoCliente());
        lblMetodoPago.setText(datos.metodoPago());
        tablaItems.setItems(FXCollections.observableArrayList(datos.lineas().stream()
                .map(linea -> new ItemComprobante(linea.cantidad(), linea.descripcion(), linea.precio())).toList()));
        lblOpGravada.setText(String.format(Locale.ROOT, "S/ %.2f", datos.subtotal()));
        lblIgv.setText(String.format(Locale.ROOT, "S/ %.2f", datos.igv()));
        lblTotal.setText(String.format(Locale.ROOT, "S/ %.2f", datos.total()));
        lblEstadoImpresion.setVisible(false);
        lblEstadoImpresion.setManaged(false);
    }

    @FXML
    public void onPagarGuardar() {
        try {
            if (comprobanteId != null) {
                comprobantes.generarPdf(comprobanteId);
            }
            Alert alerta = new Alert(Alert.AlertType.INFORMATION,
                    "El pago y el comprobante " + (documento != null ? documento.numero() : "") + " se han guardado correctamente en el sistema.");
            alerta.setTitle("Pago registrado");
            alerta.setHeaderText("¡Pago y venta guardados con éxito!");
            if (stage != null && stage.isShowing()) {
                alerta.initOwner(stage);
            }
            alerta.showAndWait();
            if (stage != null) {
                stage.close();
            }
        } catch (Exception e) {
            errorDescarga(e.getMessage());
        }
    }

    @FXML
    public void onCerrar() {
        if (stage != null) {
            stage.close();
        }
    }

    @FXML
    public void onImprimir() {
        if (btnImprimir.isDisabled()) return;
        if (documento == null || documento.lineas().isEmpty()) {
            errorImpresion("El comprobante no contiene productos.", stage);
            return;
        }
        Button boton = btnImprimir;
        Label estado = lblEstadoImpresion;
        Stage ventana = stage;
        PrinterJob trabajo = null;
        boton.setDisable(true);
        try {
            trabajo = PrinterJob.createPrinterJob();
            if (trabajo == null) {
                boton.setDisable(false);
                errorImpresion("No hay impresoras disponibles. Configura una impresora en Windows.", ventana);
                return;
            }
            trabajo.getJobSettings().setJobName("Comprobante " + documento.numero());
            com.ejemplo.crudpersona.ui.ImpresionTermica.preparar(trabajo);
            if (!trabajo.showPrintDialog(ventana)) {
                trabajo.cancelJob();
                boton.setDisable(false);
                return;
            }
            com.ejemplo.crudpersona.ui.ImpresionTermica.validar(trabajo);
            var papel = trabajo.getJobSettings().getPageLayout();
            var paginas = ComprobanteImpresion.crearPaginas(documento, papel.getPrintableWidth(), papel.getPrintableHeight());
            PrinterJob trabajoActual = trabajo;
            Task<Void> tarea = new Task<>() {
                @Override protected Void call() {
                    for (var pagina : paginas) {
                        if (!trabajoActual.printPage(papel, pagina)) {
                            throw new IllegalStateException("No se pudo enviar una página a la impresora.");
                        }
                    }
                    if (!trabajoActual.endJob()) {
                        throw new IllegalStateException("No se pudo completar el trabajo de impresión.");
                    }
                    return null;
                }
            };
            estado.setText("Enviando comprobante a la impresora...");
            estado.setVisible(true);
            estado.setManaged(true);
            tarea.setOnSucceeded(event -> {
                boton.setDisable(false);
                estado.setText("Comprobante enviado a " + trabajoActual.getPrinter().getName());
            });
            tarea.setOnFailed(event -> {
                trabajoActual.cancelJob();
                boton.setDisable(false);
                estado.setText("No se pudo imprimir el comprobante.");
                errorImpresion(tarea.getException().getMessage(), ventana);
            });
            Thread hilo = new Thread(tarea, "impresion-comprobante");
            hilo.setDaemon(true);
            hilo.start();
        } catch (RuntimeException e) {
            if (trabajo != null) trabajo.cancelJob();
            boton.setDisable(false);
            errorImpresion(e.getMessage(), ventana);
        }
    }

    private void errorImpresion(String mensaje, Stage ventana) {
        Alert alerta = new Alert(Alert.AlertType.ERROR, mensaje == null ? "Revisa la conexión de la impresora." : mensaje);
        alerta.setHeaderText("No se pudo imprimir");
        if (ventana != null && ventana.isShowing()) alerta.initOwner(ventana);
        alerta.showAndWait();
    }

    private void errorDescarga(String mensaje) {
        Alert alerta = new Alert(Alert.AlertType.ERROR, mensaje == null ? "Revisa la carpeta de destino." : mensaje);
        alerta.setHeaderText("No se pudo descargar el PDF");
        if (stage != null && stage.isShowing()) alerta.initOwner(stage);
        alerta.showAndWait();
    }

    public static class ItemComprobante {
        private final int cantidad;
        private final String descripcion;
        private final BigDecimal precio;

        public ItemComprobante(int cantidad, String descripcion, BigDecimal precio) {
            this.cantidad = cantidad;
            this.descripcion = descripcion;
            this.precio = precio;
        }

        public String getCantidad() {
            return String.valueOf(cantidad);
        }

        public String getDescripcion() {
            return descripcion;
        }

        public String getPrecio() {
            return String.format("S/ %.2f", precio);
        }
    }
}
