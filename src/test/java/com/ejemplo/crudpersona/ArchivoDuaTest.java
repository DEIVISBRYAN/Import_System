package com.ejemplo.crudpersona;

import com.ejemplo.crudpersona.model.ERQZArchivoDua;
import com.lowagie.text.Document;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import org.junit.jupiter.api.Test;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import static org.junit.jupiter.api.Assertions.*;

class ArchivoDuaTest {
    static byte[] pdf(String texto) throws Exception {
        var salida = new ByteArrayOutputStream(); Document doc = new Document(); PdfWriter.getInstance(doc, salida);
        doc.open(); doc.add(new Paragraph(texto)); doc.close(); return salida.toByteArray();
    }
    static byte[] foto(String formato, int color) throws Exception {
        var imagen = new BufferedImage(30, 20, BufferedImage.TYPE_INT_RGB); imagen.setRGB(0, 0, color);
        var salida = new ByteArrayOutputStream(); ImageIO.write(imagen, formato, salida); return salida.toByteArray();
    }
    @Test void aceptaPdfYFotosRealesSinModificarLosBytes(@TempDir Path dir) throws Exception {
        for (String ext : new String[]{"pdf", "png", "jpg"}) {
            byte[] bytes = ext.equals("pdf") ? pdf("DUA de prueba") : foto(ext, 255);
            Path file = dir.resolve("Importación." + ext); Files.write(file, bytes);
            var archivo = ERQZArchivoDua.leer(file);
            assertArrayEquals(bytes, archivo.contenido()); assertEquals(bytes.length, archivo.tamano());
            assertEquals(64, archivo.huella().length());
        }
    }
    @Test void rechazaTiposFalsosVaciosDañadosYExcesivos() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> new ERQZArchivoDua("malware.exe", "application/pdf", pdf("x")));
        assertThrows(IllegalArgumentException.class, () -> new ERQZArchivoDua("foto.png", "image/png", pdf("x")));
        assertThrows(IllegalArgumentException.class, () -> new ERQZArchivoDua("foto.jpg", "image/jpeg", foto("png", 255)));
        assertThrows(IllegalArgumentException.class, () -> new ERQZArchivoDua("falso.pdf", "application/pdf", "%PDF-dañado".getBytes()));
        assertThrows(IllegalArgumentException.class, () -> new ERQZArchivoDua("vacio.pdf", "application/pdf", new byte[0]));
        assertThrows(IllegalArgumentException.class, () -> new ERQZArchivoDua("grande.pdf", "application/pdf", new byte[ERQZArchivoDua.MAX_BYTES + 1]));
    }
    @Test void nombreSeguroYContenidoInmutable() throws Exception {
        byte[] bytes = foto("png", 35); var archivo = new ERQZArchivoDua("../../carpeta/DUA:importación.png", "image/png", bytes);
        assertEquals("DUA_importación.png", archivo.nombre()); String hash = archivo.huella();
        bytes[0] = 0; archivo.contenido()[0] = 0; assertEquals(hash, archivo.huella());
        assertEquals("archivo_CON.pdf", new ERQZArchivoDua("CON.pdf", "application/pdf", pdf("x")).nombre());
    }
}
