package com.ejemplo.crudpersona.ui;

import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.InputStream;

public final class Marca {
    public static final String NOMBRE = "IS · Import System — Gestión de Importaciones";
    private Marca() {}
    public static void aplicarIcono(Stage stage) {
        if (stage == null) return;
        stage.getIcons().clear();
        try (InputStream is = Marca.class.getResourceAsStream("/images/is-logo.png")) {
            if (is != null) {
                stage.getIcons().add(new Image(is));
            }
        } catch (Exception ignored) {
        }
    }
}
