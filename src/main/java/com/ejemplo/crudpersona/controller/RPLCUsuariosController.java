package com.ejemplo.crudpersona.controller;

import com.ejemplo.crudpersona.model.Rol;
import com.ejemplo.crudpersona.model.Usuario;
import com.ejemplo.crudpersona.repository.UsuarioRepository;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class RPLCUsuariosController {

    private final UsuarioRepository usuarioRepository;
    private final com.ejemplo.crudpersona.service.RPLCUsuariosServiceI usuariosService;

    @FXML private Label lblResumen;
    @FXML private TableView<Usuario> tablaUsuarios;
    @FXML private TableColumn<Usuario, String> colUsuario;
    @FXML private TableColumn<Usuario, String> colNombre;
    @FXML private TableColumn<Usuario, String> colRol;
    @FXML private TableColumn<Usuario, String> colEstado;
    @FXML private Label lblDetalleUsuario;
    @FXML private Label lblDetalleRol;
    @FXML private Label lblDetallePermisos;

    public RPLCUsuariosController(UsuarioRepository usuarioRepository, com.ejemplo.crudpersona.service.RPLCUsuariosServiceI usuariosService) {
        this.usuarioRepository = usuarioRepository;
        this.usuariosService = usuariosService;
    }

    @FXML
    public void initialize() {
        colUsuario.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getUsername()));
        colNombre.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getNombreCompleto()));
        colRol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getRol().getTitulo()));
        colEstado.setCellValueFactory(data -> new SimpleStringProperty(Boolean.TRUE.equals(data.getValue().getActivo()) ? "Activo" : "Inactivo"));
        tablaUsuarios.getSelectionModel().selectedItemProperty().addListener((obs, old, usuario) -> cargarDetalle(usuario));
        cargar();
    }

    @FXML
    public void onNuevoUsuario() {
        Dialog<UsuarioForm> dialog = new Dialog<>();
        dialog.setTitle("Nuevo usuario");
        dialog.getDialogPane().getStyleClass().add("app-dialog");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField username = new TextField();
        TextField nombre = new TextField();
        PasswordField clave = new PasswordField();
        ComboBox<Rol> rol = new ComboBox<>(FXCollections.observableArrayList(Rol.values()));
        rol.setValue(Rol.CAJERO);
        username.setPromptText("usuario");
        nombre.setPromptText("Nombre completo");
        clave.setPromptText("Contraseña");

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(10);
        form.add(new Label("Usuario"), 0, 0);
        form.add(username, 1, 0);
        form.add(new Label("Nombre"), 0, 1);
        form.add(nombre, 1, 1);
        form.add(new Label("Contraseña"), 0, 2);
        form.add(clave, 1, 2);
        form.add(new Label("Rol"), 0, 3);
        form.add(rol, 1, 3);

        VBox root = new VBox(14, new Label("Registrar acceso al sistema"), form);
        root.getStyleClass().add("custom-modal");
        dialog.getDialogPane().setContent(root);
        prepararBotones(dialog, "Guardar usuario");
        dialog.setResultConverter(button -> button == ButtonType.OK
                ? new UsuarioForm(username.getText().trim(), nombre.getText().trim(), clave.getText(), rol.getValue())
                : null);
        dialog.showAndWait().ifPresent(formulario -> {
            if (formulario.username().isBlank() || formulario.nombre().isBlank() || formulario.clave().isBlank()) {
                mostrarError("Datos incompletos", "Completa usuario, nombre y contraseña.");
                return;
            }
            try {
                usuariosService.crear(formulario.username(), formulario.nombre(), formulario.clave(), formulario.rol());
                cargar();
            } catch (RuntimeException e) { mostrarError("No se pudo crear el usuario", e.getMessage()); }
        });
    }

    @FXML
    public void onCambiarRol() {
        Usuario usuario = tablaUsuarios.getSelectionModel().getSelectedItem();
        if (usuario == null) return;
        ChoiceDialog<Rol> dialog = new ChoiceDialog<>(usuario.getRol(), Arrays.asList(Rol.values()));
        dialog.setTitle("Cambiar rol");
        dialog.setHeaderText(usuario.getNombreCompleto());
        dialog.setContentText("Nuevo rol:");
        dialog.showAndWait().ifPresent(rol -> {
            try { usuariosService.cambiarRol(usuario.getId(), rol); cargar(); }
            catch (RuntimeException e) { mostrarError("No se pudo cambiar el rol", e.getMessage()); }
        });
    }

    @FXML
    public void onCambiarClave() {
        Usuario usuario = tablaUsuarios.getSelectionModel().getSelectedItem();
        if (usuario == null) return;
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Cambiar contraseña");
        dialog.getDialogPane().getStyleClass().add("app-dialog");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        PasswordField clave = new PasswordField();
        clave.setPromptText("Nueva contraseña");
        VBox root = new VBox(12, new Label("Nueva contraseña para " + usuario.getNombreCompleto()), clave);
        root.getStyleClass().add("custom-modal");
        dialog.getDialogPane().setContent(root);
        prepararBotones(dialog, "Guardar clave");
        dialog.setResultConverter(button -> button == ButtonType.OK ? clave.getText() : null);
        dialog.showAndWait().ifPresent(valor -> {
            if (valor == null || valor.isBlank()) {
                mostrarError("Contraseña vacía", "Ingresa una nueva contraseña.");
                return;
            }
            try { usuariosService.cambiarClave(usuario.getId(), valor); cargar(); }
            catch (RuntimeException e) { mostrarError("No se pudo cambiar la contraseña", e.getMessage()); }
        });
    }

    @FXML
    public void onCambiarEstado() {
        Usuario usuario = tablaUsuarios.getSelectionModel().getSelectedItem();
        if (usuario == null) return;
        try { usuariosService.cambiarEstado(usuario.getId()); cargar(); }
        catch (RuntimeException e) { mostrarError("No se pudo cambiar el estado", e.getMessage()); }
    }

    private void cargar() {
        var usuarios = usuarioRepository.findAll();
        tablaUsuarios.setItems(FXCollections.observableArrayList(usuarios));
        lblResumen.setText(usuarios.size() + " usuarios");
        if (!usuarios.isEmpty()) {
            tablaUsuarios.getSelectionModel().selectFirst();
        }
    }

    private void cargarDetalle(Usuario usuario) {
        if (usuario == null) {
            lblDetalleUsuario.setText("-");
            lblDetalleRol.setText("-");
            lblDetallePermisos.setText("-");
            return;
        }
        lblDetalleUsuario.setText(usuario.getNombreCompleto() + " · " + usuario.getUsername());
        lblDetalleRol.setText(usuario.getRol().getTitulo());
        lblDetallePermisos.setText(permisos(usuario.getRol()));
    }

    private String permisos(Rol rol) {
        return switch (rol) {
            case ADMINISTRADOR -> "Acceso total: ventas, caja, inventario, compras, comprobantes, usuarios y configuración.";
            case GERENTE -> "Dashboard, ventas, caja, inventario, almacén, reposición, compras, proveedores, tipo de cambio y comprobantes.";
            case CAJERO -> "Nueva venta, historial de ventas y caja.";
            case ALMACENERO -> "Inventario, reporte de stock, almacén, reposición, compras y proveedores.";
            case CONTADOR -> "Dashboard, ventas, caja, tipo de cambio y comprobantes SUNAT.";
        };
    }

    private void prepararBotones(Dialog<?> dialog, String textoOk) {
        dialog.setOnShown(event -> {
            Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
            Button cancel = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
            if (ok != null) {
                ok.setText(textoOk);
                ok.getStyleClass().add("btn-primario");
            }
            if (cancel != null) {
                cancel.setText("Cancelar");
                cancel.getStyleClass().add("btn-secundario");
            }
        });
    }

    private void mostrarError(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.ERROR, mensaje);
        alert.setHeaderText(titulo);
        alert.showAndWait();
    }

    private record UsuarioForm(String username, String nombre, String clave, Rol rol) {
    }
}
