package com.ejemplo.crudpersona.ui;

import com.ejemplo.crudpersona.service.RPLCComprobantesServiceI;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

public final class PdfDescarga {
    private PdfDescarga() {}
    public static boolean guardar(Window owner, Long id, String numero, RPLCComprobantesServiceI service) throws Exception {
        FileChooser chooser = new FileChooser(); chooser.setTitle("Descargar comprobante PDF");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Comprobante PDF", "*.pdf"));
        chooser.setInitialFileName(numero.replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf");
        var file = chooser.showSaveDialog(owner);
        if (file == null) return false;
        var path = file.toPath();
        if (!file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".pdf")) {
            path = path.resolveSibling(file.getName() + ".pdf");
            if (Files.exists(path)) {
                Alert confirmar = new Alert(Alert.AlertType.CONFIRMATION, "El archivo " + path.getFileName() + " ya existe. ¿Reemplazarlo?");
                if (owner != null) confirmar.initOwner(owner);
                if (confirmar.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return false;
            }
        }
        guardarEn(path, service.generarPdf(id));
        return true;
    }
    public static void guardarEn(Path destino, byte[] pdf) throws java.io.IOException {
        if (pdf == null || pdf.length < 4 || pdf[0] != '%' || pdf[1] != 'P' || pdf[2] != 'D' || pdf[3] != 'F')
            throw new java.io.IOException("No se generó un PDF válido.");
        Path archivo = destino.toAbsolutePath();
        Path temporal = Files.createTempFile(archivo.getParent(), ".comprobante-", ".tmp");
        try {
            Files.write(temporal, pdf);
            try { Files.move(temporal, archivo, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temporal, archivo, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporal); }
    }
}
