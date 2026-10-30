package com.ejemplo.crudpersona.controller;

import com.ejemplo.crudpersona.model.Producto;
import com.ejemplo.crudpersona.repository.ProductoRepository;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ButtonType;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

@Component
public class DBQPTipoCambioController {

    private final ProductoRepository productoRepository;
    private final com.ejemplo.crudpersona.service.DBQPTipoCambioServiceI cambios;

    private BigDecimal tipoCambioActual = new BigDecimal("3.75");

    @FXML private Label lblTipoCambioActual;
    @FXML private TextField txtNuevoTipoCambio;
    @FXML private Button btnAplicar;
    @FXML private Button btnGuardarManual;
    @FXML private Label lblFuenteCambio;
    @FXML private Label lblEstadoCambio;
    @FXML private TableView<Producto> tablaProductos;
    @FXML private TableColumn<Producto, String> colProducto;
    @FXML private TableColumn<Producto, String> colAnterior;
    @FXML private TableColumn<Producto, String> colNuevo;
    @FXML private TableColumn<Producto, String> colDif;

    public DBQPTipoCambioController(ProductoRepository productoRepository, com.ejemplo.crudpersona.service.DBQPTipoCambioServiceI cambios) {
        this.productoRepository = productoRepository;
        this.cambios = cambios;
    }

    @FXML
    public void initialize() {
        tipoCambioActual = cambios.actual();
        lblTipoCambioActual.setText(tipoCambioActual.toString());
        txtNuevoTipoCambio.setText(tipoCambioActual.toPlainString());
        lblFuenteCambio.setText("Ingreso manual · PEN por USD");
        lblEstadoCambio.setText("Los precios se conservan al guardar la tasa. No se consulta Internet.");

        colProducto.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getDescripcion()));
        colAnterior.setCellValueFactory(data -> new SimpleStringProperty("S/ " + data.getValue().getPrecioVentaPen().toString()));
        colNuevo.setCellValueFactory(data -> {
            BigDecimal nuevoPrecio = precioVistaPrevia(data.getValue().getPrecioVentaPen());
            return new SimpleStringProperty("S/ " + nuevoPrecio.toString());
        });
        colDif.setCellValueFactory(data -> {
            BigDecimal actual = data.getValue().getPrecioVentaPen();
            BigDecimal nuevo = precioVistaPrevia(actual);
            BigDecimal dif = nuevo.subtract(actual);
            String signo = dif.compareTo(BigDecimal.ZERO) > 0 ? "+" : "";
            return new SimpleStringProperty(signo + "S/ " + dif.toString());
        });

        txtNuevoTipoCambio.textProperty().addListener((obs, old, value) -> tablaProductos.refresh());
        cargarProductos();
    }

    @FXML public void onGuardarManual() {
        try {
            cambios.guardarManual(tipoCambioActual,tasaIngresada());
            refrescarTasa();
            lblEstadoCambio.setText("Tipo de cambio guardado manualmente. Los precios de venta no cambiaron.");
        } catch(RuntimeException ex) {
            Alert alerta=new Alert(Alert.AlertType.ERROR,ex instanceof NumberFormatException?"Tipo de cambio invalido.":ex.getMessage());
            alerta.initOwner(btnGuardarManual.getScene().getWindow());alerta.show();
        }
    }
    private void refrescarTasa() {
        boolean editada = !txtNuevoTipoCambio.getText().equals(tipoCambioActual.toPlainString());
        tipoCambioActual = cambios.actual(); lblTipoCambioActual.setText(tipoCambioActual.toPlainString());
        if (!editada) txtNuevoTipoCambio.setText(tipoCambioActual.toPlainString());
        tablaProductos.refresh();
    }

    private void cargarProductos() {
        List<Producto> productos = productoRepository.findAll();
        tablaProductos.setItems(FXCollections.observableArrayList(productos));
    }

    private BigDecimal recalcularPrecio(BigDecimal precioActual, BigDecimal nuevoTipoCambio) {
        return com.ejemplo.crudpersona.service.DBQPTipoCambioServiceI.recalcular(precioActual, tipoCambioActual, nuevoTipoCambio);
    }

    private BigDecimal tasaIngresada() {
        BigDecimal tasa = new BigDecimal(txtNuevoTipoCambio.getText().trim().replace(',', '.'));
        if (tasa.signum() <= 0) throw new IllegalArgumentException("Ingresa un tipo de cambio mayor que cero.");
        return tasa;
    }

    private BigDecimal precioVistaPrevia(BigDecimal actual) {
        try { return recalcularPrecio(actual, tasaIngresada()); }
        catch (IllegalArgumentException e) { return actual; }
    }

    @FXML
    public void onAplicar() {
        try {
            BigDecimal nuevoTC = tasaIngresada();
            Alert confirmar = new Alert(Alert.AlertType.CONFIRMATION,
                    "Se recalcularán los precios de venta usando " + nuevoTC + ". Las ventas y comprobantes anteriores no cambiarán.", ButtonType.OK, ButtonType.CANCEL);
            confirmar.initOwner(btnAplicar.getScene().getWindow()); confirmar.setHeaderText("¿Aplicar el dólar a los precios?");
            if (confirmar.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            cambios.aplicar(tipoCambioActual, nuevoTC);
            tipoCambioActual = nuevoTC;
            lblTipoCambioActual.setText(nuevoTC.toString());
            cargarProductos();
            lblEstadoCambio.setText("Tipo de cambio manual y precios actualizados con confirmacion.");

            Alert alert = new Alert(Alert.AlertType.INFORMATION, "Precios actualizados exitosamente. Tipo de cambio: " + nuevoTC);
            alert.setHeaderText("Actualización Completada");
            alert.show();

        } catch (RuntimeException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR, e instanceof NumberFormatException ? "Tipo de cambio inválido" : e.getMessage());
            alert.show();
        }
    }
}
