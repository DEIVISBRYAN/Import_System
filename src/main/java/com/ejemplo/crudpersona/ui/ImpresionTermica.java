package com.ejemplo.crudpersona.ui;

import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.PrinterJob;
import java.util.Comparator;

public final class ImpresionTermica {
    public static final double ANCHO_80_MM = 80 * 72 / 25.4;
    private ImpresionTermica() {}
    public static boolean esPapelTermico(Paper papel) {
        double mm = papel.getWidth() * 25.4 / 72;
        return mm >= 70 && mm <= 90;
    }
    public static void preparar(PrinterJob trabajo) {
        var impresora = trabajo.getPrinter();
        var papel = impresora.getPrinterAttributes().getSupportedPapers().stream()
                .filter(ImpresionTermica::esPapelTermico)
                .min(Comparator.comparingDouble(p -> Math.abs(p.getWidth() - ANCHO_80_MM)));
        papel.ifPresent(p -> trabajo.getJobSettings().setPageLayout(impresora.createPageLayout(p,
                PageOrientation.PORTRAIT, 6, 6, 6, 6)));
    }
    public static void validar(PrinterJob trabajo) {
        var layout = trabajo.getJobSettings().getPageLayout();
        if (!esPapelTermico(layout.getPaper()) || layout.getPageOrientation() != PageOrientation.PORTRAIT)
            throw new IllegalArgumentException("Selecciona la impresora térmica y papel de 80 mm en orientación vertical. "
                    + "Si no aparece, configura ese tamaño en el controlador de la impresora en Windows.");
        if (layout.getPrintableWidth() < 150)
            throw new IllegalArgumentException("Reduce los márgenes del papel térmico de 80 mm.");
    }
}
