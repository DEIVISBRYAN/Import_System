package com.ejemplo.crudpersona.ui;

import com.ejemplo.crudpersona.model.ERQZArchivoDua;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class ArchivosDuaPane extends VBox {
    private final ObservableList<ERQZArchivoDua> archivos = FXCollections.observableArrayList();
    private final BooleanProperty ocupado = new SimpleBooleanProperty(false);
    private final Label estado = new Label();
    private final Label guardado = new Label();
    private Consumer<List<ERQZArchivoDua>> alAdjuntar=archivos->{};

    public ArchivosDuaPane() {
        setSpacing(10); setId("archivosDuaPane");
        Label formatos = new Label("PDF, JPG o PNG · Hasta 10 MB por archivo"); formatos.setWrapText(true); formatos.getStyleClass().add("text-subtle");
        Button adjuntar = boton("Adjuntar fotos o PDF", "fth-paperclip"); adjuntar.setId("adjuntarArchivosDua");
        adjuntar.setOnAction(e -> seleccionar());
        Label cantidad = new Label(); cantidad.getStyleClass().add("chip-info");
        var acciones = new ResponsiveFlowPane(); acciones.getChildren().addAll(adjuntar, cantidad, formatos);
        ListView<ERQZArchivoDua> lista = new ListView<>(archivos); lista.setId("listaArchivosDua"); lista.setPrefHeight(108); lista.setMinHeight(80);
        lista.setFixedCellSize(52); lista.setPlaceholder(new Label("Sin archivos pendientes"));
        lista.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(ERQZArchivoDua archivo, boolean empty) {
                super.updateItem(archivo, empty); setText(null); setGraphic(null);
                if (empty || archivo == null) return;
                Label nombre = new Label(archivo.nombre()); nombre.setMinWidth(0); nombre.setMaxWidth(Double.MAX_VALUE);
                nombre.setTooltip(new Tooltip(archivo.nombre())); HBox.setHgrow(nombre, Priority.ALWAYS);
                Label tipo = new Label(formato(archivo.tipo()) + " · " + tamano(archivo.tamano())); tipo.getStyleClass().add("text-subtle");
                tipo.setMinWidth(Region.USE_PREF_SIZE);
                Button ver = icono("fth-eye", "Ver documento");
                ver.setOnAction(e -> { try { VisorDocumentoDua.mostrar(getScene().getWindow(), archivo); } catch (RuntimeException ex) { mensaje(ex.getMessage(),true); } });
                Button quitar = icono("fth-x", "Quitar archivo pendiente"); quitar.setOnAction(e -> archivos.remove(archivo));
                HBox fila = new HBox(10, new FontIcon(archivo.tipo().equals("application/pdf") ? "fth-file-text" : "fth-image"), nombre, tipo, ver, quitar);
                fila.setAlignment(Pos.CENTER_LEFT); fila.setMinWidth(0); fila.prefWidthProperty().bind(lista.widthProperty().subtract(32)); setGraphic(fila);
            }
        });
        archivos.addListener((javafx.collections.ListChangeListener<ERQZArchivoDua>) change -> {
            cantidad.setText(archivos.size() + (archivos.size() == 1 ? " archivo pendiente" : " archivos pendientes"));
            lista.setPrefHeight(12 + 52 * Math.min(3, archivos.size())); lista.setMinHeight(lista.getPrefHeight());
            lista.setVisible(!archivos.isEmpty()); lista.setManaged(!archivos.isEmpty());
        });
        cantidad.setText("0 archivos pendientes"); lista.setVisible(false); lista.setManaged(false);
        estado.setId("estadoArchivosDua"); estado.setWrapText(true); estado.getStyleClass().add("text-subtle");
        guardado.setWrapText(true); guardado.getStyleClass().add("chip-exito"); guardado.setVisible(false); guardado.setManaged(false);
        disableProperty().bind(ocupado);
        getChildren().addAll(acciones, lista, guardado, estado);
    }
    private void seleccionar() {
        List<File> elegidos = elegir(getScene().getWindow()); if (elegidos == null) return;
        procesar(() -> leer(elegidos), this::adjuntar);
    }
    public void alAdjuntar(Consumer<List<ERQZArchivoDua>> accion){alAdjuntar=Objects.requireNonNull(accion);}
    public void adjuntar(List<ERQZArchivoDua> nuevos){agregar(nuevos);alAdjuntar.accept(List.copyOf(nuevos));}
    public void agregar(List<ERQZArchivoDua> nuevos) {
        var combinado = new ArrayList<>(archivos); combinado.addAll(nuevos);
        if (combinado.size() > ERQZArchivoDua.MAX_ARCHIVOS || combinado.stream().mapToLong(ERQZArchivoDua::tamano).sum() > ERQZArchivoDua.MAX_TOTAL_BYTES)
            throw new IllegalArgumentException("Se admiten hasta 20 archivos y 50 MB en total por DUA.");
        var huellas = new HashSet<String>();
        for (var archivo : combinado) if (!huellas.add(archivo.huella())) throw new IllegalArgumentException("El archivo ya está en la selección: " + archivo.nombre());
        archivos.setAll(combinado); mensaje("Archivos pendientes de guardar.", false);
    }
    public List<ERQZArchivoDua> archivos() { return List.copyOf(archivos); }
    public ObservableList<ERQZArchivoDua> seleccion() { return FXCollections.unmodifiableObservableList(archivos); }
    public BooleanProperty ocupadoProperty() { return ocupado; }
    public javafx.beans.property.ReadOnlyStringProperty mensajeProperty() { return estado.textProperty(); }
    public void limpiar() { archivos.clear(); }
    public void guardado(Long id) {
        limpiar(); guardado.setText("DUA documental #" + id + " guardada en PostgreSQL"); guardado.setVisible(true); guardado.setManaged(true);
        mensaje("El inventario no se ha modificado.", false);
    }
    public void reiniciar() { limpiar(); guardado.setVisible(false); guardado.setManaged(false); estado.setText(""); }
    public void mensaje(String texto, boolean error) {
        estado.setText(texto == null ? "No se pudo completar la acción." : texto);
        estado.getStyleClass().removeAll("validation-error", "text-subtle"); estado.getStyleClass().add(error ? "validation-error" : "text-subtle");
    }
    public <T> void procesar(Supplier<T> trabajo, Consumer<T> resultado) {
        if (ocupado.get()) return;
        ocupado.set(true); mensaje("Procesando archivos…", false);
        Task<T> tarea = new Task<>() { @Override protected T call() { return trabajo.get(); } };
        tarea.setOnSucceeded(e -> {
            ocupado.set(false);
            try { resultado.accept(tarea.getValue()); } catch (RuntimeException ex) { mensaje(ex.getMessage(), true); }
        });
        tarea.setOnFailed(e -> { ocupado.set(false); mensaje(tarea.getException().getMessage(), true); });
        Thread hilo = new Thread(tarea, "documentos-dua"); hilo.setDaemon(true); hilo.start();
    }
    public static List<File> elegir(Window owner) {
        FileChooser chooser = new FileChooser(); chooser.setTitle("Adjuntar documentos de importación DUA");
        chooser.getExtensionFilters().addAll(new FileChooser.ExtensionFilter("PDF y fotos", "*.pdf", "*.PDF", "*.jpg", "*.jpeg", "*.png", "*.JPG", "*.JPEG", "*.PNG"),
                new FileChooser.ExtensionFilter("PDF", "*.pdf", "*.PDF"), new FileChooser.ExtensionFilter("Fotos", "*.jpg", "*.jpeg", "*.png", "*.JPG", "*.JPEG", "*.PNG"));
        return chooser.showOpenMultipleDialog(owner);
    }
    public static List<ERQZArchivoDua> leer(List<File> files) {
        if (files.size() > ERQZArchivoDua.MAX_ARCHIVOS) throw new IllegalArgumentException("Selecciona como máximo 20 archivos.");
        var resultado = new ArrayList<ERQZArchivoDua>(); long total = 0;
        for (File file : files) {
            try { var archivo = ERQZArchivoDua.leer(file.toPath()); resultado.add(archivo); total += archivo.tamano(); }
            catch (java.io.IOException e) { throw new IllegalArgumentException("No se pudo leer " + file.getName() + ".", e); }
            if (total > ERQZArchivoDua.MAX_TOTAL_BYTES) throw new IllegalArgumentException("La selección supera los 50 MB.");
        }
        return List.copyOf(resultado);
    }
    public static void verFoto(Window owner, ERQZArchivoDua archivo) {
        if (!archivo.tipo().startsWith("image/")) throw new IllegalArgumentException("La vista previa solo admite fotos. Descarga el PDF para abrirlo.");
        Dialog<Void> dialog = new Dialog<>(); dialog.setTitle(archivo.nombre()); dialog.initOwner(owner); dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.getDialogPane().getStyleClass().addAll("app-dialog", "wide-dialog");
        Image imagen = new Image(new ByteArrayInputStream(archivo.contenido()), 1200, 1200, true, true);
        if (imagen.isError()) throw new IllegalArgumentException("No se pudo mostrar la foto.");
        ImageView vista = new ImageView(imagen); vista.setId("vistaFotoDua"); vista.setPreserveRatio(true); vista.setFitWidth(740); vista.setFitHeight(470);
        vista.fitWidthProperty().bind(javafx.beans.binding.Bindings.min(740, dialog.getDialogPane().widthProperty().subtract(80)));
        Label nombre = new Label(archivo.nombre()); nombre.setWrapText(true); nombre.getStyleClass().add("modal-title");
        VBox content = new VBox(12, nombre, new StackPane(vista)); content.getStyleClass().add("custom-modal");
        dialog.getDialogPane().setContent(content); dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.CLOSE)).setText("Cerrar"); dialog.showAndWait();
    }
    public static Button boton(String texto, String icono) { Button b = new Button(texto, new FontIcon(icono)); b.getStyleClass().add("btn-secundario"); return b; }
    public static Button icono(String icono, String tooltip) { Button b = new Button(null, new FontIcon(icono)); b.getStyleClass().add("btn-icono"); b.setTooltip(new Tooltip(tooltip)); return b; }
    public static String formato(String mime) { return mime.equals("application/pdf") ? "PDF" : mime.equals("image/png") ? "PNG" : "JPG"; }
    public static String tamano(long bytes) { return bytes < 1024 * 1024 ? String.format(Locale.ROOT, "%.1f KB", bytes / 1024d) : String.format(Locale.ROOT, "%.1f MB", bytes / (1024d * 1024d)); }
}
