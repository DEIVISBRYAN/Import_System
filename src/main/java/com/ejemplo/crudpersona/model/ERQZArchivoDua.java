package com.ejemplo.crudpersona.model;

import com.lowagie.text.pdf.PdfReader;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public record ERQZArchivoDua(String nombre, String tipo, byte[] contenido) {
    public static final int MAX_BYTES = 10 * 1024 * 1024;
    public static final int MAX_ARCHIVOS = 20;
    public static final long MAX_TOTAL_BYTES = 50L * 1024 * 1024;

    public ERQZArchivoDua {
        nombre = nombreSeguro(nombre);
        if (contenido == null || contenido.length == 0 || contenido.length > MAX_BYTES)
            throw new IllegalArgumentException("Cada archivo debe tener contenido y pesar como máximo 10 MB.");
        contenido = contenido.clone();
        String esperado = tipoPorNombre(nombre);
        if (!esperado.equals(tipo)) throw new IllegalArgumentException("El formato no corresponde al nombre del archivo.");
        validarContenido(tipo, contenido);
    }

    @Override public byte[] contenido() { return contenido.clone(); }
    public int tamano() { return contenido.length; }
    public String huella() {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contenido)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static ERQZArchivoDua leer(Path path) throws IOException {
        if (!Files.isRegularFile(path)) throw new IllegalArgumentException("Selecciona un archivo PDF, JPG o PNG.");
        if (Files.size(path) > MAX_BYTES) throw new IllegalArgumentException(path.getFileName() + ": supera los 10 MB.");
        byte[] bytes;
        try (var input = Files.newInputStream(path)) { bytes = input.readNBytes(MAX_BYTES + 1); }
        String nombre = path.getFileName().toString();
        return new ERQZArchivoDua(nombre, tipoPorNombre(nombre), bytes);
    }
    public static String tipoPorNombre(String nombre) {
        String n = nombre.toLowerCase(Locale.ROOT);
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        throw new IllegalArgumentException("Solo se admiten PDF, JPG y PNG.");
    }
    private static String nombreSeguro(String nombre) {
        if (nombre == null) throw new IllegalArgumentException("El archivo necesita un nombre.");
        String n = nombre.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}:*?\"<>|]", "_").strip();
        if (n.isBlank() || n.length() > 180) throw new IllegalArgumentException("El nombre debe tener entre 1 y 180 caracteres.");
        if (n.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])\\..*")) n = "archivo_" + n;
        return n;
    }
    private static void validarContenido(String tipo, byte[] bytes) {
        try {
            if (tipo.equals("application/pdf")) {
                if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F' || bytes[4] != '-')
                    throw new IOException("Cabecera PDF inválida.");
                PdfReader reader = new PdfReader(bytes);
                try {
                    if (reader.isEncrypted() || reader.getNumberOfPages() < 1) throw new IOException("PDF protegido o sin páginas.");
                } finally { reader.close(); }
            } else {
                try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                    var readers = ImageIO.getImageReaders(stream);
                    if (!readers.hasNext()) throw new IOException("Imagen inválida.");
                    var reader = readers.next();
                    try {
                        reader.setInput(stream, true, true);
                        String formato = reader.getFormatName();
                        if (tipo.equals("image/png") ? !formato.equalsIgnoreCase("png") : !formato.equalsIgnoreCase("jpeg"))
                            throw new IOException("Formato de imagen incorrecto.");
                        int width = reader.getWidth(0), height = reader.getHeight(0);
                        if (width <= 0 || height <= 0 || (long) width * height > 40_000_000)
                            throw new IOException("La imagen supera los 40 megapíxeles.");
                        var parametros = reader.getDefaultReadParam();
                        int paso = Math.max(1, (Math.max(width, height) + 511) / 512);
                        parametros.setSourceSubsampling(paso, paso, 0, 0);
                        if (reader.read(0, parametros) == null) throw new IOException("Imagen no legible.");
                    } finally { reader.dispose(); }
                }
            }
        } catch (IOException | RuntimeException e) {
            throw new IllegalArgumentException("El archivo no es un PDF o una imagen válida, está dañado o protegido.", e);
        }
    }
}
