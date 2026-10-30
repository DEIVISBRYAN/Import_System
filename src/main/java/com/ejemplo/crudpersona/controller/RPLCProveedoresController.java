package com.ejemplo.crudpersona.controller;

import com.ejemplo.crudpersona.service.ERQZDatabaseDateNormalizer;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class RPLCProveedoresController {

    private final JdbcTemplate jdbcTemplate;
    private final ERQZDatabaseDateNormalizer dateNormalizer;

    @FXML private TextField txtBuscar;
    @FXML private ComboBox<String> cmbEstado;
    @FXML private TableView<ProveedorItem> tablaProveedores;
    @FXML private TableColumn<ProveedorItem, String> colProveedor;
    @FXML private TableColumn<ProveedorItem, String> colRuc;
    @FXML private TableColumn<ProveedorItem, String> colContacto;
    @FXML private TableColumn<ProveedorItem, String> colTelefono;
    @FXML private TableColumn<ProveedorItem, String> colCorreo;
    @FXML private TableColumn<ProveedorItem, String> colUltimaCompra;
    @FXML private TableColumn<ProveedorItem, String> colTotalComprado;
    @FXML private TableColumn<ProveedorItem, String> colEstado;
    @FXML private Label lblRazon;
    @FXML private Label lblRuc;
    @FXML private Label lblContacto;
    @FXML private Label lblTelefono;
    @FXML private Label lblCorreo;
    @FXML private Label lblEstado;
    @FXML private Label lblCompras;
    @FXML private TextArea txtHistorial;

    private final ObservableList<ProveedorItem> proveedores = FXCollections.observableArrayList();

    public RPLCProveedoresController(JdbcTemplate jdbcTemplate, ERQZDatabaseDateNormalizer dateNormalizer) {
        this.jdbcTemplate = jdbcTemplate;
        this.dateNormalizer = dateNormalizer;
    }

    @FXML
    public void initialize() {
        dateNormalizer.normalize();
        cmbEstado.setItems(FXCollections.observableArrayList("Todos", "ACTIVO", "INACTIVO", "OBSERVADO"));
        cmbEstado.setValue("Todos");

        colProveedor.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().razonSocial()));
        colRuc.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().ruc()));
        colContacto.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().contacto()));
        colTelefono.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().telefono()));
        colCorreo.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().correo()));
        colUltimaCompra.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().ultimaCompra()));
        colTotalComprado.setCellValueFactory(data -> new SimpleStringProperty("S/ " + String.format("%.2f", data.getValue().totalComprado())));
        colEstado.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().estado()));

        tablaProveedores.setItems(proveedores);
        tablaProveedores.getSelectionModel().selectedItemProperty().addListener((obs, old, item) -> cargarDetalle(item));
        cargarProveedores();
    }

    @FXML
    public void onBuscar() {
        cargarProveedores();
    }

    @FXML
    public void onNuevo() {
        dialogProveedor(null).showAndWait().ifPresent(this::crearProveedor);
    }

    @FXML
    public void onEditar() {
        ProveedorItem item = seleccionado();
        if (item == null) return;
        dialogProveedor(item).showAndWait().ifPresent(form -> actualizarProveedor(item.id(), form));
    }

    @FXML
    public void onCambiarEstado() {
        ProveedorItem item = seleccionado();
        if (item == null) return;
        String nuevoEstado = "ACTIVO".equals(item.estado()) ? "INACTIVO" : "ACTIVO";
        jdbcTemplate.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbcTemplate, "UPDATE proveedores SET estado = ? WHERE id = ?"), nuevoEstado, item.id());
        cargarProveedores();
    }

    @FXML
    public void onObservar() {
        ProveedorItem item = seleccionado();
        if (item == null) return;
        jdbcTemplate.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbcTemplate, "UPDATE proveedores SET estado = 'OBSERVADO' WHERE id = ?"), item.id());
        cargarProveedores();
    }

    @FXML
    public void onEliminar() {
        ProveedorItem item = seleccionado();
        if (item == null) return;
        Long compras = jdbcTemplate.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbcTemplate, "SELECT COUNT(*) FROM compras_dua WHERE proveedor_id = ?"), Long.class, item.id());
        Long asignaciones = jdbcTemplate.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbcTemplate, "SELECT count(*) FROM reposicion_config WHERE proveedor_id=?"), Long.class, item.id());
        Long pedidos = jdbcTemplate.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbcTemplate, "SELECT count(*) FROM pedidos_compra WHERE proveedor_id=?"), Long.class, item.id());
        Long pendientes = jdbcTemplate.queryForObject(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbcTemplate, "SELECT count(*) FROM borradores_dua WHERE proveedor_id=?"),Long.class,item.id());
        if (compras != null && compras > 0 || asignaciones != null && asignaciones > 0 || pedidos != null && pedidos > 0 || pendientes != null && pendientes > 0) {
            mostrarError("No se puede eliminar", "Este proveedor tiene compras, DUA pendientes, pedidos o productos asignados. Puedes desactivarlo.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setHeaderText("Eliminar proveedor");
        confirm.setContentText("¿Deseas eliminar " + item.razonSocial() + "?");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        try { jdbcTemplate.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbcTemplate, "DELETE FROM proveedores WHERE id = ?"), item.id()); }
        catch (org.springframework.dao.DataIntegrityViolationException e) {
            mostrarError("Proveedor en uso", "Este proveedor está vinculado a una compra, pedido o producto. Puedes desactivarlo."); return;
        }
        cargarProveedores();
    }

    private void cargarProveedores() {
        String filtro = "%" + texto(txtBuscar.getText()).toLowerCase() + "%";
        String estado = cmbEstado.getValue() == null ? "Todos" : cmbEstado.getValue();
        proveedores.setAll(jdbcTemplate.query(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbcTemplate, """
                SELECT p.*,
                       COALESCE(SUM(c.total_landed_pen), 0) total_comprado,
                       COALESCE(MAX(c.fecha_ingreso), '-') ultima_compra,
                       COUNT(c.id) compras
                FROM proveedores p
                LEFT JOIN compras_dua c ON c.proveedor_id = p.id
                WHERE (? = 'Todos' OR p.estado = ?)
                  AND (
                    lower(COALESCE(p.razon_social, '')) LIKE ?
                    OR lower(COALESCE(p.ruc, '')) LIKE ?
                    OR lower(COALESCE(p.contacto, '')) LIKE ?
                    OR lower(COALESCE(p.telefono, '')) LIKE ?
                  )
                GROUP BY p.id
                ORDER BY p.razon_social
                """), (rs, rowNum) -> new ProveedorItem(
                rs.getLong("id"),
                texto(rs.getString("ruc")),
                texto(rs.getString("razon_social")),
                texto(rs.getString("contacto")),
                texto(rs.getString("telefono")),
                texto(rs.getString("correo")),
                texto(rs.getString("direccion")),
                texto(rs.getString("pais")),
                texto(rs.getString("estado")),
                texto(rs.getString("ultima_compra")),
                rs.getDouble("total_comprado"),
                rs.getLong("compras")
        ), estado, estado, filtro, filtro, filtro, filtro));

        if (!proveedores.isEmpty()) {
            tablaProveedores.getSelectionModel().selectFirst();
        } else {
            cargarDetalle(null);
        }
    }

    private Dialog<ProveedorForm> dialogProveedor(ProveedorItem item) {
        Dialog<ProveedorForm> dialog = new Dialog<>();
        dialog.setTitle(item == null ? "Nuevo proveedor" : "Editar proveedor");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField razon = new TextField(item == null ? "" : item.razonSocial());
        TextField ruc = new TextField(item == null ? "" : item.ruc());
        TextField contacto = new TextField(item == null ? "" : item.contacto());
        TextField telefono = new TextField(item == null ? "" : item.telefono());
        TextField correo = new TextField(item == null ? "" : item.correo());
        TextField direccion = new TextField(item == null ? "" : item.direccion());
        TextField pais = new TextField(item == null ? "Perú" : item.pais());
        ComboBox<String> estado = new ComboBox<>(FXCollections.observableArrayList("ACTIVO", "INACTIVO", "OBSERVADO"));
        estado.setValue(item == null ? "ACTIVO" : item.estado());

        aplicarCampo(razon, ruc, contacto, telefono, correo, direccion, pais);
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.add(campo("Razón social *", razon), 0, 0, 2, 1);
        grid.add(campo("RUC", ruc), 0, 1);
        grid.add(campo("Contacto", contacto), 1, 1);
        grid.add(campo("Teléfono", telefono), 0, 2);
        grid.add(campo("Correo", correo), 1, 2);
        grid.add(campo("Dirección", direccion), 0, 3);
        grid.add(campo("País", pais), 1, 3);
        grid.add(new VBox(6, etiqueta("Estado"), estado), 0, 4);

        VBox root = new VBox(14, new Label(item == null ? "Registrar proveedor" : "Editar proveedor"), new Separator(), grid);
        root.getStyleClass().add("custom-modal");
        ((Label) root.getChildren().get(0)).getStyleClass().add("modal-title");
        dialog.getDialogPane().getStyleClass().add("app-dialog");
        dialog.getDialogPane().setContent(root);
        dialog.setOnShown(event -> {
            Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
            Button cancel = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
            if (ok != null) {
                ok.setText(item == null ? "Guardar proveedor" : "Guardar cambios");
                ok.getStyleClass().add("btn-primario");
            }
            if (cancel != null) {
                cancel.setText("Cancelar");
                cancel.getStyleClass().add("btn-secundario");
            }
        });
        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) return null;
            return new ProveedorForm(texto(ruc.getText()), texto(razon.getText()), texto(contacto.getText()),
                    texto(telefono.getText()), texto(correo.getText()), texto(direccion.getText()),
                    texto(pais.getText()), estado.getValue());
        });
        return dialog;
    }

    private void crearProveedor(ProveedorForm form) {
        if (!validar(form, null)) return;
        jdbcTemplate.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbcTemplate, """
                INSERT INTO proveedores(ruc, razon_social, contacto, telefono, correo, direccion, pais, estado)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """), blankToNull(form.ruc()), form.razonSocial(), form.contacto(), form.telefono(),
                form.correo(), form.direccion(), form.pais(), form.estado());
        cargarProveedores();
    }

    private void actualizarProveedor(Long id, ProveedorForm form) {
        if (!validar(form, id)) return;
        jdbcTemplate.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbcTemplate, """
                UPDATE proveedores
                SET ruc = ?, razon_social = ?, contacto = ?, telefono = ?, correo = ?, direccion = ?, pais = ?, estado = ?
                WHERE id = ?
                """), blankToNull(form.ruc()), form.razonSocial(), form.contacto(), form.telefono(),
                form.correo(), form.direccion(), form.pais(), form.estado(), id);
        jdbcTemplate.update(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbcTemplate, """
                UPDATE compras_dua
                SET proveedor = ?, ruc = ?
                WHERE proveedor_id = ?
                """), form.razonSocial(), form.ruc(), id);
        cargarProveedores();
    }

    private boolean validar(ProveedorForm form, Long idActual) {
        if (form.razonSocial().isBlank()) {
            mostrarError("Datos incompletos", "Ingresa la razón social.");
            return false;
        }
        if (!form.ruc().isBlank()) {
            List<Map<String, Object>> repetidos = jdbcTemplate.queryForList(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbcTemplate, "SELECT id FROM proveedores WHERE ruc = ?"), form.ruc());
            boolean duplicado = repetidos.stream().anyMatch(row -> !String.valueOf(row.get("id")).equals(String.valueOf(idActual)));
            if (duplicado) {
                mostrarError("RUC duplicado", "Ya existe un proveedor con ese RUC.");
                return false;
            }
        }
        return true;
    }

    private void cargarDetalle(ProveedorItem item) {
        if (item == null) {
            lblRazon.setText("-");
            lblRuc.setText("-");
            lblContacto.setText("-");
            lblTelefono.setText("-");
            lblCorreo.setText("-");
            lblEstado.setText("-");
            lblCompras.setText("-");
            txtHistorial.setText("");
            return;
        }
        lblRazon.setText(item.razonSocial());
        lblRuc.setText(item.ruc().isBlank() ? "-" : item.ruc());
        lblContacto.setText(item.contacto().isBlank() ? "-" : item.contacto());
        lblTelefono.setText(item.telefono().isBlank() ? "-" : item.telefono());
        lblCorreo.setText(item.correo().isBlank() ? "-" : item.correo());
        lblEstado.setText(item.estado());
        lblCompras.setText(item.compras() + " compras · S/ " + String.format("%.2f", item.totalComprado()));
        txtHistorial.setText(historial(item.id()));
    }

    private String historial(Long proveedorId) {
        List<String> compras = jdbcTemplate.query(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbcTemplate, """
                SELECT fecha_ingreso, numero_dua, total_landed_pen
                FROM compras_dua
                WHERE proveedor_id = ?
                ORDER BY id DESC
                LIMIT 8
                """), (rs, rowNum) -> rs.getString("fecha_ingreso") + " | DUA " + rs.getString("numero_dua")
                + " | S/ " + String.format("%.2f", rs.getDouble("total_landed_pen")), proveedorId);
        return compras.isEmpty() ? "Sin compras registradas." : String.join("\n", compras);
    }

    private ProveedorItem seleccionado() {
        ProveedorItem item = tablaProveedores.getSelectionModel().getSelectedItem();
        if (item == null) {
            mostrarError("Selecciona un proveedor", "Primero selecciona un proveedor.");
        }
        return item;
    }

    private VBox campo(String texto, TextField field) {
        return new VBox(6, etiqueta(texto), field);
    }

    private Label etiqueta(String texto) {
        Label label = new Label(texto);
        label.getStyleClass().add("dialog-form-label");
        return label;
    }

    private void aplicarCampo(TextField... fields) {
        for (TextField field : fields) {
            field.getStyleClass().add("dialog-form-field");
            field.setMaxWidth(Double.MAX_VALUE);
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private String texto(String value) {
        return value == null ? "" : value.trim();
    }

    private void mostrarError(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.ERROR, mensaje);
        alert.setHeaderText(titulo);
        alert.showAndWait();
    }

    public record ProveedorItem(Long id, String ruc, String razonSocial, String contacto, String telefono,
                                String correo, String direccion, String pais, String estado,
                                String ultimaCompra, double totalComprado, long compras) {
    }

    public record ProveedorForm(String ruc, String razonSocial, String contacto, String telefono, String correo,
                                String direccion, String pais, String estado) {
    }
}
