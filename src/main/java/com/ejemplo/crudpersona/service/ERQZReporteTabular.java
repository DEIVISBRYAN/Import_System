package com.ejemplo.crudpersona.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record ERQZReporteTabular(String titulo, String alcance, List<String> columnas, List<List<Object>> filas) {
    public ERQZReporteTabular {
        if (titulo == null || titulo.isBlank() || columnas == null || columnas.isEmpty() || columnas.size() > 24)
            throw new IllegalArgumentException("El reporte necesita un título y columnas válidas.");
        alcance = alcance == null ? "" : alcance;
        columnas = List.copyOf(columnas);
        if (filas == null || filas.size() > 100_000) throw new IllegalArgumentException("El reporte supera 100 000 registros. Reduce los filtros.");
        var copia = new ArrayList<List<Object>>();
        for (var fila : filas) {
            if (fila.size() != columnas.size()) throw new IllegalArgumentException("Las columnas no coinciden con los datos.");
            copia.add(Collections.unmodifiableList(new ArrayList<>(fila)));
        }
        filas = List.copyOf(copia);
    }

    public static List<Object> fila(Object... valores) {
        return java.util.Arrays.asList(valores);
    }
}
