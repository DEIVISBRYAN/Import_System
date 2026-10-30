package com.ejemplo.crudpersona.controller;

import com.ejemplo.crudpersona.model.Lote;
import com.ejemplo.crudpersona.model.Producto;
import com.ejemplo.crudpersona.repository.LoteRepository;
import com.ejemplo.crudpersona.repository.ProductoRepository;
import com.ejemplo.crudpersona.service.ERQZDatabaseDateNormalizer;
import com.ejemplo.crudpersona.service.ERQZReporteTabular;
import com.ejemplo.crudpersona.ui.ExportarMenu;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.StageStyle;
import javafx.geometry.Pos;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

@Component
public class DBQPInventarioController {

    private final ProductoRepository productoRepository;
    private final LoteRepository loteRepository;
    private final ERQZDatabaseDateNormalizer dateNormalizer;
    private BigDecimal tipoCambioActual = new BigDecimal("3.75");
    private final com.ejemplo.crudpersona.service.ERQZStockServiceI stockService;
    private final com.ejemplo.crudpersona.service.DBQPTipoCambioServiceI cambios;
    private final ERQZMainController main;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    @FXML private TextField txtBuscar;
    @FXML private ExportarMenu exportarReporte;
    @FXML private ComboBox<String> cmbFiltroEstado;
    @FXML private Label lblResumen;
    @FXML private TableView<FilaInventario> tablaInventario;
    @FXML private TableColumn<FilaInventario, String> colProducto, colLote, colUbicacion, colDisponible, colCosto, colPrecio, colEstado;
    @FXML private Button btnVerDetalle;
    @FXML private Button btnEditarProducto;
    @FXML private Button btnAjustarStock;
    @FXML private Button btnAgregarLote;

    private Lote loteActual;

    private final ObservableList<Producto> productos = FXCollections.observableArrayList();
    private final ObservableList<FilaInventario> filas = FXCollections.observableArrayList();
    private final ObservableList<Lote> lotes = FXCollections.observableArrayList();

    public DBQPInventarioController(ProductoRepository productoRepository, LoteRepository loteRepository,
                                ERQZDatabaseDateNormalizer dateNormalizer, com.ejemplo.crudpersona.service.ERQZStockServiceI stockService,
                                com.ejemplo.crudpersona.service.DBQPTipoCambioServiceI cambios, ERQZMainController main,
                                org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this.productoRepository = productoRepository;
        this.loteRepository = loteRepository;
        this.dateNormalizer = dateNormalizer;
        this.stockService = stockService; this.cambios = cambios;
        this.main = main;
        this.jdbc = jdbc;
    }

