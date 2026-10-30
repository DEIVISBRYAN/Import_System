package com.ejemplo.crudpersona.controller;

import com.ejemplo.crudpersona.service.ERQZReporteTabular;
import com.ejemplo.crudpersona.ui.ExportarMenu;

import com.ejemplo.crudpersona.model.Producto;
import com.ejemplo.crudpersona.repository.DetalleVentaRepository;
import com.ejemplo.crudpersona.repository.ProductoRepository;
import com.ejemplo.crudpersona.service.ERQZDatabaseDateNormalizer;
import com.ejemplo.crudpersona.service.RPLCReposicionServiceI.Proveedor;
import com.ejemplo.crudpersona.ui.PedidosCompraDialog;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class RPLCReposicionController {
    @FXML private ExportarMenu exportarReporte;

    private final ProductoRepository productoRepository;
    private final DetalleVentaRepository detalleVentaRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ERQZDatabaseDateNormalizer dateNormalizer;
    private BigDecimal tipoCambioActual;
    private final com.ejemplo.crudpersona.service.DBQPTipoCambioServiceI cambios;
    private final com.ejemplo.crudpersona.service.RPLCReposicionServiceI pedidos;
    private final ERQZMainController main;
    private final com.ejemplo.crudpersona.service.ERQZCompraServiceI compras;

    @FXML private Text txtUrgentes;
    @FXML private Text txtResumen;
    @FXML private ComboBox<String> cmbFiltro;
    @FXML private TableView<ReposicionItem> tablaReposicion;
    @FXML private TableColumn<ReposicionItem, String> colProducto;
    @FXML private TableColumn<ReposicionItem, String> colProveedor;
    @FXML private TableColumn<ReposicionItem, String> colStock;
    @FXML private TableColumn<ReposicionItem, String> colVentasDia;
    @FXML private TableColumn<ReposicionItem, String> colEntrega;
    @FXML private TableColumn<ReposicionItem, String> colPuntoReposicion;
    @FXML private TableColumn<ReposicionItem, String> colPedir;
    @FXML private TableColumn<ReposicionItem, String> colCuando;
    @FXML private Label lblDetalleProducto;
    @FXML private Label lblDetalleFormula;
    @FXML private Label lblDetalleCosto;
    @FXML private Label lblDetalleEstado;
    @FXML private Label lblDetalleProveedor;
    @FXML private Button btnGenerarPedido;
    @FXML private Button btnAsignarProveedor;
    @FXML private MenuItem menuEditarParametros;

    private final ObservableList<ReposicionItem> items = FXCollections.observableArrayList();
    private List<ReposicionItem> todosLosItems = List.of();

    public RPLCReposicionController(ProductoRepository productoRepository,
                                DetalleVentaRepository detalleVentaRepository,
                                JdbcTemplate jdbcTemplate,
                                ERQZDatabaseDateNormalizer dateNormalizer, com.ejemplo.crudpersona.service.DBQPTipoCambioServiceI cambios,
                                com.ejemplo.crudpersona.service.RPLCReposicionServiceI pedidos, ERQZMainController main,
                                com.ejemplo.crudpersona.service.ERQZCompraServiceI compras) {
        this.productoRepository = productoRepository;
        this.detalleVentaRepository = detalleVentaRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.dateNormalizer = dateNormalizer;
        this.cambios = cambios;
        this.pedidos = pedidos;
        this.main = main;
        this.compras = compras;
    }

    @FXML
    public void initialize() {
        exportarReporte.setReporte(this::reporteReposicion);
        dateNormalizer.normalize();
        cmbFiltro.setItems(FXCollections.observableArrayList("Todos", "Pedir hoy", "Próximos 7 días", "Stock suficiente", "Sin proveedor"));
        cmbFiltro.setValue("Todos");

        colProducto.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().producto()));
        colProveedor.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().proveedor()));
        colProveedor.setCellFactory(column -> new TableCell<>() {
            private final Button asignar = new Button("Asignar");
            {
                asignar.setGraphic(new org.kordamp.ikonli.javafx.FontIcon("fth-plus"));
                asignar.getStyleClass().add("replenishment-assign-link");
                asignar.setOnAction(event -> {
                    ReposicionItem item = getTableRow().getItem();
                    if (item != null) { tablaReposicion.getSelectionModel().select(item); onAsignarProveedor(); }
                });
            }
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty); setText(null); setGraphic(null); setTooltip(null);
                if (empty || value == null) return;
                if (value.isBlank()) { setGraphic(asignar); asignar.setTooltip(new Tooltip("Asignar proveedor a este producto")); }
                else { setText(value); setTooltip(new Tooltip(value)); }
            }
        });
        colStock.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().stock())));
        colVentasDia.setCellValueFactory(data -> new SimpleStringProperty(numero(data.getValue().ventasDia())));
        colEntrega.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().diasEntrega())));
        colPuntoReposicion.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().puntoReposicion())));
        colPedir.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().cantidadPedir() <= 0 ? "-" : String.valueOf(data.getValue().cantidadPedir())));
        colCuando.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().cuando()));
        colCuando.setCellFactory(column -> new TableCell<>() {
            private final Label estado = new Label();
            { setAlignment(Pos.CENTER_RIGHT); }
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(null);
                if (empty || value == null) { setGraphic(null); return; }
                estado.setText(value);
                estado.getStyleClass().setAll("replenishment-status", estiloEstado(value));
                setGraphic(estado);
            }
        });
        colProducto.setCellFactory(column -> new TableCell<>() {
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                setText(empty ? null : value);
                setTooltip(empty || value == null ? null : new Tooltip(value));
            }
        });

        tablaReposicion.setItems(items);
        tablaReposicion.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        btnAsignarProveedor.disableProperty().bind(tablaReposicion.getSelectionModel().selectedItemProperty().isNull());
        btnGenerarPedido.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(() -> {
            ReposicionItem item = tablaReposicion.getSelectionModel().getSelectedItem();
            return item == null || item.cantidadPedir() <= 0 || item.proveedor().isBlank();
        }, tablaReposicion.getSelectionModel().selectedItemProperty()));
        menuEditarParametros.disableProperty().bind(tablaReposicion.getSelectionModel().selectedItemProperty().isNull());
        tablaReposicion.getSelectionModel().selectedItemProperty().addListener((obs, anterior, actual) -> cargarDetalle(actual));
        tablaReposicion.setRowFactory(table -> {
            TableRow<ReposicionItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) onEditarParametros();
            });
            return row;
        });
        cargarReposicion();
    }

    @FXML
    public void onFiltrar() {
        cargarReposicion();
    }

    @FXML
    public void onActualizar() {
        cargarReposicion();
    }

    @FXML public void onAsignarProveedor() {
        ReposicionItem item = seleccionado(); if (item == null) return;
        try {
            List<Proveedor> activos = pedidos.proveedoresActivos();
            Proveedor actual = pedidos.proveedorAsignado(item.productoId());
            Dialog<Proveedor> dialog = new Dialog<>(); dialog.setTitle("Asignar proveedor");
            dialog.initOwner(tablaReposicion.getScene().getWindow()); dialog.initStyle(javafx.stage.StageStyle.TRANSPARENT);
            dialog.getDialogPane().getStyleClass().add("app-dialog");
            ButtonType administrar = new ButtonType("Administrar proveedores", ButtonBar.ButtonData.LEFT);
            dialog.getDialogPane().getButtonTypes().addAll(administrar, ButtonType.CANCEL, ButtonType.OK);
            Label titulo = new Label("Asignar proveedor"); titulo.getStyleClass().add("modal-title");
            Label producto = new Label(item.producto()); producto.setWrapText(true); producto.getStyleClass().add("dialog-form-label");
            Label actualLabel = new Label("Proveedor actual: " + (actual == null ? "Sin proveedor" : actual.nombre())); actualLabel.setWrapText(true);
            actualLabel.getStyleClass().add("text-subtle");
            TextField buscar = new TextField(); buscar.setId("buscarProveedor"); buscar.setPromptText("Buscar proveedor por nombre o RUC");
            ListView<Proveedor> lista = new ListView<>(FXCollections.observableArrayList(activos)); lista.setId("listaProveedores");
            lista.setPrefHeight(220); lista.setMinHeight(100); lista.getStyleClass().add("supplier-picker");
            lista.setPlaceholder(new Label(activos.isEmpty() ? "No hay proveedores activos registrados." : "Sin coincidencias."));
            lista.setCellFactory(view -> new ListCell<>() {
                @Override protected void updateItem(Proveedor proveedor, boolean empty) {
                    super.updateItem(proveedor, empty); setText(null); setGraphic(null);
                    if (empty || proveedor == null) return;
                    Label nombre = new Label(proveedor.nombre()); nombre.setWrapText(true); nombre.setMinWidth(0);
                    nombre.getStyleClass().add("supplier-picker-name");
                    Label detalle = new Label((proveedor.ruc().isBlank() ? "Sin RUC" : "RUC: " + proveedor.ruc())
                            + (proveedor.contacto().isBlank() ? "" : " · " + proveedor.contacto()));
                    detalle.setWrapText(true); detalle.setMinWidth(0); detalle.getStyleClass().add("text-subtle");
                    VBox box = new VBox(4, nombre, detalle); box.setMinWidth(0);
                    box.prefWidthProperty().bind(lista.widthProperty().subtract(44)); setGraphic(box);
                }
            });
            if (actual != null) lista.getItems().stream().filter(p -> p.id().equals(actual.id())).findFirst().ifPresent(lista.getSelectionModel()::select);
            buscar.textProperty().addListener((obs, old, value) -> {
                Proveedor anterior = lista.getSelectionModel().getSelectedItem();
                String filtro = value.trim().toLowerCase(Locale.ROOT);
                lista.setItems(FXCollections.observableArrayList(activos.stream()
                        .filter(p -> (p.nombre() + " " + p.ruc()).toLowerCase(Locale.ROOT).contains(filtro)).toList()));
                if (anterior != null && lista.getItems().contains(anterior)) lista.getSelectionModel().select(anterior);
            });
            buscar.setOnAction(event -> { if (lista.getItems().size() == 1) lista.getSelectionModel().selectFirst(); lista.requestFocus(); event.consume(); });
            Label error = new Label(); error.setWrapText(true); error.getStyleClass().add("validation-error");
            error.setVisible(false); error.setManaged(false);
            VBox contenido = new VBox(14, titulo, producto, actualLabel, new Separator(), buscar, lista, error);
            contenido.getStyleClass().add("custom-modal"); dialog.getDialogPane().setContent(contenido);
            Button guardar = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK); guardar.setText("Guardar proveedor"); guardar.getStyleClass().add("btn-primario");
            guardar.disableProperty().bind(lista.getSelectionModel().selectedItemProperty().isNull());
            ((Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Cancelar");
            guardar.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
                try { pedidos.asignarProveedor(item.productoId(), lista.getSelectionModel().getSelectedItem().id()); }
                catch (RuntimeException e) { error.setText(e.getMessage()); error.setVisible(true); error.setManaged(true); event.consume(); }
            });
            dialog.setResultConverter(button -> button == ButtonType.OK ? lista.getSelectionModel().getSelectedItem() : null);
            final boolean[] abrirProveedores = {false};
            ((Button) dialog.getDialogPane().lookupButton(administrar)).addEventFilter(javafx.event.ActionEvent.ACTION, event -> abrirProveedores[0] = true);
            dialog.showAndWait().ifPresent(proveedor -> cargarReposicion());
            if (abrirProveedores[0]) main.onNavProveedores();
        } catch (RuntimeException e) { mostrarInfo("No se pudo asignar el proveedor", e.getMessage()); }
    }

    @FXML
    public void onVerGrafico() {
        // El grafico usa el mismo calculo de reposicion que la tabla, sin limitarse al filtro visible.
        cargarReposicion();
        List<ReposicionItem> criticos = todosLosItems.stream().filter(i -> "Pedir hoy".equals(i.cuando())).toList();
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Stock crítico");
        if (tablaReposicion.getScene() != null) dialog.initOwner(tablaReposicion.getScene().getWindow());
        dialog.getDialogPane().getStyleClass().add("app-dialog");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        VBox contenido = new VBox(18);
        contenido.getStyleClass().add("custom-modal");
        Label titulo = new Label("Stock crítico"); titulo.getStyleClass().add("modal-title");
        contenido.getChildren().addAll(titulo, new Separator());
        if (criticos.isEmpty()) {
            Label vacio = new Label("Sin productos críticos"); vacio.getStyleClass().add("text-subtle");
            contenido.getChildren().add(vacio);
        } else {
            NumberAxis unidades = new NumberAxis(); unidades.setLabel("Unidades"); unidades.setForceZeroInRange(true);
            CategoryAxis productos = new CategoryAxis();
            BarChart<Number, String> grafico = new BarChart<>(unidades, productos);
            grafico.setId("graficoStockCritico"); grafico.setAnimated(false); grafico.setHorizontalGridLinesVisible(false);
            grafico.setVerticalGridLinesVisible(true); grafico.setCategoryGap(18); grafico.setBarGap(4);
            grafico.setPrefHeight(Math.max(260, Math.min(14, criticos.size()) * 54 + 100));
            grafico.setMinHeight(grafico.getPrefHeight());
            grafico.getStyleClass().add("replenishment-chart");
            XYChart.Series<Number, String> stock = new XYChart.Series<>(); stock.setName("Stock actual");
            XYChart.Series<Number, String> punto = new XYChart.Series<>(); punto.setName("Punto de reposición");
            for (ReposicionItem item : criticos) {
                // SKU evita colisiones entre productos que tienen el mismo nombre.
                String sku = productoRepository.findById(item.productoId()).map(Producto::getSku).orElse("");
                String etiqueta = item.producto().length() > 26 ? item.producto().substring(0, 24) + "..." : item.producto();
                etiqueta += " · " + sku;
                XYChart.Data<Number, String> disponible = new XYChart.Data<>(item.stock(), etiqueta);
                XYChart.Data<Number, String> minimo = new XYChart.Data<>(item.puntoReposicion(), etiqueta);
                String detalle = item.producto() + "\nStock: " + item.stock() + "\nPunto de reposición: " + item.puntoReposicion() + "\nPedir: " + item.cantidadPedir();
                instalarTooltip(disponible, detalle); instalarTooltip(minimo, detalle);
                stock.getData().add(disponible); punto.getData().add(minimo);
            }
            grafico.getData().addAll(stock, punto);
            contenido.getChildren().add(grafico);
        }
        dialog.getDialogPane().setContent(contenido);
        dialog.setOnShown(event -> {
            Button cerrar = (Button) dialog.getDialogPane().lookupButton(ButtonType.CLOSE);
            cerrar.setText("Cerrar"); cerrar.getStyleClass().add("btn-secundario");
        });
        dialog.showAndWait();
    }

    private void instalarTooltip(XYChart.Data<Number, String> dato, String texto) {
        dato.nodeProperty().addListener((obs, old, node) -> {
            if (node != null) Tooltip.install(node, new Tooltip(texto));
        });
    }

    private String estiloEstado(String estado) {
        return "Pedir hoy".equals(estado) ? "replenishment-status-urgent"
                : "Sin proveedor".equals(estado) ? "replenishment-status-warning" : "replenishment-status-ok";
    }

    @FXML
    public void onEditarParametros() {
        ReposicionItem item = seleccionado();
        if (item == null) return;

        Dialog<ConfigForm> dialog = new Dialog<>();
        dialog.setTitle("Parámetros de reposición");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        TextField ventasDia = new TextField(numero(item.ventasDia()));
        TextField diasEntrega = new TextField(String.valueOf(item.diasEntrega()));
        TextField seguridad = new TextField(String.valueOf(item.stockSeguridad()));
        ComboBox<Proveedor> proveedor = new ComboBox<>(); proveedor.setId("proveedorParametros");
        Proveedor ninguno = new Proveedor(null, "Sin proveedor", "", "");
        proveedor.getItems().add(ninguno); proveedor.getItems().addAll(pedidos.proveedoresActivos());
        Proveedor asignado = pedidos.proveedorAsignado(item.productoId());
        proveedor.setValue(proveedor.getItems().stream().filter(p -> asignado != null && asignado.id().equals(p.id())).findFirst().orElse(ninguno));
        proveedor.setMaxWidth(Double.MAX_VALUE); proveedor.setMinWidth(0);
        aplicarCampo(ventasDia, diasEntrega, seguridad);

        VBox root = new VBox(14);
        root.getStyleClass().add("custom-modal");
        Label titulo = new Label(item.producto());
        titulo.getStyleClass().add("modal-title");
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        grid.add(campo("Ventas por día", ventasDia), 0, 0);
        grid.add(campo("Días de entrega", diasEntrega), 1, 0);
        grid.add(campo("Stock seguridad", seguridad), 0, 1);
        grid.add(new VBox(6, new Label("Proveedor"), proveedor), 1, 1);
        root.getChildren().addAll(titulo, new Separator(), grid);
        dialog.getDialogPane().getStyleClass().add("app-dialog");
        dialog.getDialogPane().setContent(root);
        Label error = new Label(); error.setWrapText(true); error.getStyleClass().add("validation-error");
        error.setVisible(false); error.setManaged(false); root.getChildren().add(error);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            try {
                pedidos.guardarParametros(item.productoId(), new BigDecimal(ventasDia.getText().trim().replace(',', '.')),
                        Integer.parseInt(diasEntrega.getText().trim()), Integer.parseInt(seguridad.getText().trim()), proveedor.getValue().id());
            } catch (RuntimeException e) {
                error.setText(e instanceof NumberFormatException ? "Revisa las ventas, los días y el stock de seguridad." : e.getMessage());
                error.setVisible(true); error.setManaged(true); event.consume();
            }
        });
        dialog.setOnShown(event -> {
            Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
            Button cancel = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
            if (ok != null) {
                ok.setText("Guardar parámetros");
                ok.getStyleClass().add("btn-primario");
            }
            if (cancel != null) {
                cancel.setText("Cancelar");
                cancel.getStyleClass().add("btn-secundario");
            }
        });
        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) return null;
            return new ConfigForm(decimalDesdeTexto(ventasDia.getText()), enteroDesdeTexto(diasEntrega.getText(), item.diasEntrega()),
                    enteroDesdeTexto(seguridad.getText(), item.stockSeguridad()), proveedor.getValue().nombre());
        });

        dialog.showAndWait().ifPresent(form -> {
            cargarReposicion();
        });
    }

    @FXML
    public void onGenerarPedido() {
        ReposicionItem item = seleccionado();
        if (item == null) return;
        if (item.cantidadPedir() <= 0) {
            mostrarInfo("Sin pedido necesario", "Este producto todavía no requiere reposición.");
            return;
        }
        if (item.proveedor().isBlank()) { onAsignarProveedor(); return; }
        revisarPedidos(List.copyOf(tablaReposicion.getSelectionModel().getSelectedItems()));
    }

    @FXML
    public void onGenerarPedidoHoy() {
        List<ReposicionItem> urgentes = todosLosItems.stream()
                .filter(item -> item.cantidadPedir() > 0 && "Pedir hoy".equals(item.cuando()))
                .toList();
        if (urgentes.isEmpty()) {
            mostrarInfo("Sin urgentes", "No hay productos marcados para pedir hoy.");
            return;
        }
        revisarPedidos(urgentes);
    }

    private void revisarPedidos(List<ReposicionItem> seleccionados) {
        try {
            var borradores = new java.util.ArrayList<PedidosCompraDialog.Borrador>();
            int sinProveedor = 0;
            for (var item : seleccionados) {
                if (item.cantidadPedir() <= 0) continue;
                var proveedor = pedidos.proveedorAsignado(item.productoId());
                if (proveedor == null) { sinProveedor++; continue; }
                var costoPen = productoRepository.findById(item.productoId()).orElseThrow().getCostoPromedioPen();
                costoPen = costoPen == null ? BigDecimal.ZERO : costoPen;
                var costoUsd = costoPen.divide(cambios.actual(), 6, RoundingMode.HALF_UP);
                borradores.add(new PedidosCompraDialog.Borrador(item.productoId(), proveedor,item.producto(),item.cantidadPedir(),costoUsd,costoPen));
            }
            if (borradores.isEmpty()) { mostrarInfo("Sin productos listos", "Asigna un proveedor a los productos que requieren reposición."); return; }
            if (PedidosCompraDialog.revisar(tablaReposicion.getScene().getWindow(),pedidos,compras,cambios,borradores,sinProveedor)) cargarReposicion();
        } catch (RuntimeException e) { mostrarInfo("No se pudo revisar el pedido",e.getMessage()); }
    }
    @FXML public void onVerPedidos() {
        PedidosCompraDialog.historial(tablaReposicion.getScene().getWindow(),pedidos,compras,cambios,null);
        cargarReposicion();
    }

    @FXML
    public void onReporte() {
        long pedirHoy = items.stream().filter(item -> "Pedir hoy".equals(item.cuando())).count();
        BigDecimal costoPen = items.stream()
                .map(ReposicionItem::costoPen)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        StringBuilder texto = new StringBuilder();
        texto.append("Productos a pedir hoy: ").append(pedirHoy).append("\n");
        texto.append("Costo estimado total: ").append(moneda(costoPen)).append("\n\n");
        items.stream()
                .filter(item -> item.cantidadPedir() > 0)
                .forEach(item -> texto.append("- ")
                        .append(item.producto())
                        .append(" | Pedir: ")
                        .append(item.cantidadPedir())
                        .append(" | ")
                        .append(item.cuando())
                        .append(" | ")
                        .append(moneda(item.costoPen()))
                        .append("\n"));
        Alert alert = new Alert(Alert.AlertType.INFORMATION, texto.toString());
        alert.setHeaderText("Reporte de reposición");
        alert.showAndWait();
    }

    public ERQZReporteTabular reporteReposicion() {
        return new ERQZReporteTabular("Reporte de reposición", "Filas según los filtros aplicados. Cantidades y costos sugeridos; no son pedidos confirmados.",
                List.of("Producto", "Proveedor", "Stock", "Venta por día", "Entrega días", "Punto reposición", "Pedir", "Costo PEN", "Costo USD", "Cuándo"),
                tablaReposicion.getItems().stream().map(i -> ERQZReporteTabular.fila(i.producto(), i.proveedor(), i.stock(), i.ventasDia(), i.diasEntrega(),
                        i.puntoReposicion(), i.cantidadPedir(), i.costoPen(), i.costoUsd(), i.cuando())).toList());
    }

    private void cargarReposicion() {
        tipoCambioActual = cambios.actual();
        ReposicionItem seleccionadoAntes = tablaReposicion.getSelectionModel().getSelectedItem();
        dateNormalizer.normalize();
        Map<Long, BigDecimal> ventasDia = ventasDia();
        String filtro = cmbFiltro.getValue() == null ? "Todos" : cmbFiltro.getValue();
        todosLosItems = productoRepository.findAll().stream()
                .map(producto -> calcularItem(producto, ventasDia.getOrDefault(producto.getId(), BigDecimal.ZERO)))
                .sorted(Comparator.comparing((ReposicionItem i) -> !"Pedir hoy".equals(i.cuando()))
                        .thenComparingInt(ReposicionItem::stock).thenComparing(ReposicionItem::producto))
                .toList();
        items.setAll(todosLosItems.stream().filter(item -> coincideFiltro(item, filtro)).toList());
        long pedirHoy = todosLosItems.stream().filter(item -> "Pedir hoy".equals(item.cuando())).count();
        txtUrgentes.setText(pedirHoy + (pedirHoy == 1 ? " producto" : " productos"));
        txtUrgentes.getStyleClass().remove("replenishment-clear");
        if (pedirHoy == 0) txtUrgentes.getStyleClass().add("replenishment-clear");
        txtResumen.setText(pedirHoy == 1 ? " hay que pedirlo hoy para no quedarse sin stock."
                : pedirHoy == 0 ? " requieren reposición hoy." : " hay que pedirlos hoy para no quedarse sin stock.");
        tablaReposicion.setPrefHeight(48 + Math.max(1, Math.min(8, items.size())) * 56 + 18);
        ReposicionItem vigente = items.stream()
                .filter(i -> seleccionadoAntes != null && i.productoId().equals(seleccionadoAntes.productoId()))
                .findFirst().orElse(null);
        if (vigente != null) tablaReposicion.getSelectionModel().select(vigente);
        else {
            tablaReposicion.getSelectionModel().clearSelection();
            cargarDetalle(null);
        }
        main.refrescarIndicadores();
    }

    private ReposicionItem calcularItem(Producto producto, BigDecimal ventasDiaCalculada) {
        Config config = config(producto.getId(), ventasDiaCalculada);
        int stock = entero(producto.getStockActual());
        BigDecimal ventasDia = config.ventasDia().compareTo(BigDecimal.ZERO) > 0 ? config.ventasDia() : ventasDiaCalculada;
        int puntoReposicion = ventasDia.multiply(BigDecimal.valueOf(config.diasEntrega()))
                .add(BigDecimal.valueOf(config.stockSeguridad()))
                .setScale(0, RoundingMode.CEILING)
                .intValue();
        int objetivo = ventasDia.multiply(BigDecimal.valueOf(config.diasEntrega() + 30L))
                .add(BigDecimal.valueOf(config.stockSeguridad()))
                .setScale(0, RoundingMode.CEILING)
                .intValue();
        int pedir = Math.max(0, objetivo - stock);
        String cuando = cuando(stock, ventasDia, puntoReposicion, pedir);
        BigDecimal costoPen = decimal(producto.getCostoPromedioPen()).multiply(BigDecimal.valueOf(pedir));
        BigDecimal costoUsd = costoPen.divide(tipoCambioActual, 2, RoundingMode.HALF_UP);
        return new ReposicionItem(producto.getId(), producto.getDescripcion(), stock, ventasDia,
                config.diasEntrega(), config.stockSeguridad(), puntoReposicion, pedir,
                config.proveedor(), costoPen, costoUsd, cuando);
    }

    private String cuando(int stock, BigDecimal ventasDia, int puntoReposicion, int pedir) {
        if (pedir <= 0) return "Stock suficiente";
        if (stock <= puntoReposicion) return "Pedir hoy";
        if (ventasDia.compareTo(BigDecimal.ZERO) <= 0) return "Stock suficiente";
        int dias = BigDecimal.valueOf(stock - puntoReposicion)
                .divide(ventasDia, 0, RoundingMode.FLOOR)
                .intValue();
        if (dias <= 7) return "En " + dias + " días";
        return "Stock suficiente";
    }

    private boolean coincideFiltro(ReposicionItem item, String filtro) {
        return "Todos".equals(filtro)
                || ("Sin proveedor".equals(filtro) ? item.proveedor().isBlank() : filtro.equals(item.cuando()))
                || ("Próximos 7 días".equals(filtro) && item.cuando().startsWith("En "));
    }

    private Map<Long, BigDecimal> ventasDia() {
        Map<Long, BigDecimal> mapa = new HashMap<>();
        for (Object[] fila : detalleVentaRepository.findVentasDiaUltimos30Dias()) {
            Number id = (Number) fila[0];
            Number valor = (Number) fila[1];
            mapa.put(id.longValue(), BigDecimal.valueOf(valor.doubleValue()).setScale(2, RoundingMode.HALF_UP));
        }
        return mapa;
    }

    private Config config(Long productoId, BigDecimal ventasDiaCalculada) {
        Proveedor asignado = pedidos.proveedorAsignado(productoId);
        String proveedor = asignado == null ? "" : asignado.nombre();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbcTemplate, "SELECT * FROM reposicion_config WHERE producto_id = ?"), productoId);
        if (rows.isEmpty()) {
            return new Config(ventasDiaCalculada, 30, 10, proveedor);
        }
        Map<String, Object> row = rows.get(0);
        return new Config(
                bigDecimal(row.get("ventas_dia"), ventasDiaCalculada),
                entero(row.get("dias_entrega"), 30),
                entero(row.get("stock_seguridad"), 10),
                proveedor
        );
    }

    private ReposicionItem seleccionado() {
        ReposicionItem item = tablaReposicion.getSelectionModel().getSelectedItem();
        if (item == null) {
            mostrarInfo("Selecciona un producto", "Primero selecciona un producto de la tabla.");
        }
        return item;
    }

    private void cargarDetalle(ReposicionItem item) {
        btnAsignarProveedor.setText(item != null && !item.proveedor().isBlank() ? "Cambiar proveedor" : "Asignar proveedor");
        if (item == null) {
            lblDetalleProducto.setText("-");
            lblDetalleFormula.setText("-");
            lblDetalleCosto.setText("-");
            lblDetalleEstado.setText("-");
            lblDetalleProveedor.setText("-");
            return;
        }
        lblDetalleProducto.setText(item.producto());
        lblDetalleFormula.setText("Punto = ventas/día (" + numero(item.ventasDia()) + ") x entrega (" + item.diasEntrega() + ") + seguridad (" + item.stockSeguridad() + ")");
        lblDetalleCosto.setText(moneda(item.costoPen()) + " / US$ " + item.costoUsd());
        lblDetalleEstado.setText(item.cuando());
        lblDetalleEstado.getStyleClass().setAll("replenishment-status", estiloEstado(item.cuando()));
        lblDetalleProveedor.setText(item.proveedor().isBlank() ? "Sin proveedor" : item.proveedor());
    }

    private VBox campo(String label, TextField field) {
        Label etiqueta = new Label(label);
        etiqueta.getStyleClass().add("dialog-form-label");
        VBox box = new VBox(6, etiqueta, field);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private void aplicarCampo(TextField... fields) {
        for (TextField field : fields) {
            field.getStyleClass().add("dialog-form-field");
            field.setMaxWidth(Double.MAX_VALUE);
        }
    }

    private BigDecimal decimalDesdeTexto(String value) {
        try {
            return new BigDecimal(value.trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private int enteroDesdeTexto(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private BigDecimal bigDecimal(Object value, BigDecimal fallback) {
        if (value == null) return fallback;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number number) return BigDecimal.valueOf(number.doubleValue());
        return fallback;
    }

    private BigDecimal decimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private int entero(Integer value) {
        return value == null ? 0 : value;
    }

    private int entero(Object value, int fallback) {
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private String texto(Object value) {
        return value == null ? "" : value.toString();
    }

    private String moneda(BigDecimal value) {
        return String.format("S/ %.2f", decimal(value));
    }

    private String numero(BigDecimal value) {
        return decimal(value).stripTrailingZeros().toPlainString();
    }

    private void mostrarInfo(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, mensaje);
        alert.setHeaderText(titulo);
        alert.showAndWait();
    }

    public record ReposicionItem(Long productoId, String producto, int stock, BigDecimal ventasDia,
                                 int diasEntrega, int stockSeguridad, int puntoReposicion,
                                 int cantidadPedir, String proveedor, BigDecimal costoPen,
                                 BigDecimal costoUsd, String cuando) {
    }

    public record Config(BigDecimal ventasDia, int diasEntrega, int stockSeguridad, String proveedor) {
    }

    public record ConfigForm(BigDecimal ventasDia, int diasEntrega, int stockSeguridad, String proveedor) {
    }
}
