package com.ejemplo.crudpersona.controller;

import com.ejemplo.crudpersona.model.CajaTurno;
import com.ejemplo.crudpersona.repository.CajaTurnoRepository;
import com.ejemplo.crudpersona.service.ERQZDatabaseDateNormalizer;
import com.ejemplo.crudpersona.service.RPLCCajaServiceI;
import com.ejemplo.crudpersona.service.RPLCTerminalSesion;
import com.ejemplo.crudpersona.service.ERQZReporteTabular;
import com.ejemplo.crudpersona.ui.ExportarMenu;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

@Component
public class ERQZCajaController {

    private final CajaTurnoRepository cajaTurnoRepository;
    private final ERQZDatabaseDateNormalizer dateNormalizer;
    private final RPLCCajaServiceI cajaService;
    private final RPLCTerminalSesion sesion;

    private CajaTurno turnoActual;
    private final ObservableList<CajaTurno> historial = FXCollections.observableArrayList();

    @FXML private Label lblEstadoCaja;
    @FXML private ExportarMenu exportarReporte;
    @FXML private TextField txtMontoApertura;
    @FXML private Button btnAbrirCaja;
    @FXML private Label lblApertura;
    @FXML private Label lblEfectivo;
    @FXML private Label lblYapePlin;
    @FXML private Label lblTarjeta;
    @FXML private Label lblMixto;
    @FXML private TextField txtEfectivoContado;
    @FXML private Label lblCuadre;
    @FXML private Label lblEsperado;
    @FXML private Button btnCerrarCaja;
    @FXML private TableView<CajaTurno> tablaHistorial;
    @FXML private TableColumn<CajaTurno, String> colFecha;
    @FXML private TableColumn<CajaTurno, String> colEstado;
    @FXML private TableColumn<CajaTurno, String> colEsperado;
    @FXML private TableColumn<CajaTurno, String> colDiferencia;

    public ERQZCajaController(CajaTurnoRepository cajaTurnoRepository, ERQZDatabaseDateNormalizer dateNormalizer,
                         RPLCCajaServiceI cajaService, RPLCTerminalSesion sesion) {
        this.cajaTurnoRepository = cajaTurnoRepository;
        this.dateNormalizer = dateNormalizer;
        this.cajaService = cajaService;
        this.sesion = sesion;
    }

    @FXML
    public void initialize() {
        exportarReporte.setReporte(this::reporteCaja);
        dateNormalizer.normalize();
        configurarTabla();
        txtEfectivoContado.textProperty().addListener((obs, oldVal, newVal) -> validarCuadre());
        cargarTurnoActual();
        cargarHistorial();
    }

    public ERQZReporteTabular reporteCaja() {
        return new ERQZReporteTabular("Historial de caja " + sesion.numeroCaja(), "Todos los turnos de esta caja. Los campos de arqueo permanecen vacíos mientras el turno está abierto.",
                java.util.List.of("Apertura", "Cierre", "Estado", "Inicial PEN", "Efectivo PEN", "Yape Plin PEN", "Tarjeta PEN", "Mixto PEN", "Esperado PEN", "Contado PEN", "Diferencia PEN"),
                tablaHistorial.getItems().stream().map(c -> ERQZReporteTabular.fila(c.getFechaApertura(), c.getFechaCierre(), c.getEstado(), c.getMontoApertura(),
                        c.getMontoEfectivoVentas(), c.getMontoYapePlin(), c.getMontoTarjeta(), c.getMontoMixto(),
                        "ABIERTA".equals(c.getEstado()) ? decimal(c.getMontoApertura()).add(decimal(c.getMontoEfectivoVentas())) : c.getMontoEsperado(),
                        "ABIERTA".equals(c.getEstado()) ? null : c.getMontoContado(), "ABIERTA".equals(c.getEstado()) ? null : c.getDiferencia())).toList());
    }

    @FXML
    public void onActualizar() {
        dateNormalizer.normalize();
        cargarTurnoActual();
        cargarHistorial();
    }

    @FXML
    public void onAbrirCaja() {
        if (turnoActual != null) {
            mostrarInfo("Caja abierta", "Ya existe una caja abierta.");
            return;
        }

        BigDecimal apertura = leerDecimal(txtMontoApertura.getText(), null);
        if (apertura == null || apertura.compareTo(BigDecimal.ZERO) < 0) {
            mostrarError("Monto inválido", "Ingresa un monto de apertura válido.");
            return;
        }

        try {
            turnoActual = cajaService.abrir(sesion.numeroCaja(), apertura, sesion.usuarioId());
        } catch (Exception e) { mostrarError("No se pudo abrir la caja", e.getMessage()); return; }
        cargarTurnoActual();
        cargarHistorial();
    }

    @FXML
    public void onCerrarCaja() {
        if (turnoActual == null) {
            mostrarInfo("Sin caja abierta", "Abre una caja antes de cerrarla.");
            return;
        }

        BigDecimal contado = leerDecimal(txtEfectivoContado.getText(), null);
        if (contado == null) {
            mostrarError("Monto inválido", "Ingresa el efectivo contado.");
            return;
        }

        Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION);
        confirmacion.setHeaderText("Cerrar caja");
        confirmacion.setContentText("Se bloquearán nuevas ventas hasta abrir otro turno. ¿Deseas continuar?");
        if (confirmacion.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            return;
        }

        try {
            turnoActual = cajaService.cerrar(sesion.numeroCaja(), turnoActual.getId(), contado);
        } catch (Exception e) { mostrarError("No se pudo cerrar la caja", e.getMessage()); return; }

