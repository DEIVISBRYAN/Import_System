package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.model.PedidoCompraDocumento;
import com.lowagie.text.*;
import com.lowagie.text.pdf.*;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Locale;

public final class ERQZPedidoCompraExportador {
    private ERQZPedidoCompraExportador() {}
    private static final String[] COLUMNAS = {"SKU", "Producto", "Cantidad", "Unit. USD", "Total USD", "Unit. PEN", "Total PEN"};
    public static byte[] pdf(PedidoCompraDocumento p) {
        try {
            var bytes = new ByteArrayOutputStream();
            var doc = new Document(PageSize.A4.rotate(),36,36,36,42);
            var writer = PdfWriter.getInstance(doc,bytes);
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override public void onEndPage(PdfWriter w, Document d) {
                    ColumnText.showTextAligned(w.getDirectContent(),Element.ALIGN_RIGHT,
                            new Phrase(p.numero() + "  |  Pagina " + w.getPageNumber(),fuente(9,false)),d.right(),22,0);
                }
            });
            doc.addTitle("Pedido de compra " + p.numero()); doc.addAuthor(p.empresa()); doc.open();
            doc.add(new Paragraph(p.empresa(),fuente(18,true)));
            doc.add(new Paragraph("RUC: " + p.rucEmpresa() + "   " + p.direccionEmpresa(),fuente(10,false)));
            var titulo = new Paragraph("PEDIDO DE COMPRA  " + p.numero(),fuente(15,true));
            titulo.setSpacingBefore(16); titulo.setSpacingAfter(10); doc.add(titulo);
            doc.add(new Paragraph("Proveedor: " + p.proveedor() + "   RUC: " + p.rucProveedor(),fuente(11,true)));
            if (!p.contacto().isBlank()) doc.add(new Paragraph("Contacto: " + p.contacto(),fuente(10,false)));
            doc.add(new Paragraph("Fecha: " + p.fecha() + "   Estado: " + p.estado(),fuente(10,false)));
            var table = new PdfPTable(new float[]{2,5,1.2f,1.7f,1.7f,1.7f,1.7f}); table.setWidthPercentage(100);
            table.setHeaderRows(1); table.setSplitLate(false); table.setSpacingBefore(16); table.setSpacingAfter(12);
            for (String label : COLUMNAS) { var c = celda(label,true,Element.ALIGN_LEFT); c.setBackgroundColor(new Color(238,244,252)); table.addCell(c); }
            for (var l : p.lineas()) {
                table.addCell(celda(l.sku(),false,Element.ALIGN_LEFT)); table.addCell(celda(l.producto(),false,Element.ALIGN_LEFT));
                table.addCell(celda(""+l.cantidad(),false,Element.ALIGN_RIGHT));
                table.addCell(celda(unitario(l.unitarioUsd()),false,Element.ALIGN_RIGHT)); table.addCell(celda(dinero(l.totalUsd()),true,Element.ALIGN_RIGHT));
                table.addCell(celda(unitario(l.unitarioPen()),false,Element.ALIGN_RIGHT)); table.addCell(celda(dinero(l.totalPen()),true,Element.ALIGN_RIGHT));
            }
            doc.add(table);
            var total = new Paragraph("TOTAL ESTIMADO:  USD " + dinero(p.totalUsd()) + "    |    PEN " + dinero(p.totalPen()),fuente(12,true));
            total.setAlignment(Element.ALIGN_RIGHT); total.setKeepTogether(true); doc.add(total);
            if (!p.observaciones().isBlank()) { var nota = new Paragraph("Observaciones: " + p.observaciones(),fuente(10,false)); nota.setSpacingBefore(16); doc.add(nota); }
            var aviso = new Paragraph("Costos estimados en monedas equivalentes. No sumar USD y PEN. Este pedido no acredita recepción de mercadería ni es un comprobante de pago.",fuente(9,false));
            aviso.setSpacingBefore(16); doc.add(aviso); doc.close(); return bytes.toByteArray();
        } catch (Exception e) { throw new IllegalStateException("No se pudo generar el PDF del pedido.",e); }
    }
    private static com.lowagie.text.Font fuente(int size, boolean bold) {
        return FontFactory.getFont(FontFactory.HELVETICA,size,bold ? com.lowagie.text.Font.BOLD : com.lowagie.text.Font.NORMAL,new Color(21,36,54));
    }
    private static PdfPCell celda(String value, boolean bold, int align) {
        var cell = new PdfPCell(new Phrase(value == null ? "" : value,fuente(10,bold)));
        cell.setPadding(7); cell.setHorizontalAlignment(align); cell.setBorder(com.lowagie.text.Rectangle.BOTTOM); cell.setBorderColor(new Color(225,233,242)); return cell;
    }
    private static String dinero(BigDecimal n) { return String.format(Locale.ROOT,"%.2f",n); }
    private static String unitario(BigDecimal n) { return n.setScale(6,java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(); }

    public static byte[] excel(PedidoCompraDocumento p) {
        try (var book = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var sheet = book.createSheet("Pedido"); sheet.setDisplayGridlines(false);
            var title = estilo(book,true,18); var normal = estilo(book,false,11); var bold = estilo(book,true,11);
            var heading = estilo(book,true,11); heading.setFillForegroundColor(IndexedColors.PALE_BLUE.getIndex()); heading.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            var unit = estilo(book,false,11); unit.setDataFormat(book.createDataFormat().getFormat("#,##0.00####")); unit.setAlignment(HorizontalAlignment.RIGHT);
            var money = estilo(book,false,11); money.setDataFormat(book.createDataFormat().getFormat("#,##0.00")); money.setAlignment(HorizontalAlignment.RIGHT);
            var totalStyle = estilo(book,true,12); totalStyle.setDataFormat(money.getDataFormat()); totalStyle.setAlignment(HorizontalAlignment.RIGHT);
            linea(sheet,0,p.empresa(),title); linea(sheet,1,"RUC: " + p.rucEmpresa() + "   " + p.direccionEmpresa(),normal);
            linea(sheet,3,"PEDIDO DE COMPRA  " + p.numero(),bold);
            linea(sheet,4,"Proveedor: " + p.proveedor() + "   RUC: " + p.rucProveedor(),normal);
            linea(sheet,5,"Contacto: " + p.contacto(),normal); linea(sheet,6,"Fecha: " + p.fecha() + "   Estado: " + p.estado(),normal);
            var header = sheet.createRow(8); header.setHeightInPoints(28);
            for (int c=0;c<COLUMNAS.length;c++) { var cell=header.createCell(c); cell.setCellValue(COLUMNAS[c]); cell.setCellStyle(heading); }
            int rowIndex=9;
            for (var l : p.lineas()) {
                var row=sheet.createRow(rowIndex); row.setHeightInPoints(Math.max(32, 16 * (1+(l.producto().length()/45))));
                texto(row,0,l.sku(),normal); texto(row,1,l.producto(),normal);
                numero(row,2,BigDecimal.valueOf(l.cantidad()),normal); numero(row,3,l.unitarioUsd(),unit); numero(row,5,l.unitarioPen(),unit);
                var usd=row.createCell(4); usd.setCellFormula("ROUND(C"+(rowIndex+1)+"*D"+(rowIndex+1)+",2)"); usd.setCellStyle(money);
                var pen=row.createCell(6); pen.setCellFormula("ROUND(C"+(rowIndex+1)+"*F"+(rowIndex+1)+",2)"); pen.setCellStyle(money); rowIndex++;
            }
            var total=sheet.createRow(rowIndex); total.setHeightInPoints(30); texto(total,1,"TOTAL ESTIMADO",bold);
            for (int c : new int[]{4,6}) { var cell=total.createCell(c); cell.setCellFormula(p.lineas().isEmpty()?"0":"SUM("+(c==4?"E":"G")+"10:"+(c==4?"E":"G")+rowIndex+")"); cell.setCellStyle(totalStyle); }
            if (!p.observaciones().isBlank()) linea(sheet,rowIndex+2,"Observaciones: " + p.observaciones(),normal);
            linea(sheet,rowIndex+4,"Costos estimados en monedas equivalentes. No sumar USD y PEN. No acredita recepción ni es comprobante de pago.",normal);
            int[] widths={20,48,12,16,17,16,17}; for (int c=0;c<widths.length;c++) sheet.setColumnWidth(c,widths[c]*256);
            sheet.createFreezePane(2,9); sheet.setAutoFilter(new CellRangeAddress(8,Math.max(8,rowIndex-1),0,6));
            sheet.setRepeatingRows(new CellRangeAddress(8,8,-1,-1)); sheet.getPrintSetup().setLandscape(true);
            sheet.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE); sheet.getPrintSetup().setFitWidth((short)1); sheet.getPrintSetup().setFitHeight((short)0); sheet.setFitToPage(true);
            book.setPrintArea(0,0,6,0,rowIndex+4); sheet.getFooter().setRight(p.numero()+" | Página &P de &N");
            book.getCreationHelper().createFormulaEvaluator().evaluateAll(); book.write(bytes); return bytes.toByteArray();
        } catch(Exception e) { throw new IllegalStateException("No se pudo generar el Excel del pedido.",e); }
    }
    private static CellStyle estilo(XSSFWorkbook book, boolean bold, int size) {
        var style=book.createCellStyle(); var font=book.createFont(); font.setFontName("Calibri"); font.setFontHeightInPoints((short)size); font.setBold(bold);
        font.setColor(IndexedColors.DARK_BLUE.getIndex()); style.setFont(font); style.setWrapText(true); style.setVerticalAlignment(VerticalAlignment.CENTER); return style;
    }
    private static void linea(org.apache.poi.ss.usermodel.Sheet sheet,int index,String text,CellStyle style) {
        var row=sheet.createRow(index); row.setHeightInPoints(Math.max(28,16*(1+text.length()/120))); texto(row,0,text,style); sheet.addMergedRegion(new CellRangeAddress(index,index,0,6));
    }
    private static void texto(org.apache.poi.ss.usermodel.Row row,int col,String value,CellStyle style) { var cell=row.createCell(col); cell.setCellValue(value); cell.setCellStyle(style); }
    private static void numero(org.apache.poi.ss.usermodel.Row row,int col,BigDecimal value,CellStyle style) { var cell=row.createCell(col); cell.setCellValue(value.doubleValue()); cell.setCellStyle(style); }
}