    @FXML
    public void initialize() {
        exportarReporte.setReporte(this::reporteInventario);
        dateNormalizer.normalize();
        tipoCambioActual = cambios.actual();
        cmbFiltroEstado.setItems(FXCollections.observableArrayList("Todos", "Disponible", "Stock bajo", "Sin stock"));
        cmbFiltroEstado.setValue("Todos");

        celdaTexto(colProducto, f -> f.producto().getDescripcion(), false);
        celdaTexto(colLote, f -> f.lote() == null ? "-" : f.lote().getCodigoLote(), true);
        celdaTexto(colUbicacion, f -> f.lote() == null || texto(f.lote().getUbicacion()).isBlank() ? "-" : f.lote().getUbicacion(), true);
        celdaTexto(colDisponible, f -> String.valueOf(cantidad(f)), true);
        celdaTexto(colCosto, f -> String.format(Locale.ROOT, "%.2f", f.lote() == null ? BigDecimal.ZERO : decimal(f.lote().getCostoUnitarioUsd())), true);
        celdaTexto(colPrecio, f -> String.format(Locale.ROOT, "%.2f", decimal(f.producto().getPrecioVentaPen())), true);
        colDisponible.setComparator(Comparator.comparingInt(Integer::parseInt));
        colCosto.setComparator(Comparator.comparing(BigDecimal::new));
        colPrecio.setComparator(Comparator.comparing(BigDecimal::new));
        tablaInventario.widthProperty().addListener((obs, old, ancho) -> {
            tablaInventario.getStyleClass().remove("inventory-table-compact");
            if (ancho.doubleValue() < 650) tablaInventario.getStyleClass().add("inventory-table-compact");
        });
        colEstado.setCellValueFactory(data -> new SimpleStringProperty(estadoFila(data.getValue())));
        colEstado.setCellFactory(column -> new TableCell<>() {
            private final Label badge = new Label();
            { setAlignment(Pos.CENTER_LEFT); }
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty); setText(null);
                if (empty || value == null) { setGraphic(null); return; }
                badge.setText(value); badge.getStyleClass().setAll("inventory-status", claseEstado(value));
                setGraphic(badge);
            }
        });
        for (var column : tablaInventario.getColumns()) {
            Label encabezado = new Label(column.getText()); encabezado.setWrapText(true); encabezado.setMinWidth(0);
            encabezado.getStyleClass().add("inventory-column-heading");
            column.setText(null); column.setGraphic(encabezado);
        }

        tablaInventario.setItems(filas);
        tablaInventario.getSelectionModel().selectedItemProperty().addListener((obs, old, fila) -> {
            loteActual = fila == null ? null : fila.lote(); cargarDetalle(fila == null ? null : fila.producto());
        });
        var sinSeleccion = tablaInventario.getSelectionModel().selectedItemProperty().isNull();
        for (Button button : List.of(btnEditarProducto, btnAjustarStock, btnAgregarLote, btnVerDetalle)) button.disableProperty().bind(sinSeleccion);

        cargarProductos();
    }

    private void celdaTexto(TableColumn<FilaInventario, String> columna, Function<FilaInventario, String> valor, boolean numerica) {
        columna.setCellValueFactory(data -> new SimpleStringProperty(valor.apply(data.getValue())));
        columna.setCellFactory(column -> new TableCell<>() {
            private final Label titulo = new Label();
            {
                titulo.setWrapText(!numerica); titulo.setMinWidth(0);
                titulo.setMaxWidth(Double.MAX_VALUE);
                titulo.getStyleClass().add(numerica ? "inventory-cell-number" : "inventory-cell-name");
                titulo.prefWidthProperty().bind(column.widthProperty().subtract(16).map(n -> Math.max(0, n.doubleValue())));
            }
            @Override protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty); setText(null);
                if (empty || value == null) { setGraphic(null); setTooltip(null); return; }
                titulo.setText(value); setGraphic(titulo);
                var fila = getTableRow() == null ? null : getTableRow().getItem();
                setTooltip(new Tooltip(value + (columna == colProducto && fila != null
                        ? "\nSKU: " + fila.producto().getSku() + "\nCódigo: " + texto(fila.producto().getCodigoBarras()) : "")));
            }
        });
    }

    private int cantidad(FilaInventario fila) { return fila.lote() == null ? entero(fila.producto().getStockActual()) : entero(fila.lote().getCantidadDisponible()); }
    private String estadoFila(FilaInventario fila) {
        return cantidad(fila) == 0 ? "Sin stock" : cantidad(fila) <= entero(fila.producto().getStockMinimo()) ? "Stock bajo" : "Disponible";
    }

    @FXML public void onVerGrafico() {
        main.onNavReporteStock();
    }

    private String claseEstado(String value) {
        return "Sin stock".equals(value) ? "estado-empty" : "Stock bajo".equals(value) ? "estado-low" : "estado-ok";
    }

    @FXML public void onVerDetalle() {
        Producto producto = seleccionado(); if (producto == null) return;
        Dialog<Void> dialog = new Dialog<>(); dialog.setTitle("Detalle del producto");
        dialog.initOwner(tablaInventario.getScene().getWindow()); dialog.getDialogPane().getStyleClass().add("app-dialog");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        Label nombre = new Label(producto.getDescripcion()); nombre.setWrapText(true); nombre.getStyleClass().add("modal-title");
        Label datos = new Label("SKU: " + texto(producto.getSku()) + "\nCódigo: " + texto(producto.getCodigoBarras())
                + "\nStock: " + entero(producto.getStockActual()) + " · Mínimo: " + entero(producto.getStockMinimo())
                + "\nPrecio: " + moneda(producto.getPrecioVentaPen()) + " · Costo: US$ " + costoUsd(producto));
        datos.setWrapText(true); datos.getStyleClass().add("text-subtle");
        TableView<Lote> detalleLotes = new TableView<>(lotes);
        detalleLotes.getStyleClass().addAll("tabla", "inventory-lots-table", "fit-columns"); detalleLotes.setPrefHeight(200);
        TableColumn<Lote, String> codigo = new TableColumn<>("Lote"), cantidad = new TableColumn<>("Cantidad"), fecha = new TableColumn<>("Ingreso");
        codigo.setCellValueFactory(d -> new SimpleStringProperty(texto(d.getValue().getCodigoLote())));
        cantidad.setCellValueFactory(d -> new SimpleStringProperty(String.valueOf(entero(d.getValue().getCantidadDisponible()))));
        fecha.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getFechaIngreso() == null ? "-" : d.getValue().getFechaIngreso().toString()));
        detalleLotes.getColumns().add(codigo); detalleLotes.getColumns().add(cantidad); detalleLotes.getColumns().add(fecha);
        detalleLotes.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        detalleLotes.getSelectionModel().select(loteActual);
        detalleLotes.getSelectionModel().selectedItemProperty().addListener((obs, old, lote) -> loteActual = lote);
        TableView<MovimientoStock> movimientos = new TableView<>(FXCollections.observableArrayList(jdbc.query(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbc, """
                SELECT m.fecha,m.cantidad,m.motivo,COALESCE(u.nombre_completo,'Sistema') usuario
                FROM movimientos_stock m LEFT JOIN usuarios u ON u.id=m.usuario_id
                WHERE m.producto_id=? ORDER BY m.fecha DESC,m.id DESC LIMIT 100
                """), (rs, n) -> new MovimientoStock(rs.getString("fecha"), rs.getInt("cantidad"), rs.getString("motivo"), rs.getString("usuario")), producto.getId())));
        TableColumn<MovimientoStock, String> movFecha = new TableColumn<>("Fecha"), movCantidad = new TableColumn<>("Cambio"),
                movMotivo = new TableColumn<>("Motivo"), movUsuario = new TableColumn<>("Usuario");
        movFecha.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().fecha()));
        movCantidad.setCellValueFactory(d -> new SimpleStringProperty((d.getValue().cantidad() > 0 ? "+" : "") + d.getValue().cantidad()));
        movMotivo.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().motivo()));
        movUsuario.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().usuario()));
        movimientos.getColumns().addAll(movFecha, movCantidad, movMotivo, movUsuario);
        movimientos.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        movimientos.getStyleClass().addAll("tabla", "inventory-lots-table", "fit-columns");
        movimientos.setPlaceholder(new Label("Sin ajustes registrados."));
        TabPane tabs = new TabPane(new Tab("Lotes", detalleLotes), new Tab("Movimientos", movimientos));
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE); tabs.setPrefHeight(220);
        VBox content = new VBox(14, nombre, datos, new Separator(), tabs);
        content.getStyleClass().add("custom-modal"); dialog.getDialogPane().setContent(content);
        dialog.setOnShown(event -> ((Button) dialog.getDialogPane().lookupButton(ButtonType.CLOSE)).setText("Cerrar"));
        dialog.showAndWait();
    }

    @FXML
    public void onFiltrar() {
        cargarProductos();
    }

    @FXML
    public void onLimpiar() {
        txtBuscar.clear();
        cmbFiltroEstado.setValue("Todos");
        cargarProductos();
    }

    @FXML
    public void onNuevoProducto() {
        Dialog<ProductoForm> dialog = dialogProducto("Nuevo producto", null);
        Optional<ProductoForm> resultado = dialog.showAndWait();
        resultado.ifPresent(form -> {
            if (!validarProductoForm(form, null)) return;
            Producto producto = Producto.builder()
                    .sku(form.sku())
                    .codigoBarras(form.codigo().isBlank() ? null : form.codigo())
                    .descripcion(form.descripcion())
                    .stockActual(form.stock())
                    .stockMinimo(form.minimo())
                    .precioVentaPen(form.precio())
                    .costoPromedioPen(form.costoPen())
                    .build();
            try { stockService.guardarProducto(producto, form.loteCodigo(), form.ubicacion(), form.stock(), form.costoUsd()); cargarProductos(); }
            catch (RuntimeException e) { mostrarError("No se pudo guardar el producto", e.getMessage()); }
        });
    }

    @FXML
    public void onEditarProducto() {
        Producto producto = seleccionado();
        if (producto == null) return;

        Dialog<ProductoForm> dialog = dialogProducto("Editar producto", producto);
        dialog.showAndWait().ifPresent(form -> {
            if (!validarProductoForm(form, producto.getId())) return;
            producto.setSku(form.sku());
            producto.setCodigoBarras(form.codigo().isBlank() ? null : form.codigo());
            producto.setDescripcion(form.descripcion());
            producto.setStockMinimo(form.minimo());
            producto.setPrecioVentaPen(form.precio());
            producto.setCostoPromedioPen(form.costoPen());
            try { stockService.guardarProducto(producto, form.loteCodigo(), form.ubicacion(), form.stock(), form.costoUsd()); cargarProductos(); }
            catch (RuntimeException e) { mostrarError("No se pudo guardar el producto", e.getMessage()); cargarProductos(); }
        });
    }

    @FXML
    public void onAjustarStock() {
        Producto producto = seleccionado();
        if (producto == null) return;

        Dialog<AjusteForm> dialog = dialogAjuste(producto);
        dialog.showAndWait().ifPresent(form -> {
            if (form.cantidad() == 0) {
                mostrarError("Cantidad inválida", "Ingresa una cantidad diferente de cero.");
                return;
            }
            if (form.motivo().isBlank()) {
                mostrarError("Motivo requerido", "Escribe el motivo del ajuste.");
                return;
            }
            try {
                Lote seleccionado = loteActual;
                int nuevo = stockService.ajustar(producto.getId(), seleccionado == null ? null : seleccionado.getId(), form.cantidad(), form.motivo());
                mostrarInfo("Stock ajustado", "Motivo: " + form.motivo() + "\nNuevo stock: " + nuevo);
                cargarProductos();
            } catch (RuntimeException e) { mostrarError("No se pudo ajustar el stock", e.getMessage()); }
        });
    }

    @FXML
    public void onAgregarLote() {
        Producto producto = seleccionado();
        if (producto == null) return;

        Dialog<LoteForm> dialog = dialogLote(producto);
        dialog.showAndWait().ifPresent(form -> {
            if (form.codigoLote().isBlank() || form.cantidad() <= 0) {
                mostrarError("Datos incompletos", "Ingresa código de lote y una cantidad mayor a cero.");
                return;
            }
            Lote lote = Lote.builder()
                    .producto(producto)
                    .codigoLote(form.codigoLote())
                    .numeroDua(form.numeroDua())
                    .ubicacion(form.ubicacion())
                    .cantidadInicial(form.cantidad())
                    .cantidadDisponible(form.cantidad())
                    .costoUnitarioUsd(form.costoUsd())
                    .costoUnitarioPen(form.costoPen())
                    .fechaIngreso(LocalDate.now())
                    .build();
            try { stockService.agregarLote(producto.getId(), lote); cargarProductos(); }
            catch (RuntimeException e) { mostrarError("No se pudo agregar el lote", e.getMessage()); }
        });
    }

    @FXML
    public void onReporte() {
        List<Producto> todos = productoRepository.findAll();
        long total = todos.size();
        long bajo = todos.stream().filter(p -> "Stock bajo".equals(estado(p))).count();
        long sinStock = todos.stream().filter(p -> "Sin stock".equals(estado(p))).count();
        BigDecimal valor = productoRepository.sumValorInventarioPEN();
        mostrarReporteInventario(total, bajo, sinStock, valor, todos);
    }

    public ERQZReporteTabular reporteInventario() {
        return new ERQZReporteTabular("Inventario por lote", "Filas visibles según los filtros aplicados. Cantidades por lote; no sumar stock del producto repetido.",
                List.of("Producto", "SKU", "Código de barras", "Lote", "Ubicación", "Disponible", "Costo USD", "Precio PEN", "Estado"),
                tablaInventario.getItems().stream().map(f -> ERQZReporteTabular.fila(f.producto().getDescripcion(), f.producto().getSku(),
                        f.producto().getCodigoBarras(), f.lote() == null ? "" : f.lote().getCodigoLote(),
                        f.lote() == null ? "" : f.lote().getUbicacion(), cantidad(f), f.lote() == null ? null : f.lote().getCostoUnitarioUsd(),
                        f.producto().getPrecioVentaPen(), estadoFila(f))).toList());
    }

    private void cargarProductos() {
        dateNormalizer.normalize();
        String filtro = texto(txtBuscar.getText()).toLowerCase(Locale.ROOT).trim();
        List<Producto> origen = productoRepository.findAll();
        productos.setAll(origen.stream()
                .filter(p -> filtro.isBlank() || coincide(p, filtro))
                .toList());
        String estadoFiltro = cmbFiltroEstado.getValue() == null ? "Todos" : cmbFiltroEstado.getValue();
        filas.clear();
        for (Producto producto : productos) {
            var lista = loteRepository.findByProductoIdOrderByFechaIngresoAsc(producto.getId());
            var candidatas = lista.isEmpty() ? List.of(new FilaInventario(producto, null))
                    : lista.stream().map(l -> new FilaInventario(producto, l)).toList();
            filas.addAll(candidatas.stream().filter(f -> "Todos".equals(estadoFiltro) || estadoFiltro.equals(estadoFila(f)))
                    .filter(f -> filtro.isBlank() || texto(producto.getDescripcion()).toLowerCase(Locale.ROOT).contains(filtro)
                            || texto(producto.getSku()).toLowerCase(Locale.ROOT).contains(filtro)
                            || texto(producto.getCodigoBarras()).toLowerCase(Locale.ROOT).contains(filtro)
                            || f.lote() != null && (texto(f.lote().getCodigoLote()).toLowerCase(Locale.ROOT).contains(filtro)
                                || texto(f.lote().getUbicacion()).toLowerCase(Locale.ROOT).contains(filtro))).toList());
        }
        lblResumen.setText(filas.stream().map(f -> f.producto().getId()).distinct().count() + " productos · " + filas.size() + " lotes");
        if (!filas.isEmpty()) {
            tablaInventario.getSelectionModel().selectFirst();
        } else {
            cargarDetalle(null);
        }
    }

    private boolean coincide(Producto producto, String filtro) {
        return texto(producto.getDescripcion()).toLowerCase(Locale.ROOT).contains(filtro)
                || texto(producto.getSku()).toLowerCase(Locale.ROOT).contains(filtro)
                || texto(producto.getCodigoBarras()).toLowerCase(Locale.ROOT).contains(filtro)
                || ubicacion(producto).toLowerCase(Locale.ROOT).contains(filtro)
                || loteRepository.findByProductoIdOrderByFechaIngresoAsc(producto.getId()).stream()
                        .anyMatch(l -> texto(l.getCodigoLote()).toLowerCase(Locale.ROOT).contains(filtro)
                                || texto(l.getUbicacion()).toLowerCase(Locale.ROOT).contains(filtro));
    }

    private void cargarDetalle(Producto producto) {
        if (producto == null) {
            lotes.clear();
            return;
        }
        lotes.setAll(loteRepository.findByProductoIdOrderByFechaIngresoAsc(producto.getId()));
    }

    private Dialog<ProductoForm> dialogProducto(String titulo, Producto producto) {
        Dialog<ProductoForm> dialog = new Dialog<>();
        dialog.setTitle(titulo);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        prepararDialogo(dialog, producto == null ? "Guardar en Base de Datos" : "Guardar cambios");

        TextField descripcion = new TextField(producto == null ? "" : texto(producto.getDescripcion()));
        descripcion.setPromptText("Ej: Globos látex pastel, bolsa de 50 unidades");
        TextField codigo = new TextField(producto == null ? "" : texto(producto.getCodigoBarras()));
        codigo.setPromptText("Ej: 7750001");
        TextField sku = new TextField(producto == null ? "" : texto(producto.getSku()));
        sku.setPromptText("Ej: GLO-LAT-50");
        TextField loteCodigo = new TextField(producto == null ? "L-" + (2400 + (int) (System.currentTimeMillis() % 200)) : lotePrincipal(producto));
        TextField stock = new TextField(producto == null ? "20" : String.valueOf(entero(producto.getStockActual())));
        TextField ubicacion = new TextField(producto == null ? "A-01-1" : ubicacion(producto));
        TextField costoUsd = new TextField(producto == null ? "5" : costoUsd(producto));
        TextField gastosUsd = new TextField(producto == null ? "0.9" : "0");
        BigDecimal margenActual = producto == null || decimal(producto.getCostoPromedioPen()).signum() == 0 ? new BigDecimal("35")
                : decimal(producto.getPrecioVentaPen()).divide(producto.getCostoPromedioPen(), 8, RoundingMode.HALF_UP).subtract(BigDecimal.ONE).multiply(new BigDecimal("100"));
        TextField margen = new TextField(margenActual.stripTrailingZeros().toPlainString());
        String costoOriginal = costoUsd.getText(), gastosOriginal = gastosUsd.getText(), margenOriginal = margen.getText();
        TextField minimo = new TextField(producto == null ? "5" : String.valueOf(entero(producto.getStockMinimo())));
        aplicarEstiloCampo(descripcion, codigo, sku, loteCodigo, stock, ubicacion, costoUsd, gastosUsd, margen, minimo);
        stock.setDisable(producto != null);

        Label lblCostoTotal = new Label();
        Label lblPrecioVenta = new Label();
        lblCostoTotal.getStyleClass().add("modal-money");
        lblPrecioVenta.getStyleClass().addAll("modal-money", "modal-money-primary");
        Runnable recalcular = () -> {
            BigDecimal landed = decimalDesdeTexto(costoUsd.getText()).add(decimalDesdeTexto(gastosUsd.getText()));
            BigDecimal precioPen = landed.multiply(tipoCambioActual)
                    .multiply(BigDecimal.ONE.add(decimalDesdeTexto(margen.getText()).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)))
                    .setScale(2, RoundingMode.HALF_UP);
            lblCostoTotal.setText("US$ " + landed.setScale(3, RoundingMode.HALF_UP));
            lblPrecioVenta.setText("S/ " + precioPen);
        };
        costoUsd.textProperty().addListener((obs, a, b) -> recalcular.run());
        gastosUsd.textProperty().addListener((obs, a, b) -> recalcular.run());
        margen.textProperty().addListener((obs, a, b) -> recalcular.run());
        recalcular.run();

        VBox root = new VBox(16);
        root.getStyleClass().add("custom-modal");
        root.getChildren().add(cabeceraModal(producto == null ? "Registrar Nuevo Producto & Lote Inicial" : "Editar Producto & Datos de Stock", dialog));
        root.getChildren().add(new Separator());

        VBox nombreBox = new VBox(6, etiquetaDialogo("Nombre del Producto *"), descripcion);
        HBox codigos = new HBox(14,
                campoVertical("Código de Barras (EAN / Lector)", codigo),
                campoVertical("Código SKU *", sku));
        VBox.setVgrow(descripcion, Priority.NEVER);

        VBox loteBox = new VBox(12);
        loteBox.getStyleClass().add("modal-section");
        Label loteTitulo = new Label("DATOS DEL LOTE DE INGRESO (FIFO)");
        loteTitulo.setStyle("-fx-font-weight: bold;");
        HBox fila1 = new HBox(10,
                campoVertical("N° Lote", loteCodigo),
                campoVertical("Cantidad (Stock)", stock),
                campoVertical("Ubicación", ubicacion));
        HBox fila2 = new HBox(10,
                campoVertical("Costo FOB (USD)", costoUsd),
                campoVertical("Gastos DUA (USD)", gastosUsd),
                campoVertical("Margen (%)", margen));
        HBox resumen = new HBox(20);
        resumen.getStyleClass().add("modal-summary");
        VBox costoBox = new VBox(2, new Label("Costo Landed Total"), lblCostoTotal);
        VBox precioBox = new VBox(2, new Label("Precio de Venta en Soles (PEN)"), lblPrecioVenta);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        resumen.getChildren().addAll(costoBox, spacer, precioBox);
        loteBox.getChildren().addAll(loteTitulo, fila1, fila2, campoVertical("Stock mínimo", minimo), resumen);

        root.getChildren().addAll(nombreBox, codigos, loteBox);
        Label validacion = new Label();
        validacion.setId("productoValidacion"); validacion.setWrapText(true);
        validacion.getStyleClass().add("validation-error");
        validacion.setVisible(false); validacion.setManaged(false);
        descripcion.setId("nombreProducto"); sku.setId("codigoSku");
        root.getChildren().add(validacion);
        dialog.getDialogPane().setContent(root);

        ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String error = null;
            descripcion.getStyleClass().remove("campo-error"); sku.getStyleClass().remove("campo-error");
            if (descripcion.getText().isBlank() || sku.getText().isBlank()) {
                error = "Completa el nombre y el SKU del producto.";
                if (descripcion.getText().isBlank()) descripcion.getStyleClass().add("campo-error");
                if (sku.getText().isBlank()) sku.getStyleClass().add("campo-error");
            } else {
                Long id = producto == null ? null : producto.getId();
                if (productoRepository.findBySkuIgnoreCase(sku.getText().trim()).filter(p -> !p.getId().equals(id)).isPresent()) error = "Ya existe un producto con ese SKU.";
                if (!codigo.getText().isBlank() && productoRepository.findByCodigoBarras(codigo.getText().trim()).filter(p -> !p.getId().equals(id)).isPresent()) error = "El codigo de barras ya esta registrado.";
                try {
                    if (Integer.parseInt(stock.getText().trim()) < 0 || Integer.parseInt(minimo.getText().trim()) < 0 || new BigDecimal(costoUsd.getText().trim()).signum() < 0
                            || new BigDecimal(gastosUsd.getText().trim()).signum() < 0 || new BigDecimal(margen.getText().trim()).signum() < 0)
                        error = "Las cantidades y costos no pueden ser negativos.";
                } catch (NumberFormatException e) { error = "Revisa los numeros de cantidad, costos y margen."; }
            }
            if (error != null) {
                validacion.setText(error); validacion.setVisible(true); validacion.setManaged(true); event.consume();
                (descripcion.getText().isBlank() ? descripcion : sku).requestFocus();
            }
        });

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) return null;
            BigDecimal landed = decimalDesdeTexto(costoUsd.getText()).add(decimalDesdeTexto(gastosUsd.getText()));
            BigDecimal precio = landed.multiply(tipoCambioActual)
                    .multiply(BigDecimal.ONE.add(decimalDesdeTexto(margen.getText()).divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)))
                    .setScale(2, RoundingMode.HALF_UP);
            boolean conservar = producto != null && costoOriginal.equals(costoUsd.getText()) && gastosOriginal.equals(gastosUsd.getText()) && margenOriginal.equals(margen.getText());
            return new ProductoForm(
                    sku.getText().trim(),
                    codigo.getText().trim(),
                    descripcion.getText().trim(),
                    enteroDesdeTexto(stock.getText(), 0),
                    enteroDesdeTexto(minimo.getText(), 5),
                    conservar ? producto.getPrecioVentaPen() : precio,
                    conservar ? producto.getCostoPromedioPen() : landed.multiply(tipoCambioActual).setScale(2, RoundingMode.HALF_UP),
                    landed,
                    loteCodigo.getText().trim(),
                    ubicacion.getText().trim()
            );
        });
        return dialog;
    }

    private Dialog<LoteForm> dialogLote(Producto producto) {
        Dialog<LoteForm> dialog = new Dialog<>();
        dialog.setTitle("Agregar lote");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        prepararDialogo(dialog, "Guardar lote");

        TextField codigo = new TextField("L-" + System.currentTimeMillis() % 10000);
        TextField dua = new TextField();
        TextField ubicacion = new TextField(ubicacion(producto));
        TextField cantidad = new TextField("1");
        TextField costoUsd = new TextField("0.00");
        TextField costoPen = new TextField(decimal(producto.getCostoPromedioPen()).toPlainString());
        aplicarEstiloCampo(codigo, dua, ubicacion, cantidad, costoUsd, costoPen);

        VBox root = new VBox(16);
        root.getStyleClass().add("custom-modal");
        root.getChildren().add(cabeceraModal("Agregar lote FIFO", dialog));
        root.getChildren().add(new Separator());
        VBox loteBox = new VBox(12);
        loteBox.getStyleClass().add("modal-section");
        Label productoLabel = new Label(producto.getDescripcion());
        productoLabel.setStyle("-fx-font-weight: bold;");
        HBox fila1 = new HBox(10, campoVertical("Código lote", codigo), campoVertical("DUA", dua), campoVertical("Ubicación", ubicacion));
        HBox fila2 = new HBox(10, campoVertical("Cantidad", cantidad), campoVertical("Costo USD", costoUsd), campoVertical("Costo PEN", costoPen));
        loteBox.getChildren().addAll(productoLabel, fila1, fila2);
        root.getChildren().add(loteBox);
        dialog.getDialogPane().setContent(root);
        validarDialogo(dialog, root, () -> {
            int unidades = Integer.parseInt(cantidad.getText().trim());
            BigDecimal usd = new BigDecimal(costoUsd.getText().trim().replace(',', '.'));
            BigDecimal pen = new BigDecimal(costoPen.getText().trim().replace(',', '.'));
            return codigo.getText().isBlank() || unidades <= 0 || usd.signum() < 0 || pen.signum() < 0
                    ? "Revisa código, cantidad mayor que cero y costos no negativos." : null;
        });

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) return null;
            return new LoteForm(codigo.getText().trim(), dua.getText().trim(), ubicacion.getText().trim(), enteroDesdeTexto(cantidad.getText(), 1),
                    decimalDesdeTexto(costoUsd.getText()), decimalDesdeTexto(costoPen.getText()));
        });
        return dialog;
    }

    private void prepararDialogo(Dialog<?> dialog, String textoPrimario) {
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.getDialogPane().getStyleClass().add("app-dialog");
        dialog.getDialogPane().setMinWidth(640);
        dialog.getDialogPane().setPrefWidth(640);
        dialog.getDialogPane().setMaxWidth(640);
        dialog.setResizable(false);
        dialog.setOnShown(event -> {
            Button aceptar = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
            Button cancelar = (Button) dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
            if (aceptar != null) {
                aceptar.setText(textoPrimario);
                aceptar.getStyleClass().add("btn-primario");
            }
            if (cancelar != null) {
                cancelar.setText("Cancelar");
                cancelar.getStyleClass().add("btn-secundario");
            }
            Button cerrar = (Button) dialog.getDialogPane().lookupButton(ButtonType.CLOSE);
            if (cerrar != null) {
                cerrar.setText(textoPrimario);
                cerrar.getStyleClass().add("btn-primario");
            }
        });
    }

    private HBox cabeceraModal(String titulo, Dialog<?> dialog) {
        FontIcon icon = new FontIcon("fth-package");
        icon.setIconSize(22);
        icon.getStyleClass().add("color-acento");
        Label title = new Label(titulo);
        title.getStyleClass().add("modal-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button cerrar = new Button("×");
        cerrar.getStyleClass().add("modal-close");
        cerrar.setOnAction(event -> {
            dialog.setResult(null);
            dialog.close();
        });
        HBox header = new HBox(12, icon, title, spacer, cerrar);
        header.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        return header;
    }

    private VBox campoVertical(String label, TextField campo) {
        Label etiqueta = etiquetaDialogo(label);
        VBox box = new VBox(6, etiqueta, campo);
        box.setMinWidth(0);
        HBox.setHgrow(box, Priority.ALWAYS);
        campo.setMinWidth(0);
        campo.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private Label etiquetaDialogo(String texto) {
        Label label = new Label(texto);
        label.getStyleClass().add("dialog-form-label");
        label.setMinWidth(120);
        return label;
    }

    private void aplicarEstiloCampo(TextField... campos) {
        for (TextField campo : campos) {
            campo.getStyleClass().add("dialog-form-field");
        }
    }

    private Dialog<AjusteForm> dialogAjuste(Producto producto) {
        Dialog<AjusteForm> dialog = new Dialog<>();
        dialog.setTitle("Ajustar stock");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        prepararDialogo(dialog, "Aplicar ajuste");

        TextField cantidad = new TextField("0");
        cantidad.setPromptText("Ej: 5 o -2");
        TextField motivo = new TextField();
        motivo.setPromptText("Ej: conteo físico, merma, corrección");
        aplicarEstiloCampo(cantidad, motivo);

        VBox root = new VBox(16);
        root.getStyleClass().add("custom-modal");
        root.getChildren().add(cabeceraModal("Ajustar stock", dialog));
        root.getChildren().add(new Separator());

        VBox box = new VBox(12);
        box.getStyleClass().add("modal-section");
        Label productoLabel = new Label(producto.getDescripcion());
        productoLabel.setStyle("-fx-font-weight: bold;");
        Label actual = new Label("Stock actual: " + entero(producto.getStockActual()) + " unidades");
        actual.getStyleClass().add("text-subtle");
        HBox fila = new HBox(10, campoVertical("Cantidad a sumar/restar", cantidad), campoVertical("Motivo", motivo));
        box.getChildren().addAll(productoLabel, actual, fila);
        root.getChildren().add(box);
        dialog.getDialogPane().setContent(root);
        validarDialogo(dialog, root, () -> {
            int ajuste = Integer.parseInt(cantidad.getText().trim());
            return ajuste == 0 || motivo.getText().isBlank() ? "Ingresa una cantidad diferente de cero y el motivo del ajuste." : null;
        });

        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) return null;
            return new AjusteForm(enteroDesdeTexto(cantidad.getText(), 0), motivo.getText().trim());
        });
        return dialog;
    }

    private void validarDialogo(Dialog<?> dialog, VBox contenido, java.util.function.Supplier<String> comprobar) {
        Label error = new Label(); error.setWrapText(true); error.getStyleClass().add("validation-error");
        error.setVisible(false); error.setManaged(false); contenido.getChildren().add(error);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            String mensaje;
            try { mensaje = comprobar.get(); } catch (NumberFormatException e) { mensaje = "Revisa las cantidades y los importes numéricos."; }
            if (mensaje != null) { error.setText(mensaje); error.setVisible(true); error.setManaged(true); event.consume(); }
        });
    }

    private boolean validarProductoForm(ProductoForm form, Long productoActualId) {
        if (form.descripcion().isBlank() || form.sku().isBlank()) {
            mostrarError("Datos incompletos", "Ingresa nombre del producto y SKU.");
            return false;
        }
        if (form.stock() < 0 || form.minimo() < 0) {
            mostrarError("Stock inválido", "El stock y el mínimo no pueden ser negativos.");
            return false;
        }
        Optional<Producto> skuExistente = productoRepository.findBySku(form.sku());
        if (skuExistente.isPresent() && !skuExistente.get().getId().equals(productoActualId)) {
            mostrarError("SKU duplicado", "Ya existe un producto con ese SKU.");
            return false;
        }
        if (!form.codigo().isBlank()) {
            Optional<Producto> codigoExistente = productoRepository.findByCodigoBarras(form.codigo());
            if (codigoExistente.isPresent() && !codigoExistente.get().getId().equals(productoActualId)) {
                mostrarError("Código duplicado", "Ya existe un producto con ese código de barras.");
                return false;
            }
        }
        return true;
    }

    private void mostrarReporteInventario(long total, long bajo, long sinStock, BigDecimal valor, List<Producto> todos) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Reporte de inventario");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        prepararDialogo(dialog, "Cerrar");

        VBox root = new VBox(16);
        root.getStyleClass().add("custom-modal");
        root.getChildren().add(cabeceraModal("Reporte de inventario", dialog));
        root.getChildren().add(new Separator());

        HBox metricas = new HBox(10,
                miniMetrica("Productos", String.valueOf(total)),
                miniMetrica("Stock bajo", String.valueOf(bajo)),
                miniMetrica("Sin stock", String.valueOf(sinStock)),
                miniMetrica("Valor PEN", moneda(valor)));

        TextArea resumen = new TextArea(reporteTexto(todos));
        resumen.setEditable(false);
        resumen.setWrapText(true);
        resumen.setPrefHeight(220);
        resumen.getStyleClass().add("report-area");

        root.getChildren().addAll(metricas, resumen);
        dialog.getDialogPane().setContent(root);
        dialog.showAndWait();
    }

    private VBox miniMetrica(String titulo, String valor) {
        Label label = new Label(titulo);
        label.getStyleClass().add("text-subtle");
        Label numero = new Label(valor);
        numero.getStyleClass().add("title-5");
        VBox box = new VBox(4, label, numero);
        box.getStyleClass().add("modal-summary");
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    private String reporteTexto(List<Producto> todos) {
        StringBuilder builder = new StringBuilder();
        builder.append("Productos que requieren atención\n\n");
        todos.stream()
                .filter(p -> !"Disponible".equals(estado(p)))
                .sorted(Comparator.comparing(this::estado).thenComparing(p -> texto(p.getDescripcion())))
                .forEach(p -> builder.append("- ")
                        .append(p.getDescripcion())
                        .append(" | Stock: ")
                        .append(entero(p.getStockActual()))
                        .append(" | Mínimo: ")
                        .append(entero(p.getStockMinimo()))
                        .append(" | Estado: ")
                        .append(estado(p))
                        .append("\n"));
        if (builder.toString().endsWith("\n\n")) {
            builder.append("No hay productos críticos por ahora.\n");
        }
        return builder.toString();
    }

    private Producto seleccionado() {
        var fila = tablaInventario.getSelectionModel().getSelectedItem();
        Producto producto = fila == null ? null : fila.producto();
        if (producto == null) {
            mostrarError("Selecciona un producto", "Primero selecciona un producto de la tabla.");
        }
        return producto;
    }

    private String estado(Producto producto) {
        int stock = entero(producto.getStockActual());
        int minimo = entero(producto.getStockMinimo());
        if (stock == 0) return "Sin stock";
        if (stock <= minimo) return "Stock bajo";
        return "Disponible";
    }

    private String lotePrincipal(Producto producto) {
        return loteRepository.findByProductoIdOrderByFechaIngresoAsc(producto.getId()).stream()
                .findFirst()
                .map(Lote::getCodigoLote)
                .orElse("L-0000");
    }

    private String ubicacion(Producto producto) {
        return loteRepository.findByProductoIdOrderByFechaIngresoAsc(producto.getId()).stream()
                .map(Lote::getUbicacion)
                .filter(valor -> valor != null && !valor.isBlank())
                .findFirst()
                .orElse("A-01-1");
    }

    private String costoUsd(Producto producto) {
        return decimal(producto.getCostoPromedioPen()).divide(tipoCambioActual, 2, RoundingMode.HALF_UP).toPlainString();
    }

    private String texto(String valor) {
        return valor == null || valor.isBlank() ? "" : valor;
    }

    private String moneda(BigDecimal valor) {
        return String.format(Locale.ROOT, "S/ %.2f", decimal(valor));
    }

    private BigDecimal decimal(BigDecimal valor) {
        return valor == null ? BigDecimal.ZERO : valor;
    }

    private BigDecimal decimalDesdeTexto(String valor) {
        try {
            return new BigDecimal(valor.trim().replace(',', '.'));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private Integer enteroDesdeTexto(String valor) {
        try {
            return Integer.parseInt(valor.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private int enteroDesdeTexto(String valor, int fallback) {
        Integer entero = enteroDesdeTexto(valor);
        return entero == null ? fallback : entero;
    }

    private int entero(Integer valor) {
        return valor == null ? 0 : valor;
    }

    private void mostrarError(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.ERROR, mensaje);
        alert.setHeaderText(titulo);
        alert.showAndWait();
    }

    private void mostrarInfo(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, mensaje);
        alert.setHeaderText(titulo);
        alert.showAndWait();
    }

    public record ProductoForm(String sku, String codigo, String descripcion, int stock, int minimo,
                               BigDecimal precio, BigDecimal costoPen, BigDecimal costoUsd, String loteCodigo,
                               String ubicacion) {
    }

    public record LoteForm(String codigoLote, String numeroDua, String ubicacion, int cantidad,
                           BigDecimal costoUsd, BigDecimal costoPen) {
    }

    public record AjusteForm(int cantidad, String motivo) {
    }
    public record FilaInventario(Producto producto, Lote lote) {}
    public record MovimientoStock(String fecha, int cantidad, String motivo, String usuario) {}
}
