package com.ejemplo.crudpersona.service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class DBQPReporteExportador {
    public enum Formato {
        EXCEL("Excel", "xlsx"), CSV("CSV", "csv"), PDF("PDF", "pdf");
        public final String nombre, extension;
        Formato(String nombre, String extension) { this.nombre = nombre; this.extension = extension; }
    }
    private DBQPReporteExportador() {}
    private static final ZoneId ZONA = ZoneId.of("America/Lima");
    private static String generado() { return ZonedDateTime.now(ZONA).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")); }

    public static byte[] generar(ERQZReporteTabular reporte, Formato formato) {
        return switch (formato) {
            case EXCEL -> excel(reporte);
            case CSV -> csv(reporte);
            case PDF -> pdf(reporte);
        };
    }

    public static void guardar(Path destino, ERQZReporteTabular reporte, Formato formato) throws java.io.IOException {
        Path absoluto = destino.toAbsolutePath();
        byte[] datos = generar(reporte, formato);
        Path temporal = Files.createTempFile(absoluto.getParent(), ".reporte-", ".tmp");
        try {
            Files.write(temporal, datos);
            try { Files.move(temporal, absoluto, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException ex) { Files.move(temporal, absoluto, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporal); }
    }

    public static byte[] csv(ERQZReporteTabular r) {
        StringBuilder csv = new StringBuilder("\uFEFF");
        lineaCsv(csv, r.columnas());
        for (var fila : r.filas()) lineaCsv(csv, fila);
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }
    private static void lineaCsv(StringBuilder salida, java.util.List<?> fila) {
        for (int i = 0; i < fila.size(); i++) {
            if (i > 0) salida.append(',');
            Object valor = fila.get(i);
            String texto = texto(valor);
            // Spreadsheet applications may execute formulas even inside quoted CSV fields.
            if (!(valor instanceof Number) && texto.stripLeading().matches("(?s)^[=+@-].*")) texto = "'" + texto;
            salida.append('"').append(texto.replace("\"", "\"\"")).append('"');
        }
        salida.append("\r\n");
    }

    public static byte[] excel(ERQZReporteTabular r) {
        try (var libro = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var hoja = libro.createSheet("Reporte");
            hoja.setDisplayGridlines(false);
            var titulo = estilo(libro, true, 18);
            var normal = estilo(libro, false, 11);
            var cabecera = estilo(libro, true, 11);
            cabecera.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex());
            cabecera.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var decimal = estilo(libro, false, 11);
            decimal.setDataFormat(libro.createDataFormat().getFormat("#,##0.00####"));
            var entero = estilo(libro, false, 11);
            entero.setDataFormat(libro.createDataFormat().getFormat("#,##0"));
            var fecha = estilo(libro, false, 11);
            fecha.setDataFormat(libro.createDataFormat().getFormat("dd/mm/yyyy"));
            var fechaHora = estilo(libro, false, 11);
            fechaHora.setDataFormat(libro.createDataFormat().getFormat("dd/mm/yyyy hh:mm"));
            int fin = r.columnas().size() - 1;
            textoHoja(hoja, 0, r.titulo(), titulo, fin);
            textoHoja(hoja, 1, r.alcance(), normal, fin);
            textoHoja(hoja, 2, "Generado: " + generado() + " (Lima) | Registros: " + r.filas().size(), normal, fin);
            var encabezado = hoja.createRow(4); encabezado.setHeightInPoints(30);
            for (int c = 0; c <= fin; c++) {
                var celda = encabezado.createCell(c); celda.setCellValue(r.columnas().get(c)); celda.setCellStyle(cabecera);
            }
            int indice = 5;
            for (var fila : r.filas()) {
                var renglon = hoja.createRow(indice++); renglon.setHeightInPoints(34);
                for (int c = 0; c <= fin; c++) {
                    Object valor = fila.get(c); var celda = renglon.createCell(c); celda.setCellStyle(normal);
                    if (valor instanceof Number n && precisionExcel(n)) {
                        celda.setCellValue(n.doubleValue()); celda.setCellStyle(n instanceof Float || n instanceof Double || n instanceof BigDecimal ? decimal : entero);
                    } else if (valor instanceof LocalDate d) { celda.setCellValue(d); celda.setCellStyle(fecha); }
                    else if (valor instanceof LocalDateTime d) { celda.setCellValue(d); celda.setCellStyle(fechaHora); }
                    else celda.setCellValue(texto(valor));
                }
            }
            for (int c = 0; c <= fin; c++) {
                int longitud = r.columnas().get(c).length();
                for (var fila : r.filas()) longitud = Math.max(longitud, texto(fila.get(c)).length());
                hoja.setColumnWidth(c, Math.max(14, Math.min(55, longitud + 3)) * 256);
            }
            hoja.createFreezePane(0, 5);
            hoja.setAutoFilter(new CellRangeAddress(4, Math.max(4, indice - 1), 0, fin));
            hoja.setRepeatingRows(new CellRangeAddress(4, 4, -1, -1));
            hoja.setFitToPage(true); hoja.getPrintSetup().setLandscape(fin > 4);
            hoja.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
            hoja.getPrintSetup().setFitWidth((short) 1); hoja.getPrintSetup().setFitHeight((short) 0);
            libro.setPrintArea(0, 0, fin, 0, Math.max(4, indice - 1));
            hoja.getFooter().setRight("Página &P de &N");
            libro.write(bytes); return bytes.toByteArray();
        } catch (Exception ex) { throw new IllegalStateException("No se pudo generar el reporte Excel.", ex); }
    }
    private static boolean precisionExcel(Number n) {
        return new BigDecimal(n.toString()).stripTrailingZeros().precision() <= 15;
    }
    private static CellStyle estilo(XSSFWorkbook libro, boolean negrita, int tamano) {
        var estilo = libro.createCellStyle(); var fuente = libro.createFont();
        fuente.setFontName("Calibri"); fuente.setFontHeightInPoints((short) tamano); fuente.setBold(negrita);
        fuente.setColor(IndexedColors.DARK_BLUE.getIndex()); estilo.setFont(fuente);
        estilo.setWrapText(true); estilo.setVerticalAlignment(VerticalAlignment.CENTER); return estilo;
    }
    private static void textoHoja(Sheet hoja, int fila, String valor, CellStyle estilo, int fin) {
        var row = hoja.createRow(fila); row.setHeightInPoints(fila == 0 ? 34 : 30);
        var cell = row.createCell(0); cell.setCellValue(valor); cell.setCellStyle(estilo);
        if (fin > 0) hoja.addMergedRegion(new CellRangeAddress(fila, fila, 0, fin));
    }

    public static byte[] pdf(ERQZReporteTabular r) {
        try {
            var bytes = new ByteArrayOutputStream();
            var pagina = r.columnas().size() > 5 ? PageSize.A4.rotate() : PageSize.A4;
            var documento = new Document(pagina, 28, 28, 30, 40);
            var writer = PdfWriter.getInstance(documento, bytes);
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override public void onEndPage(PdfWriter w, Document d) {
                    ColumnText.showTextAligned(w.getDirectContent(), Element.ALIGN_RIGHT,
                            new Phrase(com.ejemplo.crudpersona.ui.Marca.NOMBRE + " | Página " + w.getPageNumber(), fuente(8, false)), d.right(), 20, 0);
                }
            });
            documento.addTitle(r.titulo()); documento.addAuthor(com.ejemplo.crudpersona.ui.Marca.NOMBRE); documento.open();
            var titulo = new Paragraph(r.titulo(), fuente(18, true)); titulo.setSpacingAfter(8); documento.add(titulo);
            documento.add(new Paragraph(r.alcance(), fuente(9, false)));
            documento.add(new Paragraph("Generado: " + generado() + " (Lima) | Registros: " + r.filas().size(), fuente(9, false)));
            float[] anchos = new float[r.columnas().size()];
            var fuenteMedida = BaseFont.createFont(BaseFont.HELVETICA, BaseFont.WINANSI, BaseFont.NOT_EMBEDDED);
            for (int c = 0; c < anchos.length; c++) {
                float ancho = fuenteMedida.getWidthPoint(r.columnas().get(c), 9) + 16;
                for (var fila : r.filas()) ancho = Math.max(ancho, fuenteMedida.getWidthPoint(texto(fila.get(c)), 9) + 16);
                anchos[c] = Math.max(48, Math.min(180, ancho));
            }
            var tabla = new PdfPTable(anchos); tabla.setWidthPercentage(100); tabla.setHeaderRows(1);
            tabla.setSplitRows(true); tabla.setSplitLate(false); tabla.setSpacingBefore(16);
            for (String columna : r.columnas()) tabla.addCell(celda(columna, true, false));
            for (var fila : r.filas()) for (int c = 0; c < fila.size(); c++) {
                Object valor = fila.get(c);
                String texto = valor instanceof BigDecimal n && r.columnas().get(c).endsWith("PEN")
                        ? n.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() : texto(valor);
                tabla.addCell(celda(texto, false, valor instanceof Number));
            }
            if (r.filas().isEmpty()) { var celda = celda("Sin registros", false, false); celda.setColspan(anchos.length); tabla.addCell(celda); }
            documento.add(tabla); documento.close(); return bytes.toByteArray();
        } catch (Exception ex) { throw new IllegalStateException("No se pudo generar el reporte PDF.", ex); }
    }
    private static com.lowagie.text.Font fuente(int tamano, boolean negrita) {
        return FontFactory.getFont(FontFactory.HELVETICA, tamano, negrita ? com.lowagie.text.Font.BOLD : com.lowagie.text.Font.NORMAL, new Color(20, 36, 57));
    }
    private static PdfPCell celda(String texto, boolean cabecera, boolean numero) {
        var celda = new PdfPCell(new Phrase(texto, fuente(9, cabecera))); celda.setPadding(6);
        celda.setBorder(com.lowagie.text.Rectangle.BOTTOM); celda.setBorderColor(new Color(224, 232, 242));
        if (cabecera) celda.setBackgroundColor(new Color(237, 244, 255));
        if (numero) celda.setHorizontalAlignment(Element.ALIGN_RIGHT); return celda;
    }
    private static String texto(Object valor) {
        if (valor == null) return "";
        if (valor instanceof BigDecimal d) return d.toPlainString();
        if (valor instanceof LocalDate d) return d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        if (valor instanceof LocalDateTime d) return d.format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"));
        return valor.toString();
    }
}