        mostrarInfo("Caja cerrada", "Diferencia: " + moneda(turnoActual.getDiferencia()));
        turnoActual = null;
        cargarTurnoActual();
        cargarHistorial();
    }

    @FXML
    public void onVerReporte() {
        CajaTurno caja = turnoActual != null ? turnoActual : tablaHistorial.getSelectionModel().getSelectedItem();
        if (caja == null) {
            mostrarInfo("Sin reporte", "Selecciona un turno del historial o abre una caja.");
            return;
        }

        String reporte = """
                Apertura: %s
                Estado: %s
                Monto apertura: %s
                Efectivo: %s
                Yape / Plin: %s
                Tarjeta: %s
                Mixto: %s
                Esperado en caja: %s
                Contado: %s
                Diferencia: %s
                """.formatted(
                fecha(caja.getFechaApertura()),
                texto(caja.getEstado()),
                moneda(caja.getMontoApertura()),
                moneda(caja.getMontoEfectivoVentas()),
                moneda(caja.getMontoYapePlin()),
                moneda(caja.getMontoTarjeta()),
                moneda(caja.getMontoMixto()),
                moneda("ABIERTA".equals(caja.getEstado()) ? decimal(caja.getMontoApertura()).add(decimal(caja.getMontoEfectivoVentas())) : caja.getMontoEsperado()),
                moneda(caja.getMontoContado()),
                moneda(caja.getDiferencia())
        );

        Alert alert = new Alert(Alert.AlertType.INFORMATION, reporte);
        alert.setHeaderText("Reporte de caja");
        alert.showAndWait();
    }

    private void configurarTabla() {
        colFecha.setCellValueFactory(data -> new SimpleStringProperty(fecha(data.getValue().getFechaApertura())));
        colEstado.setCellValueFactory(data -> new SimpleStringProperty(texto(data.getValue().getEstado())));
        colEsperado.setCellValueFactory(data -> new SimpleStringProperty(moneda(data.getValue().getMontoEsperado())));
        colDiferencia.setCellValueFactory(data -> new SimpleStringProperty(moneda(data.getValue().getDiferencia())));
        tablaHistorial.setItems(historial);
    }

    private void cargarTurnoActual() {
        Optional<CajaTurno> optTurno = cajaTurnoRepository.findByNumeroCajaAndEstado(sesion.numeroCaja(), "ABIERTA");
        turnoActual = optTurno.orElse(null);

        if (turnoActual == null) {
            lblEstadoCaja.setText("Caja " + sesion.numeroCaja() + " cerrada");
            lblEstadoCaja.getStyleClass().setAll("chip-alerta");
            lblApertura.setText("S/ 0.00");
            lblEfectivo.setText("S/ 0.00");
            lblYapePlin.setText("S/ 0.00");
            lblTarjeta.setText("S/ 0.00");
            lblMixto.setText("S/ 0.00");
            txtEfectivoContado.setText("0.00");
            lblEsperado.setText("S/ 0.00 (apertura más ventas en efectivo)");
            btnAbrirCaja.setDisable(false);
            btnCerrarCaja.setDisable(true);
            validarCuadre();
            return;
        }

        lblEstadoCaja.setText("Caja " + sesion.numeroCaja() + " abierta");
        lblEstadoCaja.getStyleClass().setAll("chip-exito");
        lblApertura.setText(moneda(turnoActual.getMontoApertura()));
        lblEfectivo.setText(moneda(turnoActual.getMontoEfectivoVentas()));
        lblYapePlin.setText(moneda(turnoActual.getMontoYapePlin()));
        lblTarjeta.setText(moneda(turnoActual.getMontoTarjeta()));
        lblMixto.setText(moneda(turnoActual.getMontoMixto()));
        txtEfectivoContado.setText(esperadoEnCaja().toPlainString());
        lblEsperado.setText(moneda(esperadoEnCaja()) + " (apertura más ventas en efectivo)");
        btnAbrirCaja.setDisable(true);
        btnCerrarCaja.setDisable(false);
        validarCuadre();
    }

    private void cargarHistorial() {
        historial.setAll(cajaTurnoRepository.findByNumeroCajaOrderByFechaAperturaDesc(sesion.numeroCaja()));
    }

    private void validarCuadre() {
        BigDecimal contado = leerDecimal(txtEfectivoContado.getText(), null);
        if (turnoActual == null || contado == null) {
            lblCuadre.setText("Pendiente");
            lblCuadre.getStyleClass().setAll("chip-alerta");
            return;
        }

        BigDecimal diferencia = contado.subtract(esperadoEnCaja());
        if (diferencia.compareTo(BigDecimal.ZERO) == 0) {
            lblCuadre.setText("Cuadra");
            lblCuadre.getStyleClass().setAll("chip-exito");
        } else {
            lblCuadre.setText("Diferencia " + moneda(diferencia));
            lblCuadre.getStyleClass().setAll("chip-peligro");
        }
    }

    private BigDecimal esperadoEnCaja() {
        if (turnoActual == null) {
            return BigDecimal.ZERO;
        }
        return decimal(turnoActual.getMontoApertura()).add(decimal(turnoActual.getMontoEfectivoVentas()));
    }

    private BigDecimal leerDecimal(String texto, BigDecimal fallback) {
        try {
            return new BigDecimal(texto.trim().replace(',', '.'));
        } catch (Exception e) {
            return fallback;
        }
    }

    private BigDecimal decimal(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private String moneda(BigDecimal valor) {
        return String.format("S/ %.2f", decimal(valor));
    }

    private String texto(Object valor) {
        return valor == null ? "-" : valor.toString();
    }

    private String fecha(LocalDateTime fecha) {
        return fecha == null ? "-" : fecha.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
    }

    private void mostrarInfo(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, mensaje);
        alert.setHeaderText(titulo);
        alert.showAndWait();
    }

    private void mostrarError(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.ERROR, mensaje);
        alert.setHeaderText(titulo);
        alert.showAndWait();
    }
}
