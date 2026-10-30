package com.ejemplo.crudpersona.ui;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.FileChooser;
import javafx.stage.Window;
import java.nio.file.*;
import java.util.Locale;
import java.util.function.Supplier;

public final class ArchivoDescarga {
    private ArchivoDescarga() {}
    public static boolean guardar(Window owner, String nombre, String extension, Supplier<byte[]> generar) throws Exception {
        if (!extension.equals("pdf") && !extension.equals("xlsx")) throw new IllegalArgumentException("Formato no admitido.");
        var chooser=new FileChooser(); chooser.setTitle("Descargar pedido " + extension.toUpperCase(Locale.ROOT));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(extension.equals("pdf")?"Pedido PDF":"Pedido Excel","*."+extension));
        chooser.setInitialFileName(nombre.replaceAll("[^A-Za-z0-9._-]","_")+"."+extension);
        var file=chooser.showSaveDialog(owner); if(file==null) return false;
        Path path=file.toPath();
        if(!file.getName().toLowerCase(Locale.ROOT).endsWith("."+extension)) {
            path=path.resolveSibling(file.getName()+"."+extension);
            if(Files.exists(path)) {
                var alert=new Alert(Alert.AlertType.CONFIRMATION,"El archivo " + path.getFileName()+" ya existe. ¿Reemplazarlo?"); alert.initOwner(owner);
                if(alert.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK) return false;
            }
        }
        byte[] bytes=generar.get();
        if(extension.equals("pdf")) PdfDescarga.guardarEn(path,bytes);
        else {
            if(bytes==null || bytes.length<4 || bytes[0]!='P' || bytes[1]!='K') throw new java.io.IOException("No se generó un Excel válido.");
            guardarEn(path,bytes);
        }
        return true;
    }
    public static void guardarEn(Path destino,byte[] bytes) throws java.io.IOException {
        Path path=destino.toAbsolutePath(); Path temp=Files.createTempFile(path.getParent(),".pedido-",".tmp");
        try {
            Files.write(temp,bytes);
            try { Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch(AtomicMoveNotSupportedException e) { Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temp); }
    }
}
