package com.ejemplo.crudpersona;

import com.ejemplo.crudpersona.model.ERQZArchivoDua;
import com.ejemplo.crudpersona.service.ERQZDuaPdfService;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.junit.jupiter.api.Test;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DuaPdfServiceTest {
    final ERQZDuaPdfService lector=new ERQZDuaPdfService();
    static ERQZArchivoDua tabla(List<String> encabezados,List<List<String>> filas,boolean bordes)throws Exception {
        var salida=new ByteArrayOutputStream();var doc=new Document(PageSize.A4.rotate());PdfWriter.getInstance(doc,salida);doc.open();
        doc.add(new Paragraph("DUA: DEMO-2026-001\nRUC proveedor: 99999999999\nFecha de ingreso: 16/10/2026\nTipo de cambio: 3.75"));
        var tabla=new PdfPTable(encabezados.size());tabla.setWidthPercentage(100);tabla.setSpacingBefore(12);
        for(String t:encabezados){var c=new PdfPCell(new Phrase(t));if(!bordes)c.setBorder(0);tabla.addCell(c);}
        for(var f:filas)for(String t:f){var c=new PdfPCell(new Phrase(t));if(!bordes)c.setBorder(0);tabla.addCell(c);}
        doc.add(tabla);doc.close();return new ERQZArchivoDua("DUA.pdf","application/pdf",salida.toByteArray());
    }
    @Test void leeTablaCompletaSinGuardarNiInventarDatos()throws Exception {
        var archivo=tabla(List.of("SKU","Producto","Cant.","FOB USD","Gastos USD","Lote","Ubicación","Margen %"),
                List.of(List.of("AUD-1","Audifonos", "20","4.20","0.80","L-1","A-01-1","35"),List.of("CAB-1","Cable HDMI","30","1.80","0.30","L-2","B-01-1","35")),true);
        var r=lector.leer(archivo);assertEquals(2,r.productos().size());assertEquals(20,r.productos().get(0).cantidad());
        assertEquals(new BigDecimal("4.20"),r.productos().get(0).fob());assertEquals("DEMO-2026-001",r.dua());
        assertEquals("99999999999",r.ruc());assertEquals(java.time.LocalDate.of(2026,10,16),r.fecha());assertEquals(new BigDecimal("3.75"),r.cambio());
        assertEquals("B-01-1",r.productos().get(1).ubicacion());assertEquals(archivo.huella(),r.huella());
    }
    @Test void camposAusentesQuedanPendientesYNoSeUsanTotalesComoProductos()throws Exception {
        var r=lector.leer(tabla(List.of("Producto","Cantidad"),List.of(List.of("Producto sin costos","4"),List.of("TOTAL","4")),true));
        assertEquals(1,r.productos().size());var p=r.productos().get(0);assertNull(p.fob());assertNull(p.gastos());assertTrue(p.sku().isEmpty());
        assertTrue(p.lote().isEmpty());assertTrue(p.ubicacion().isEmpty());assertFalse(r.avisos().isEmpty());
    }
    @Test void reconoceColumnasEnInglesYSinBordes()throws Exception {
        var r=lector.leer(tabla(List.of("SKU","Description","Quantity","FOB USD","Gastos USD"),List.of(List.of("ABC","USB cable","12","2.50","0.50")),false));
        assertEquals(1,r.productos().size());assertEquals("ABC",r.productos().get(0).sku());assertEquals(12,r.productos().get(0).cantidad());
    }
    @Test void cantidadesAmbiguasONoEnterasSeMarcanParaRevision()throws Exception {
        var r=lector.leer(tabla(List.of("Producto","Cantidad"),List.of(List.of("Uno","1,234"),List.of("Dos","2.5"),List.of("Tres","-4")),true));
        assertEquals(3,r.productos().size());r.productos().forEach(p->assertNull(p.cantidad()));
    }
    @Test void convierteFobTotalSinConfundirloConUnitario()throws Exception {
        var r=lector.leer(tabla(List.of("Producto","Cantidad","FOB total USD"),List.of(List.of("Producto","4","20.00")),true));
        assertEquals(0,new BigDecimal("5").compareTo(r.productos().get(0).fob()));assertFalse(r.avisos().isEmpty());
    }
    @Test void valoresNumericosLocalesSeValidanSinAdivinar() {
        assertEquals(new BigDecimal("1234.50"),ERQZDuaPdfService.numero("1.234,50"));assertEquals(new BigDecimal("1234.50"),ERQZDuaPdfService.numero("1,234.50"));
        assertEquals(new BigDecimal("0.900"),ERQZDuaPdfService.numero("0,900"));assertNull(ERQZDuaPdfService.numero("1,234"));assertNull(ERQZDuaPdfService.numero("12.34.56"));
        assertNull(ERQZDuaPdfService.numero("sin costo"));assertNull(ERQZDuaPdfService.numero("-3"));
    }
    @Test void pdfEscaneadoSinTextoNoProduceProductos()throws Exception {
        var salida=new ByteArrayOutputStream();var doc=new Document();PdfWriter.getInstance(doc,salida);doc.open();
        var png=ArchivoDuaTest.foto("png",255);doc.add(Image.getInstance(png));doc.close();
        var r=lector.leer(new ERQZArchivoDua("escaneo.pdf","application/pdf",salida.toByteArray()));assertTrue(r.productos().isEmpty());assertTrue(r.avisos().stream().anyMatch(s->s.contains("OCR")));
    }
}
