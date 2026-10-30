package com.ejemplo.crudpersona;

import com.ejemplo.crudpersona.ui.Marca;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PresentacionGenericaTest {
    @Test void escritorioSinMarcaComercialNiRubroFijo() throws Exception {
        assertEquals("IS · Import System — Gestión de Importaciones", Marca.NOMBRE);
        try (var vistas = Files.list(Path.of("src/main/resources/view"))) {
            for (var vista : vistas.filter(p -> p.toString().endsWith(".fxml")).toList()) {
                String contenido = Files.readString(vista).toLowerCase(Locale.ROOT);
                for (String marca : new String[]{"festa", "festiva", "importadora perú", "artículos para eventos"}) {
                    assertFalse(contenido.contains(marca), vista + " conserva " + marca);
                }
            }
        }
    }
}
