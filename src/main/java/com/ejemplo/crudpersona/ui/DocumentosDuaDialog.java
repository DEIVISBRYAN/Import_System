package com.ejemplo.crudpersona.ui;

import com.ejemplo.crudpersona.service.DBQPDocumentosDuaServiceI;
import com.ejemplo.crudpersona.service.DBQPDocumentosDuaServiceI.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import static com.ejemplo.crudpersona.ui.ArchivosDuaPane.*;

public final class DocumentosDuaDialog {
    private DocumentosDuaDialog() {}

    public static void mostrar(Window owner, DBQPDocumentosDuaServiceI servicio, Long seleccionado) {
        mostrar(owner,servicio,seleccionado,null);
    }
    public static void mostrar(Window owner, DBQPDocumentosDuaServiceI servicio, Long seleccionado, java.util.function.Consumer<Documento> usar) {
        Dialog<Void> dialog = new Dialog<>(); dialog.setTitle("Documentos DUA guardados"); dialog.initOwner(owner); dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.getDialogPane().getStyleClass().addAll("app-dialog", "wide-dialog");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        Button cerrar = (Button) dialog.getDialogPane().lookupButton(ButtonType.CLOSE); cerrar.setText("Cerrar");
        Label titulo = new Label("Documentos DUA guardados"); titulo.getStyleClass().add("modal-title");
        TextField buscar = new TextField(); buscar.setId("buscarDocumentosDua"); buscar.setPromptText("Nombre de archivo, DUA o proveedor"); buscar.setPrefWidth(400);
        Button filtrar = boton("Buscar", "fth-search"), actualizar = icono("fth-refresh-cw", "Actualizar documentos");
        TableView<Documento> documentos = tabla("documentosDua", 205);
        columna(documentos, "Documento", 250, Documento::referencia);
        columna(documentos, "DUA", 140, d -> d.dua().isBlank() ? "Sin digitar" : d.dua());
        columna(documentos, "Estado", 135, d -> d.compraId() != null ? "Aprobada / stock" : d.borradorId()!=null ? "Pendiente" : "Solo documento");
        columna(documentos, "Archivos", 70, d -> "" + d.archivos());
        columna(documentos, "Fecha", 140, d -> d.fecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        Label proveedor = new Label(); proveedor.setWrapText(true); proveedor.getStyleClass().add("text-subtle");
        TableView<Adjunto> archivos = tabla("archivosDuaGuardados", 175);
        columna(archivos, "Archivo", 400, Adjunto::nombre); columna(archivos, "Formato", 85, a -> formato(a.tipo()));
        columna(archivos, "Tamaño", 100, a -> tamano(a.tamano())); columna(archivos, "Fecha", 150, a -> a.fecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")));
        ArchivosDuaPane progreso = new ArchivosDuaPane(); progreso.setVisible(false); progreso.setManaged(false);
        Label trabajo = new Label(); trabajo.setWrapText(true); trabajo.textProperty().bind(progreso.mensajeProperty()); trabajo.getStyleClass().add("text-subtle");
        Label estado = new Label(); estado.setWrapText(true); estado.setId("estadoDocumentosDua"); estado.getStyleClass().add("text-subtle");
        Label error = new Label(); error.setWrapText(true); error.setId("errorDocumentosDua"); error.getStyleClass().add("validation-error");
        Button adjuntar = boton("Adjuntar más", "fth-paperclip"), foto = boton("Ver documento", "fth-eye"), descargar = boton("Descargar", "fth-download");
        Button eliminar=boton("Eliminar archivo","fth-trash-2"), seleccionar=boton("Usar en compra pendiente","fth-check");
        eliminar.setId("eliminarArchivoDua"); seleccionar.setId("usarDocumentoDua"); seleccionar.setVisible(usar!=null); seleccionar.setManaged(usar!=null);
        Button vincular = boton("Vincular a compra", "fth-link"); vincular.setId("vincularDocumentoDua"); descargar.setId("descargarArchivoDua"); foto.setId("verFotoDua"); adjuntar.setId("adjuntarMasDua");
        Runnable habilitar = () -> {
            boolean busy = progreso.ocupadoProperty().get(); var d = documentos.getSelectionModel().getSelectedItem(); var a = archivos.getSelectionModel().getSelectedItem();
            adjuntar.setDisable(busy || d == null || d.compraId()!=null); vincular.setDisable(busy || d == null || d.compraId() != null || d.borradorId()!=null);
            eliminar.setDisable(busy || a==null || d==null || d.compraId()!=null);
            seleccionar.setDisable(busy || d==null || d.compraId()!=null || d.borradorId()!=null);
            foto.setDisable(busy || a == null); descargar.setDisable(busy || a == null);
            cerrar.setDisable(busy); filtrar.setDisable(busy); actualizar.setDisable(busy); documentos.setDisable(busy);
        };
        Runnable cargarArchivos = () -> {
            archivos.getItems().clear(); var d = documentos.getSelectionModel().getSelectedItem(); proveedor.setText("");
            if (d != null) {
                archivos.getItems().setAll(servicio.archivos(d.id())); if (!archivos.getItems().isEmpty()) archivos.getSelectionModel().selectFirst();
                proveedor.setText(d.compraId() != null ? d.proveedor() + " · Compra #" + d.compraId() : d.borradorId()!=null
                        ? d.proveedor()+" · Pendiente #"+d.borradorId()+" · Sin ingreso a stock" : "Sin compra vinculada · No modifica el inventario");
            }
            habilitar.run();
        };
        Runnable cargar = () -> {
            try {
                error.setText(""); var previo = documentos.getSelectionModel().getSelectedItem(); Long id = previo == null ? seleccionado : previo.id();
                documentos.getItems().setAll(servicio.listar(buscar.getText()));
                documentos.getItems().stream().filter(d -> d.id().equals(id)).findFirst().ifPresent(documentos.getSelectionModel()::select);
                if (documentos.getSelectionModel().isEmpty() && !documentos.getItems().isEmpty()) documentos.getSelectionModel().selectFirst();
                cargarArchivos.run(); estado.setText(documentos.getItems().size() + " documentos encontrados · Hasta 200 resultados recientes");
            } catch (RuntimeException ex) { error.setText(ex.getMessage()); }
        };
        documentos.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> { try { cargarArchivos.run(); } catch (RuntimeException ex) { error.setText(ex.getMessage()); } });
        archivos.getSelectionModel().selectedItemProperty().addListener((o,a,b) -> habilitar.run());
        progreso.ocupadoProperty().addListener((o,a,b) -> habilitar.run());
        filtrar.setOnAction(e -> cargar.run()); actualizar.setOnAction(e -> cargar.run()); buscar.setOnAction(e -> cargar.run());
        adjuntar.setOnAction(e -> {
            var documento = documentos.getSelectionModel().getSelectedItem(); if (documento == null) return;
            var files = elegir(dialog.getDialogPane().getScene().getWindow()); if (files == null) return;
            error.setText("");
            progreso.procesar(() -> { servicio.adjuntar(documento.id(), leer(files)); return true; }, ok -> {
                cargar.run(); progreso.mensaje("Archivos guardados en PostgreSQL.", false);
            });
        });
        foto.setOnAction(e -> {
            var archivo = archivos.getSelectionModel().getSelectedItem(); if (archivo == null) return;
            progreso.procesar(() -> servicio.descargar(archivo.id()), fotoGuardada -> {
                progreso.mensaje("", false); VisorDocumentoDua.mostrar(dialog.getDialogPane().getScene().getWindow(), fotoGuardada);
            });
        });
        seleccionar.setOnAction(e -> { var d=documentos.getSelectionModel().getSelectedItem(); if(d!=null && usar!=null) { usar.accept(d); dialog.close(); } });
        eliminar.setOnAction(e -> {
            var a=archivos.getSelectionModel().getSelectedItem(); if(a==null) return;
            Alert confirmar=new Alert(Alert.AlertType.CONFIRMATION,"Se eliminará de la base de datos: "+a.nombre(),ButtonType.OK,ButtonType.CANCEL);
            confirmar.initOwner(dialog.getDialogPane().getScene().getWindow()); confirmar.setHeaderText("¿Eliminar archivo pendiente?");
            if(confirmar.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK) return;
            progreso.procesar(()->{servicio.eliminarArchivo(a.id());return true;},ok->{cargar.run();progreso.mensaje("Archivo eliminado. El stock no cambió.",false);});
        });
        descargar.setOnAction(e -> {
            var archivo = archivos.getSelectionModel().getSelectedItem(); if (archivo == null) return;
            try {
                String extension = archivo.nombre().substring(archivo.nombre().lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
                FileChooser selector = new FileChooser(); selector.setTitle("Descargar documento DUA"); selector.setInitialFileName(archivo.nombre());
                selector.getExtensionFilters().add(new FileChooser.ExtensionFilter(formato(archivo.tipo()), "*." + extension));
                var file = selector.showSaveDialog(dialog.getDialogPane().getScene().getWindow()); if (file == null) return;
                Path destino = file.toPath();
                if (!file.getName().toLowerCase(Locale.ROOT).endsWith("." + extension)) {
                    destino = destino.resolveSibling(file.getName() + "." + extension);
                    if (Files.exists(destino)) {
                        Alert alerta = new Alert(Alert.AlertType.CONFIRMATION, "El archivo ya existe. ¿Reemplazarlo?", ButtonType.OK, ButtonType.CANCEL);
                        alerta.initOwner(dialog.getDialogPane().getScene().getWindow());
                        if (alerta.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
                    }
                }
                Path elegido = destino; error.setText("");
                progreso.procesar(() -> {
                    try { ArchivoDescarga.guardarEn(elegido, servicio.descargar(archivo.id()).contenido()); return true; }
                    catch (java.io.IOException ex) { throw new IllegalStateException("No se pudo guardar el archivo: " + ex.getMessage(), ex); }
                }, ok -> progreso.mensaje("Archivo descargado.", false));
            } catch (Exception ex) { error.setText("No se pudo descargar: " + ex.getMessage()); }
        });
        vincular.setOnAction(e -> {
            var documento = documentos.getSelectionModel().getSelectedItem(); if (documento == null) return;
            try {
                List<Compra> compras = servicio.comprasSinDocumento();
                if (compras.isEmpty()) throw new IllegalArgumentException("Primero registra una compra DUA sin expediente documental.");
                ChoiceDialog<Compra> elegir = new ChoiceDialog<>(compras.get(0), compras);
                elegir.initOwner(dialog.getDialogPane().getScene().getWindow()); elegir.setTitle("Vincular DUA documental"); elegir.setHeaderText(documento.referencia());
                elegir.setContentText("Compra registrada");
                elegir.showAndWait().ifPresent(compra -> { servicio.vincular(documento.id(), compra.id()); cargar.run(); estado.setText("Documento vinculado. El stock no se modifica nuevamente."); });
            } catch (RuntimeException ex) { error.setText(ex.getMessage()); }
        });
        var filtros = new ResponsiveFlowPane(); filtros.getChildren().addAll(buscar, filtrar, actualizar);
        var acciones = new ResponsiveFlowPane(); acciones.getChildren().addAll(foto, descargar, adjuntar, eliminar, seleccionar, vincular);
        VBox content = new VBox(12, titulo, filtros, documentos, proveedor, new Separator(), archivos, acciones, trabajo, estado, error);
        content.getStyleClass().add("custom-modal"); dialog.getDialogPane().setContent(content); cargar.run(); dialog.showAndWait();
    }
    private static <T> TableView<T> tabla(String id, int alto) {
        TableView<T> tabla = new TableView<>(); tabla.setId(id); tabla.setPrefHeight(alto); tabla.setMinHeight(100);
        tabla.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN); tabla.setFixedCellSize(43);
        tabla.getStyleClass().addAll("tabla", "fit-columns", "orders-table"); tabla.setPlaceholder(new Label("Sin documentos")); return tabla;
    }
    private static <T> void columna(TableView<T> tabla, String nombre, int ancho, Function<T,String> texto) {
        TableColumn<T,String> col = new TableColumn<>(nombre); col.setPrefWidth(ancho); col.setMinWidth(60);
        col.setCellValueFactory(d -> new SimpleStringProperty(texto.apply(d.getValue())));
        col.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) { super.updateItem(value, empty); setText(empty ? null : value); setTooltip(empty ? null : new Tooltip(value)); }
        }); tabla.getColumns().add(col);
    }
}
