package com.ejemplo.crudpersona.controller;

import com.ejemplo.crudpersona.model.Usuario;
import com.ejemplo.crudpersona.service.ERQZAuthService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.stage.Stage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

@Component
public class RPLCLoginController {

    @Autowired
    private ERQZAuthService authService;

    @Autowired
    private ApplicationContext applicationContext;

    @FXML private TextField txtUsername;
    @FXML private PasswordField txtPassword;
    @FXML private Label lblError;
    @FXML private Label lblAccesoLocal;
    @FXML private ScrollPane loginPage;
    @FXML private VBox loginVisual;
    @FXML private HBox loginCompactBrand;

    // Constructor vacío por defecto requerido por FXMLLoader
    public RPLCLoginController() {
    }

    public RPLCLoginController(ERQZAuthService authService, ApplicationContext applicationContext) {
        this.authService = authService;
        this.applicationContext = applicationContext;
    }

    @FXML
    public void initialize() {
        if (lblAccesoLocal != null) {
            boolean demo = applicationContext != null && applicationContext.getEnvironment()
                    .getProperty("app.demo.users", Boolean.class, false);
            lblAccesoLocal.setText(demo ? "Prueba: admin.demo  |  Contraseña: Demo12345" : "");
            lblAccesoLocal.setVisible(demo);
            lblAccesoLocal.setManaged(demo);
        }
        loginPage.sceneProperty().addListener((obs, old, scene) -> {
            if (scene == null) return;
            scene.widthProperty().addListener((widthObs, oldWidth, width) -> ajustarLogin(width.doubleValue()));
            ajustarLogin(scene.getWidth());
        });
        if (lblError != null) {
            lblError.setVisible(false);
            lblError.setManaged(false);
        }
        if (txtUsername != null) {
            Platform.runLater(() -> txtUsername.requestFocus());
        }
    }

    private void ajustarLogin(double width) {
        boolean compact = width < 900;
        loginVisual.setVisible(!compact);
        loginVisual.setManaged(!compact);
        loginCompactBrand.setVisible(compact);
        loginCompactBrand.setManaged(compact);
        loginPage.getStyleClass().remove("login-compact");
        if (compact) loginPage.getStyleClass().add("login-compact");
    }

    @FXML
    public void onIngresar(ActionEvent event) {
        if (lblError != null) {
            lblError.setVisible(false);
            lblError.setManaged(false);
        }

        try {
            Usuario usuario = authService.autenticar(txtUsername.getText(), txtPassword.getText());
            cargarVentanaPrincipal(usuario);
        } catch (Exception ex) {
            mostrarError(ex.getMessage());
            if (txtPassword != null) {
                txtPassword.clear();
                txtPassword.requestFocus();
            }
        }
    }

    private void cargarVentanaPrincipal(Usuario usuario) {
        try {
            var fxmlResource = getClass().getResource("/view/main-view.fxml");
            if (fxmlResource == null) {
                throw new IllegalStateException("No se encontró el archivo /view/main-view.fxml en resources");
            }

            FXMLLoader loader = new FXMLLoader(fxmlResource);
            if (applicationContext != null) {
                loader.setControllerFactory(applicationContext::getBean);
            }
            Parent root = loader.load();

            ERQZMainController mainController = loader.getController();
            if (mainController != null) {
                mainController.setUsuarioActual(usuario);
            }

            Stage stage = (Stage) txtUsername.getScene().getWindow();
            Scene scene = new Scene(root, 1280, 800);

            var cssResource = getClass().getResource("/css/app.css");
            if (cssResource != null) {
                scene.getStylesheets().add(cssResource.toExternalForm());
            }

            stage.setScene(scene);
            if (usuario != null && usuario.getRol() != null) {
                stage.setTitle(com.ejemplo.crudpersona.ui.Marca.NOMBRE + " · " + usuario.getNombreCompleto() + " (" + usuario.getRol().getTitulo() + ")");
            } else {
                stage.setTitle(com.ejemplo.crudpersona.ui.Marca.NOMBRE + " · Sistema Integral de Gestión");
            }
            com.ejemplo.crudpersona.ui.Marca.aplicarIcono(stage);
            stage.setMaximized(true);

        } catch (Throwable t) {
            t.printStackTrace();
            Throwable cause = t;
            while (cause.getCause() != null) {
                cause = cause.getCause();
            }
            mostrarError("Error al cargar la ventana principal: " + cause.getMessage());
        }
    }

    private void mostrarError(String mensaje) {
        if (lblError != null) {
            lblError.setText(mensaje);
            lblError.setVisible(true);
            lblError.setManaged(true);
        }
    }
}
