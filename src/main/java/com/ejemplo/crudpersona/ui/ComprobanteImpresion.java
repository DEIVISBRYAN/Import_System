package com.ejemplo.crudpersona.ui;

import com.ejemplo.crudpersona.model.ComprobanteDocumento;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ComprobanteImpresion {
    private ComprobanteImpresion() {
    }

    public static List<VBox> crearPaginas(ComprobanteDocumento documento, double ancho, double alto) {
        if (ancho < 100 || alto < 220) {
            throw new IllegalArgumentException("El papel seleccionado es demasiado pequeño para el comprobante.");
        }
        List<VBox> paginas = new ArrayList<>();
        VBox pagina = nuevaPagina(documento, ancho, alto);
        double encabezado = pagina.prefHeight(ancho);
        int filas = 0;
        for (var linea : documento.lineas()) {
            GridPane fila = fila(Integer.toString(linea.cantidad()), linea.descripcion(),
                    dinero(linea.precio()), dinero(linea.importe()), ancho, false);
            pagina.getChildren().add(fila);
            pagina.applyCss();
            if (pagina.prefHeight(ancho) > alto - 25) {
                pagina.getChildren().remove(fila);
                if (filas == 0) {
                    throw new IllegalArgumentException("Una descripción no cabe en el papel. Selecciona un formato mayor.");
                }
                paginas.add(pagina);
                pagina = nuevaPagina(documento, ancho, alto);
                pagina.getChildren().add(fila);
                pagina.applyCss();
                if (pagina.prefHeight(ancho) > alto - 25 || encabezado >= alto - 25) {
                    throw new IllegalArgumentException("Selecciona un papel más alto para este comprobante.");
                }
                filas = 0;
            }
            filas++;
        }
        VBox totales = new VBox(6,
                etiqueta("Operación gravada: S/ " + dinero(documento.subtotal()), ancho, false),
                etiqueta("IGV: S/ " + dinero(documento.igv()), ancho, false),
                etiqueta("TOTAL: S/ " + dinero(documento.total()), ancho, true),
                etiqueta("Estado: " + documento.estado(), ancho, false),
                etiqueta("ACEPTADO".equals(documento.estado()) ? "" : "Documento interno. No enviado ni validado por SUNAT.", ancho, false));
        pagina.getChildren().add(totales);
        pagina.applyCss();
        if (pagina.prefHeight(ancho) > alto - 25) {
            pagina.getChildren().remove(totales);
            paginas.add(pagina);
            pagina = nuevaPagina(documento, ancho, alto);
            pagina.getChildren().add(totales);
            pagina.applyCss();
            if (pagina.prefHeight(ancho) > alto - 25) {
                throw new IllegalArgumentException("Selecciona un papel más alto para los totales del comprobante.");
            }
        }
        paginas.add(pagina);
        for (int i = 0; i < paginas.size(); i++) {
            VBox hoja = paginas.get(i);
            hoja.getChildren().add(etiqueta("Página " + (i + 1) + " de " + paginas.size(), ancho, false));
            hoja.applyCss();
            hoja.layout();
        }
        return paginas;
    }

    private static VBox nuevaPagina(ComprobanteDocumento d, double ancho, double alto) {
        VBox pagina = new VBox(7);
        pagina.setStyle("-fx-background-color: white; -fx-padding: 6;");
        pagina.setPrefWidth(ancho);
        pagina.setMinWidth(ancho); pagina.setMaxWidth(ancho);
        pagina.getChildren().addAll(
                etiqueta(d.empresa(), ancho, true),
                etiqueta("RUC: " + (d.rucEmpresa().isBlank() ? "Por configurar" : d.rucEmpresa()), ancho, false),
                etiqueta(d.tipo() + " " + d.numero(), ancho, true),
                etiqueta("Fecha: " + d.fecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), ancho, false),
                etiqueta("Cliente: " + d.cliente(), ancho, false),
                etiqueta("Documento: " + d.documentoCliente(), ancho, false),
                etiqueta("Pago: " + d.metodoPago(), ancho, false),
                fila("Cant.", "Producto", "P. unit.", "Importe", ancho, true));
        new Scene(pagina, ancho, alto);
        pagina.applyCss();
        return pagina;
    }

    private static GridPane fila(String cantidad, String descripcion, String precio, String importe,
                                 double ancho, boolean encabezado) {
        GridPane fila = new GridPane();
        fila.setHgap(5);
        if (ancho < 250) {
            ColumnConstraints detalle = new ColumnConstraints(); detalle.setPercentWidth(70); detalle.setMinWidth(0);
            ColumnConstraints total = new ColumnConstraints(); total.setPercentWidth(30); total.setMinWidth(0);
            fila.getColumnConstraints().addAll(detalle, total);
            if (encabezado) {
                fila.add(etiqueta("Producto / Cant. x precio", ancho, true), 0, 0);
                fila.add(etiqueta("Importe", ancho, true), 1, 0);
            } else {
                fila.add(etiqueta(descripcion, ancho, true), 0, 0, 2, 1);
                fila.add(etiqueta(cantidad + " x S/ " + precio, ancho, false), 0, 1);
                Label monto = etiqueta("S/ " + importe, ancho, false); monto.setAlignment(Pos.CENTER_RIGHT);
                fila.add(monto, 1, 1);
            }
            return fila;
        }
        for (int porcentaje : new int[]{12, 46, 21, 21}) {
            ColumnConstraints columna = new ColumnConstraints();
            columna.setPercentWidth(porcentaje);
            columna.setMinWidth(0);
            fila.getColumnConstraints().add(columna);
        }
        fila.add(etiqueta(cantidad, ancho, encabezado), 0, 0);
        fila.add(etiqueta(descripcion, ancho, encabezado), 1, 0);
        Label unitario = etiqueta(precio, ancho, encabezado);
        Label monto = etiqueta(importe, ancho, encabezado);
        unitario.setAlignment(Pos.CENTER_RIGHT);
        monto.setAlignment(Pos.CENTER_RIGHT);
        fila.add(unitario, 2, 0);
        fila.add(monto, 3, 0);
        return fila;
    }

    private static Label etiqueta(String texto, double ancho, boolean negrita) {
        Label label = new Label(texto == null ? "" : texto);
        label.setWrapText(true);
        label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setStyle("-fx-text-fill: black; -fx-font-family: 'Segoe UI'; -fx-font-size: "
                + (ancho < 250 ? 9 : 11) + "px; -fx-font-weight: " + (negrita ? "bold" : "normal") + ";");
        return label;
    }

    private static String dinero(BigDecimal valor) {
        return String.format(Locale.ROOT, "%.2f", valor == null ? BigDecimal.ZERO : valor);
    }
}
