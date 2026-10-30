package com.ejemplo.crudpersona;

import com.ejemplo.crudpersona.model.Venta;
import com.ejemplo.crudpersona.service.*;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ReporteExportadorTest {
    @TempDir Path temporal;
    private ERQZReporteTabular muestra() {
        return new ERQZReporteTabular("Ventas de prueba", "Solo verificación", List.of("Código", "Producto", "Cantidad", "Total PEN", "Fecha"),
                List.of(ERQZReporteTabular.fila("0012300456", "Audífonos, edición \"azul\"\nLote 1", 2, new BigDecimal("44.20"), LocalDateTime.of(2026, 10, 21, 10, 15))));
    }
    @Test void excelMantieneTiposCodigosFechasYFiltros() throws Exception {
        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(DBQPReporteExportador.excel(muestra())))) {
            var hoja = libro.getSheetAt(0); var fila = hoja.getRow(5);
            assertEquals("0012300456", fila.getCell(0).getStringCellValue());
            assertEquals(CellType.NUMERIC, fila.getCell(2).getCellType()); assertEquals(2, fila.getCell(2).getNumericCellValue());
            assertEquals(44.20, fila.getCell(3).getNumericCellValue(), .00001);
            assertTrue(DateUtil.isCellDateFormatted(fila.getCell(4)));
            assertNotNull(hoja.getPaneInformation()); assertNotNull(hoja.getCTWorksheet().getAutoFilter());
        }
    }
    @Test void csvEscapaComasComillasSaltosYFormulas() {
        var reporte = new ERQZReporteTabular("CSV", "", List.of("Texto", "Importe"), List.of(
                ERQZReporteTabular.fila(" =HYPERLINK(\"x\")", -2), ERQZReporteTabular.fila("a,b\n\"c\"", null)));
        String csv = new String(DBQPReporteExportador.csv(reporte), StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("\uFEFF\"Texto\",\"Importe\"\r\n"));
        assertTrue(csv.contains("\"' =HYPERLINK(\"\"x\"\")\",\"-2\""));
        assertTrue(csv.contains("\"a,b\n\"\"c\"\"\",\"\"\r\n"));
    }
    @Test void excelNoEjecutaFormulasRecibidasComoTexto() throws Exception {
        var r = new ERQZReporteTabular("Prueba", "", List.of("Valor"), List.of(ERQZReporteTabular.fila("=1+2"), ERQZReporteTabular.fila(12345678901234567L)));
        try (var libro = new XSSFWorkbook(new ByteArrayInputStream(DBQPReporteExportador.excel(r)))) {
            assertEquals(CellType.STRING, libro.getSheetAt(0).getRow(5).getCell(0).getCellType());
            assertEquals("12345678901234567", libro.getSheetAt(0).getRow(6).getCell(0).getStringCellValue());
        }
    }
    @Test void pdfMultipaginaConCabecerasYUltimoRegistro() throws Exception {
        var filas = new ArrayList<List<Object>>();
        for (int i = 1; i <= 180; i++) filas.add(ERQZReporteTabular.fila("SKU-" + i, "Producto de importación " + i, i, new BigDecimal("12.50")));
        byte[] bytes = DBQPReporteExportador.pdf(new ERQZReporteTabular("Reporte completo", "Prueba", List.of("SKU", "Producto", "Cantidad", "Total PEN"), filas));
        try (var pdf = PDDocument.load(bytes)) {
            assertTrue(pdf.getNumberOfPages() > 1); String texto = new PDFTextStripper().getText(pdf);
            assertTrue(texto.contains("SKU-180")); assertTrue(texto.contains("Página 2"));
            assertTrue(texto.indexOf("Cantidad") != texto.lastIndexOf("Cantidad"));
        }
    }
    @Test void guardarGeneraArchivoYNoDejaTemporales() throws Exception {
        Path archivo = temporal.resolve("ventas.xlsx");
        DBQPReporteExportador.guardar(archivo, muestra(), DBQPReporteExportador.Formato.EXCEL);
        assertTrue(Files.size(archivo) > 1000);
        try (var files = Files.list(temporal)) { assertEquals(1, files.count()); }
    }
    @Test void ventasIncluyeAnuladasSinSumarlasAlTotal() {
        Venta valida = Venta.builder().serie("B001").correlativo(1).clienteDocumento("00123456").total(new BigDecimal("100.00")).estado("COMPLETADA").build();
        Venta anulada = Venta.builder().serie("B001").correlativo(2).total(new BigDecimal("200.00")).estado("ANULADA").build();
        var reporte = DBQPReporteVentas.crear(List.of(valida, anulada), "Prueba");
        assertEquals(2, reporte.filas().size()); assertTrue(reporte.alcance().contains("Total no anulado PEN: 100.00"));
        assertEquals("00123456", reporte.filas().get(0).get(4));
    }
    @Test void rechazaFilasDesalineadasYConservaInstantanea() {
        assertThrows(IllegalArgumentException.class, () -> new ERQZReporteTabular("Reporte", "", List.of("Uno"), List.of(ERQZReporteTabular.fila(1, 2))));
        var fila = ERQZReporteTabular.fila("Original"); var r = new ERQZReporteTabular("Reporte", "", List.of("Uno"), List.of(fila));
        fila.set(0, "Otro"); assertEquals("Original", r.filas().get(0).get(0));
    }
}
