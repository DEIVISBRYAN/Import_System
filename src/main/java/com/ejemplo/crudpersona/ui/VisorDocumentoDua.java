package com.ejemplo.crudpersona.ui;

import com.ejemplo.crudpersona.model.ERQZArchivoDua;
import javafx.stage.Window;
import java.awt.Desktop;
import java.nio.file.Files;

public final class VisorDocumentoDua {
    private VisorDocumentoDua() {}
    public static void mostrar(Window owner, ERQZArchivoDua archivo) {
        if (archivo.tipo().startsWith("image/")) {
            ArchivosDuaPane.verFoto(owner,archivo); return;
        }
        try {
            if (!Desktop.isDesktopSupported() || !Desktop.getDesktop().isSupported(Desktop.Action.OPEN))
                throw new IllegalStateException("No hay un visor PDF disponible. Usa Descargar para guardar el original.");
            var carpeta=Files.createTempDirectory("importadora-dua-"); carpeta.toFile().deleteOnExit();
            var pdf=carpeta.resolve(archivo.nombre()); Files.write(pdf,archivo.contenido()); pdf.toFile().deleteOnExit();
            Desktop.getDesktop().open(pdf.toFile());
        } catch (java.io.IOException ex) { throw new IllegalStateException("No se pudo abrir el PDF. Instala un visor PDF o usa Descargar.",ex); }
    }
}
