package com.ejemplo.crudpersona.config;

import atlantafx.base.theme.PrimerLight;
import com.ejemplo.crudpersona.ui.AppStyles;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class DBQPStageInitializer implements ApplicationListener<RPLCStageReadyEvent> {

    private final ApplicationContext applicationContext;

    public DBQPStageInitializer(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void onApplicationEvent(RPLCStageReadyEvent event) {
        try {
            // 1. Tema de colores claros por defecto (AtlantaFX PrimerLight)
            Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
            AppStyles.install();

            Stage stage = event.getStage();
            FXMLLoader fxmlLoader = new FXMLLoader(getClass().getResource("/view/login-view.fxml"));
            fxmlLoader.setControllerFactory(applicationContext::getBean);

            Parent root = fxmlLoader.load();
            Scene scene = new Scene(root, 1024, 680);
            AppStyles.apply(scene);

            stage.setTitle(com.ejemplo.crudpersona.ui.Marca.NOMBRE + " · Sistema Integral de Gestión");
            com.ejemplo.crudpersona.ui.Marca.aplicarIcono(stage);
            stage.setMinWidth(640);
            stage.setMinHeight(480);
            stage.setScene(scene);
            stage.show();

        } catch (IOException e) {
            throw new RuntimeException("Error al inicializar la vista de Login modular", e);
        }
    }
}
