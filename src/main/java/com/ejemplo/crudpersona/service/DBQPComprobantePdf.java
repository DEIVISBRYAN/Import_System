package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.model.ComprobanteDocumento;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class DBQPComprobantePdf {
    private DBQPComprobantePdf() {}
    public static byte[] generar(ComprobanteDocumento c) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            Document doc = new Document(PageSize.A4, 42, 42, 40, 48);
            PdfWriter writer = PdfWriter.getInstance(doc, bytes);
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override public void onEndPage(PdfWriter w, Document d) {
                    ColumnText.showTextAligned(w.getDirectContent(), Element.ALIGN_RIGHT,
                            new Phrase("Pagina " + w.getPageNumber(), font(9, false)), d.right(), 26, 0);
                }
            });
            doc.addTitle(c.tipo() + " " + c.numero()); doc.addAuthor(c.empresa()); doc.open();
            doc.add(new Paragraph(c.empresa(), font(18, true)));
            doc.add(new Paragraph("RUC: " + (c.rucEmpresa().isBlank() ? "POR CONFIGURAR" : c.rucEmpresa()), font(10, false)));
            if (!c.direccionEmpresa().isBlank()) doc.add(new Paragraph(c.direccionEmpresa(), font(10, false)));
            Paragraph title = new Paragraph(c.tipo() + "  " + c.numero(), font(15, true));
            title.setSpacingBefore(18); title.setSpacingAfter(12); doc.add(title);
            doc.add(new Paragraph("Fecha: " + c.fecha().format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")), font(10, false)));
            doc.add(new Paragraph("Cliente: " + c.cliente() + "    DNI / RUC: " + c.documentoCliente(), font(10, false)));
            doc.add(new Paragraph("Pago: " + c.metodoPago().replace('_', ' '), font(10, false)));
            PdfPTable table = new PdfPTable(new float[]{1, 6, 2, 2}); table.setWidthPercentage(100);
            table.setSpacingBefore(18); table.setSpacingAfter(12); table.setHeaderRows(1);
            for (String label : new String[]{"Cant.", "Descripcion", "P. unitario", "Importe"}) {
                PdfPCell cell = cell(label, true, Element.ALIGN_LEFT); cell.setBackgroundColor(new Color(238, 243, 251)); table.addCell(cell);
            }
            table.setSplitLate(false);
            for (var line : c.lineas()) {
                table.addCell(cell(String.valueOf(line.cantidad()), false, Element.ALIGN_CENTER));
                table.addCell(cell(line.descripcion(), false, Element.ALIGN_LEFT));
                table.addCell(cell(moneda(line.precio()), false, Element.ALIGN_RIGHT));
                table.addCell(cell(moneda(line.importe()), false, Element.ALIGN_RIGHT));
            }
            doc.add(table);
            PdfPTable totals = new PdfPTable(new float[]{3, 2}); totals.setWidthPercentage(45); totals.setHorizontalAlignment(Element.ALIGN_RIGHT); totals.setKeepTogether(true);
            for (Object[] row : new Object[][]{{"Operacion gravada", c.subtotal()}, {"IGV 18%", c.igv()}, {"TOTAL", c.total()}}) {
                totals.addCell(cell(row[0].toString(), true, Element.ALIGN_LEFT));
                totals.addCell(cell(moneda((BigDecimal) row[1]), true, Element.ALIGN_RIGHT));
            }
            doc.add(totals);
            Paragraph notice = new Paragraph("Estado: " + c.estado() + ". " +
                    ("ACEPTADO".equals(c.estado()) ? "" : "Documento interno. No enviado ni validado por SUNAT."), font(9, false));
            notice.setSpacingBefore(18); doc.add(notice); doc.close(); return bytes.toByteArray();
        } catch (Exception e) { throw new IllegalStateException("No se pudo generar el PDF.", e); }
    }
    private static Font font(int size, boolean bold) { return FontFactory.getFont(FontFactory.HELVETICA, size, bold ? Font.BOLD : Font.NORMAL, new Color(20, 32, 52)); }
    private static PdfPCell cell(String text, boolean bold, int align) {
        PdfPCell c = new PdfPCell(new Phrase(text, font(10, bold))); c.setPadding(7); c.setHorizontalAlignment(align);
        c.setBorder(Rectangle.BOTTOM); c.setBorderColor(new Color(222, 230, 241)); return c;
    }
    private static String moneda(BigDecimal v) { return String.format(Locale.ROOT, "S/ %.2f", v); }
}
