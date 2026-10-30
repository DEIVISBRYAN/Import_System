package com.ejemplo.crudpersona.ui;

import com.ejemplo.crudpersona.service.DBQPReporteExportador;
import com.ejemplo.crudpersona.service.ERQZReporteTabular;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import org.kordamp.ikonli.javafx.FontIcon;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Supplier;

public final class ExportarMenu extends MenuButton {
    private Supplier<ERQZReporteTabular> reporte;
    public ExportarMenu() {
        super("Exportar", new FontIcon("fth-download")); getStyleClass().add("btn-secundario");
        for (var formato : DBQPReporteExportador.Formato.values()) {
            var item = new MenuItem(formato.nombre + " (." + formato.extension + ")");
            item.setId("exportar" + formato.name()); item.setOnAction(e -> exportar(formato)); getItems().add(item);
        }
    }
    public void setReporte(Supplier<ERQZReporteTabular> reporte) { this.reporte = reporte; }
    public ERQZReporteTabular prepararReporte() {
        if (reporte == null) throw new IllegalStateException("No hay un reporte configurado.");
        return reporte.get();
    }
    private void exportar(DBQPReporteExportador.Formato formato) {
        var owner = getScene().getWindow();
        try {
            var datos = prepararReporte();
            if (datos.filas().isEmpty()) { aviso(Alert.AlertType.INFORMATION, "Sin registros para exportar con los filtros actuales."); return; }
            var chooser = new FileChooser(); chooser.setTitle("Exportar " + datos.titulo());
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(formato.nombre, "*." + formato.extension));
            chooser.setInitialFileName(datos.titulo().replaceAll("[^A-Za-z0-9_-]", "_") + "." + formato.extension);
            var elegido = chooser.showSaveDialog(owner); if (elegido == null) return;
            Path destino = elegido.toPath();
            if (!elegido.getName().toLowerCase(Locale.ROOT).endsWith("." + formato.extension)) destino = destino.resolveSibling(elegido.getName() + "." + formato.extension);
            if (Files.exists(destino)) {
                var confirmar = new Alert(Alert.AlertType.CONFIRMATION, "¿Reemplazar " + destino.getFileName() + "?", ButtonType.OK, ButtonType.CANCEL);
                confirmar.initOwner(owner); if (confirmar.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            }
            Path archivo = destino; var tarea = new Task<Void>() {
                @Override protected Void call() throws Exception { DBQPReporteExportador.guardar(archivo, datos, formato); return null; }
            };
            setDisable(true); setText("Exportando...");
            tarea.setOnSucceeded(e -> { terminar(); aviso(Alert.AlertType.INFORMATION, "Reporte guardado:\n" + archivo.toAbsolutePath()); });
            tarea.setOnFailed(e -> { terminar(); aviso(Alert.AlertType.ERROR, "No se pudo guardar el reporte.\n" + tarea.getException().getMessage()); });
            var hilo = new Thread(tarea, "exportar-reporte"); hilo.setDaemon(true); hilo.start();
        } catch (Exception ex) { aviso(Alert.AlertType.ERROR, ex.getMessage()); }
    }
    private void terminar() { setDisable(false); setText("Exportar"); }
    private void aviso(Alert.AlertType tipo, String mensaje) {
        var alerta = new Alert(tipo, mensaje); alerta.initOwner(getScene().getWindow()); alerta.setHeaderText(null); alerta.showAndWait();
    }
}
