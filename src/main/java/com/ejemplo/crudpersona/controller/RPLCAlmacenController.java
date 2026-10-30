package com.ejemplo.crudpersona.controller;

import com.ejemplo.crudpersona.model.Lote;
import com.ejemplo.crudpersona.model.Producto;
import com.ejemplo.crudpersona.model.UbicacionAlmacen;
import com.ejemplo.crudpersona.repository.LoteRepository;
import com.ejemplo.crudpersona.repository.ProductoRepository;
import com.ejemplo.crudpersona.repository.UbicacionAlmacenRepository;
import com.ejemplo.crudpersona.service.ERQZDatabaseDateNormalizer;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.StageStyle;
import javafx.util.StringConverter;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class RPLCAlmacenController {

    private final LoteRepository loteRepository;
    private final ProductoRepository productoRepository;
    private final UbicacionAlmacenRepository ubicacionAlmacenRepository;
    private final ERQZDatabaseDateNormalizer dateNormalizer;
    private final com.ejemplo.crudpersona.service.ERQZStockServiceI stockService;

    @FXML private HBox boxPasillos;
    @FXML private Label lblNiveles;
    @FXML private Label lblSeleccion;
    @FXML private Label lblTotalUbicaciones;
    @FXML private Label lblOcupadas;
    @FXML private Label lblLibres;
    @FXML private Label lblCriticas;
    @FXML private TextField txtBuscarUbicacion;
    @FXML private ComboBox<String> cmbFiltroMapa;
    @FXML private Label lblDetalleUbicacion;
    @FXML private Label lblDetalleProducto;
    @FXML private Label lblDetalleLote;
    @FXML private Label lblDetalleStock;
    @FXML private Label lblDetalleEstado;
    @FXML private TextField txtEditLote;
    @FXML private TextField txtEditCantidad;
    @FXML private TextField txtEditUbicacion;
    @FXML private TableView<LoteUbicacionItem> tablaHistorialLotes;
    @FXML private TableColumn<LoteUbicacionItem, String> colHistProducto;
    @FXML private TableColumn<LoteUbicacionItem, String> colHistLote;
    @FXML private TableColumn<LoteUbicacionItem, String> colHistCantidad;
    @FXML private TableColumn<LoteUbicacionItem, String> colHistInicial;
    @FXML private Button btnMover;
    @FXML private Button btnEditarCantidad;
    @FXML private Button btnVaciar;
    @FXML private Button btnAsignar;
    @FXML private Button btnGuardarEdicion;

    private final List<String> pasillos = new ArrayList<>(List.of("A", "B", "C"));
    private int anaqueles = 4;
    private final Map<String, Integer> anaquelesPorPasillo = new HashMap<>();
    private int niveles = 3;
    private String ubicacionSeleccionada;
    private Map<String, List<Lote>> lotesPorUbicacion = new HashMap<>();
    private final ObservableList<LoteUbicacionItem> historialLotes = FXCollections.observableArrayList();

    public RPLCAlmacenController(LoteRepository loteRepository, ProductoRepository productoRepository,
                             UbicacionAlmacenRepository ubicacionAlmacenRepository,
                             ERQZDatabaseDateNormalizer dateNormalizer, com.ejemplo.crudpersona.service.ERQZStockServiceI stockService) {
        this.loteRepository = loteRepository;
        this.productoRepository = productoRepository;
        this.ubicacionAlmacenRepository = ubicacionAlmacenRepository;
        this.dateNormalizer = dateNormalizer;
        this.stockService = stockService;
    }

    @FXML
    public void initialize() {
        dateNormalizer.normalize();
        cmbFiltroMapa.setItems(javafx.collections.FXCollections.observableArrayList("Todos", "Con stock", "Stock bajo", "Sin stock", "Libre"));
        cmbFiltroMapa.setValue("Todos");
        colHistProducto.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().producto()));
        colHistLote.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().lote()));
        colHistCantidad.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().cantidad()));
        colHistInicial.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().inicial()));
        tablaHistorialLotes.setItems(historialLotes);
        tablaHistorialLotes.getSelectionModel().selectedItemProperty().addListener((obs, old, item) -> cargarLoteEnEditor(item == null ? null : item.loteEntidad()));
        cargarMapa();
    }

    @FXML
    public void onFiltrarMapa() {
        cargarMapa();
    }

    @FXML
    public void onBuscarUbicacion() {
        String filtro = filtroBusqueda();
        if (filtro.isBlank()) {
            cargarMapa();
            return;
        }
        Optional<String> encontrada = ubicacionesVisibles().stream()
                .filter(ubicacion -> coincideBusqueda(ubicacion, lotesPorUbicacion.getOrDefault(ubicacion, List.of()), filtro))
                .findFirst();
        if (encontrada.isPresent()) {
            ubicacionSeleccionada = encontrada.get();
            cargarMapa();
        } else {
            mostrarInfo("Sin resultados", "No encontré una ubicación, producto o lote con ese texto.");
        }
    }

    @FXML
    public void onLimpiarBusqueda() {
        txtBuscarUbicacion.clear();
        cmbFiltroMapa.setValue("Todos");
        cargarMapa();
    }

    @FXML
    public void onAgregarPasillo() {
        String siguiente = java.util.stream.IntStream.rangeClosed('A', 'Z').mapToObj(c -> String.valueOf((char)c))
                .filter(p -> !pasillos.contains(p)).findFirst().orElse(null);
        if (siguiente == null) { mostrarInfo("Límite de pasillos", "Ya están registrados los pasillos A a Z."); return; }
        pasillos.add(siguiente); anaquelesPorPasillo.put(siguiente, 4);
        guardarUbicacionesPasillo(siguiente);
        cargarMapa();
    }

    @FXML
    public void onNivelMenos() {
        if (niveles > 1) {
            var quitar = ubicacionAlmacenRepository.findByActivoTrueOrderByPasilloAscAnaquelAscNivelAsc().stream()
                    .filter(u -> u.getNivel() == niveles).toList();
            if (!retirarUbicaciones(quitar)) return;
            niveles--;
            cargarMapa();
        }
    }

    @FXML
    public void onNivelMas() {
        niveles++;
        guardarUbicacionesVisibles();
        cargarMapa();
    }

    @FXML
    public void onAsignar() {
        if (ubicacionSeleccionada == null) return;
        List<Lote> disponibles = loteRepository.findAllWithProductoOrderByUbicacion().stream()
                .filter(lote -> entero(lote.getCantidadDisponible()) > 0)
                .toList();
        if (disponibles.isEmpty()) {
            mostrarInfo("Sin lotes disponibles", "No hay lotes con stock para asignar.");
            return;
        }

        Dialog<Lote> dialog = dialogAsignarLote(disponibles);
        dialog.showAndWait().ifPresent(lote -> {
            try { stockService.moverLote(lote.getId(), ubicacionSeleccionada); }
            catch (RuntimeException e) { mostrarError("No se pudo asignar", e.getMessage()); return; }
            cargarMapa();
        });
    }

    @FXML
    public void onMover() {
        Lote lote = elegirLoteSeleccionado("Mover mercadería");
        if (lote == null) return;

        Dialog<String> dialog = dialogMoverLote(lote);
        dialog.showAndWait().ifPresent(destino -> {
            String normalizada = normalizarUbicacion(destino);
            if (normalizada == null) {
                mostrarError("Ubicación inválida", "Usa el formato A-01-1.");
                return;
            }
            try { stockService.moverLote(lote.getId(), normalizada); }
            catch (RuntimeException e) { mostrarError("No se pudo mover", e.getMessage()); return; }
            asegurarUbicacionVisible(normalizada);
            ubicacionSeleccionada = normalizada;
            cargarMapa();
        });
    }

    @FXML
    public void onEditarCantidad() {
        Lote lote = elegirLoteSeleccionado("Cambiar cantidad");
        if (lote == null) return;

        Dialog<Integer> dialog = dialogCambiarCantidad(lote);
        dialog.showAndWait().ifPresent(valor -> {
            try { stockService.editarLote(lote.getId(), entero(lote.getCantidadDisponible()), valor, lote.getUbicacion()); }
            catch (RuntimeException e) { mostrarError("No se pudo cambiar la cantidad", e.getMessage()); cargarMapa(); return; }
            ubicacionSeleccionada = normalizarUbicacion(texto(lote.getUbicacion()));
            cargarMapa();
            mostrarInfo("Cantidad actualizada", "Ahora " + lote.getProducto().getDescripcion()
                    + " tiene " + valor + " unidades disponibles en " + ubicacionSeleccionada + ".");
        });
    }

    @FXML
    public void onGuardarEdicion() {
        Lote lote = loteActivoEnEditor();
        if (lote == null) {
            mostrarInfo("Sin lote seleccionado", "Selecciona una celda ocupada o un lote del historial.");
            return;
        }

        Integer nuevaCantidad = enteroDesdeTexto(txtEditCantidad.getText());
        if (nuevaCantidad == null || nuevaCantidad < 0) {
            mostrarError("Cantidad inválida", "Ingresa una cantidad mayor o igual a cero.");
            return;
        }
        if (nuevaCantidad > entero(lote.getCantidadInicial())) {
            mostrarError("Cantidad excedida", "No puede superar la cantidad inicial del lote: " + entero(lote.getCantidadInicial()) + ".");
            return;
        }

        String destino = normalizarUbicacion(txtEditUbicacion.getText());
        if (destino == null) {
            mostrarError("Ubicación inválida", "Usa el formato A-01-1.");
            return;
        }

        try { stockService.editarLote(lote.getId(), entero(lote.getCantidadDisponible()), nuevaCantidad, destino); }
        catch (RuntimeException e) { mostrarError("No se pudo guardar", e.getMessage()); cargarMapa(); return; }
        asegurarUbicacionVisible(destino);
        ubicacionSeleccionada = destino;
        cargarMapa();
        mostrarInfo("Ubicación actualizada", "Se guardó " + lote.getProducto().getDescripcion() + " en " + destino + ".");
    }

    @FXML
    public void onVaciar() {
        List<Lote> lotes = lotesSeleccionados();
        if (lotes.isEmpty()) return;

        if (!confirmarVaciar(lotes)) {
            return;
        }

        try { stockService.vaciar(lotes.stream().collect(Collectors.toMap(Lote::getId, Lote::getCantidadDisponible))); }
        catch (RuntimeException e) { mostrarError("No se pudo vaciar", e.getMessage()); }
        cargarMapa();
    }

    private void cargarMapa() {
        dateNormalizer.normalize();
        List<Lote> lotes = loteRepository.findAllWithProductoOrderByUbicacion();
        lotes.forEach(lote -> asegurarUbicacionVisible(texto(lote.getUbicacion())));
        cargarEstructuraDesdeBase();
        guardarUbicacionesVisibles();
        lotesPorUbicacion = lotes.stream()
                .filter(lote -> !texto(lote.getUbicacion()).isBlank())
                .collect(Collectors.groupingBy(lote -> normalizarUbicacion(lote.getUbicacion()) == null ? lote.getUbicacion() : normalizarUbicacion(lote.getUbicacion())));

        lblNiveles.setText(String.valueOf(niveles));
        boxPasillos.getChildren().clear();
        actualizarMetricas();
        for (String pasillo : pasillos) {
            boxPasillos.getChildren().add(crearPasillo(pasillo));
        }
        seleccionarUbicacion(ubicacionSeleccionada);
    }

    private VBox crearPasillo(String pasillo) {
        VBox contenedor = new VBox(14);
        HBox.setHgrow(contenedor, Priority.ALWAYS);

        HBox header = new HBox(8);
        header.setAlignment(Pos.CENTER_LEFT);
        Label titulo = new Label("Pasillo " + pasillo);
        titulo.setStyle("-fx-font-weight: bold;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button agregar = botonIcono("fth-plus");
        agregar.setTooltip(new Tooltip("Agregar estante en pasillo " + pasillo));
        agregar.setOnAction(event -> {
            anaquelesPorPasillo.put(pasillo, anaquelesPorPasillo.getOrDefault(pasillo, 4) + 1);
            guardarUbicacionesPasillo(pasillo);
            cargarMapa();
        });
        Button quitar = botonIcono("fth-minus");
        quitar.setTooltip(new Tooltip("Quitar el último estante del pasillo " + pasillo));
        quitar.setOnAction(event -> {
            int ultimo = anaquelesPorPasillo.getOrDefault(pasillo, 4);
            if (ultimo > 1) {
                var retirar = ubicacionAlmacenRepository.findByActivoTrueOrderByPasilloAscAnaquelAscNivelAsc().stream()
                        .filter(u -> pasillo.equals(u.getPasillo()) && u.getAnaquel() == ultimo).toList();
                if (!retirarUbicaciones(retirar)) return;
                anaquelesPorPasillo.put(pasillo, ultimo - 1);
                cargarMapa();
            }
        });
        header.getChildren().addAll(titulo, spacer, agregar, quitar);
        contenedor.getChildren().add(header);

        for (int anaquel = 1; anaquel <= anaquelesPorPasillo.getOrDefault(pasillo, 4); anaquel++) {
            HBox fila = new HBox(10);
            fila.setAlignment(Pos.CENTER_LEFT);
            Label numero = new Label(String.format("%02d", anaquel));
            numero.getStyleClass().add("text-subtle");
            fila.getChildren().add(numero);
            for (int nivel = 1; nivel <= niveles; nivel++) {
                String ubicacion = pasillo + "-" + String.format("%02d", anaquel) + "-" + nivel;
                fila.getChildren().add(crearCelda(ubicacion));
            }
            contenedor.getChildren().add(fila);
        }
        return contenedor;
    }

    private Label crearCelda(String ubicacion) {
        List<Lote> lotes = lotesPorUbicacion.getOrDefault(ubicacion, List.of());
        int cantidad = lotes.stream().mapToInt(lote -> entero(lote.getCantidadDisponible())).sum();
        Label celda = new Label(textoCelda(ubicacion, lotes, cantidad));
        celda.getStyleClass().add("warehouse-cell");
        celda.setWrapText(true);
        celda.getStyleClass().add(estiloCelda(lotes, cantidad));
        if (ubicacion.equals(ubicacionSeleccionada)) {
            celda.getStyleClass().add("warehouse-selected");
        }
        if (!debeResaltar(ubicacion, lotes, cantidad)) {
            celda.getStyleClass().add("warehouse-muted");
        }
        celda.setOnMouseClicked(event -> seleccionarUbicacion(ubicacion));
        celda.setTooltip(new Tooltip(tooltipUbicacion(ubicacion, lotes, cantidad)));
        return celda;
    }

    private String estiloCelda(List<Lote> lotes, int cantidad) {
        if (lotes.isEmpty()) return "warehouse-free";
        if (cantidad == 0) return "warehouse-empty";
        boolean bajo = lotes.stream().anyMatch(lote -> {
            int minimo = Math.max(1, entero(lote.getProducto().getStockMinimo()));
            int stockProducto = entero(lote.getProducto().getStockActual());
            return entero(lote.getCantidadDisponible()) <= minimo || stockProducto <= minimo;
        });
        return bajo ? "warehouse-low" : "warehouse-ok";
    }

    private String textoCelda(String ubicacion, List<Lote> lotes, int cantidad) {
        if (lotes.isEmpty()) return "";
        String nombre = lotes.stream()
                .map(lote -> texto(lote.getProducto().getDescripcion()))
                .filter(texto -> !texto.isBlank())
                .findFirst()
                .orElse("Producto");
        if (lotes.stream().map(lote -> lote.getProducto().getId()).distinct().count() > 1) {
            return "Varios\n" + cantidad + " uds.";
        }
        return nombreCorto(nombre) + "\n" + cantidad + " uds.";
    }

    private String nombreCorto(String nombre) {
        if (nombre.length() <= 18) return nombre;
        return nombre.substring(0, 17) + "...";
    }

    private void seleccionarUbicacion(String ubicacion) {
        ubicacionSeleccionada = ubicacion;
        List<Lote> lotes = lotesSeleccionados();
        int cantidad = lotes.stream().mapToInt(lote -> entero(lote.getCantidadDisponible())).sum();
        lblSeleccion.setText(ubicacion == null ? "Selecciona una ubicación" : "Ubicación seleccionada: " + ubicacion);
        if (lblDetalleUbicacion != null) lblDetalleUbicacion.setText(ubicacion == null ? "-" : ubicacion);
        if (lblDetalleProducto != null) lblDetalleProducto.setText(lotes.isEmpty() ? "Libre" : lotes.stream()
                .map(lote -> lote.getProducto().getDescripcion())
                .distinct()
                .collect(Collectors.joining(", ")));
        if (lblDetalleLote != null) {
            lblDetalleLote.setText(lotes.isEmpty() ? "-" : lotes.stream()
                    .map(Lote::getCodigoLote)
                    .collect(Collectors.joining(", ")));
        }
        if (lblDetalleStock != null) lblDetalleStock.setText(lotes.isEmpty() ? "0" : cantidad + " unidades");
        String estado = lotes.isEmpty() ? "Libre" : cantidad == 0 ? "Sin stock" : "warehouse-low".equals(estiloCelda(lotes, cantidad)) ? "Stock bajo" : "Con stock";
        if (lblDetalleEstado != null) {
            lblDetalleEstado.setText(estado);
            lblDetalleEstado.getStyleClass().removeAll("estado-ok", "estado-low", "estado-empty", "estado-free");
            lblDetalleEstado.getStyleClass().add(lotes.isEmpty() ? "estado-free" : cantidad == 0 ? "estado-empty" : "Stock bajo".equals(estado) ? "estado-low" : "estado-ok");
        }
        historialLotes.setAll(lotes.stream().map(LoteUbicacionItem::new).toList());
        if (historialLotes.isEmpty()) {
            cargarLoteEnEditor(null);
        } else {
            tablaHistorialLotes.getSelectionModel().selectFirst();
            cargarLoteEnEditor(historialLotes.get(0).loteEntidad());
        }

        boolean haySeleccion = ubicacion != null;
        boolean ocupada = !lotes.isEmpty();
        btnAsignar.setDisable(!haySeleccion);
        btnMover.setDisable(!ocupada);
        btnEditarCantidad.setDisable(!ocupada);
        btnVaciar.setDisable(!ocupada);
        btnGuardarEdicion.setDisable(!ocupada);
    }

    private void cargarLoteEnEditor(Lote lote) {
        if (lote == null) {
            if (txtEditLote != null) txtEditLote.setText("");
            if (txtEditCantidad != null) txtEditCantidad.setText("");
            if (txtEditUbicacion != null) txtEditUbicacion.setText(ubicacionSeleccionada == null ? "" : ubicacionSeleccionada);
            return;
        }
        if (txtEditLote != null) txtEditLote.setText(texto(lote.getCodigoLote()));
        if (txtEditCantidad != null) txtEditCantidad.setText(String.valueOf(entero(lote.getCantidadDisponible())));
        if (txtEditUbicacion != null) txtEditUbicacion.setText(texto(lote.getUbicacion()));
    }

    private Lote loteActivoEnEditor() {
        LoteUbicacionItem item = tablaHistorialLotes.getSelectionModel().getSelectedItem();
        if (item != null && item.loteEntidad() != null) {
            return item.loteEntidad();
        }
        List<Lote> lotes = lotesSeleccionados();
        return lotes.isEmpty() ? null : lotes.get(0);
    }

    private void actualizarMetricas() {
        List<String> ubicaciones = ubicacionesVisibles();
        long ocupadas = ubicaciones.stream().filter(ubicacion -> !lotesPorUbicacion.getOrDefault(ubicacion, List.of()).isEmpty()).count();
        long criticas = ubicaciones.stream().filter(ubicacion -> {
            List<Lote> lotes = lotesPorUbicacion.getOrDefault(ubicacion, List.of());
            int cantidad = lotes.stream().mapToInt(lote -> entero(lote.getCantidadDisponible())).sum();
            return !lotes.isEmpty() && (cantidad == 0 || "warehouse-low".equals(estiloCelda(lotes, cantidad)));
        }).count();
        lblTotalUbicaciones.setText(String.valueOf(ubicaciones.size()));
        lblOcupadas.setText(String.valueOf(ocupadas));
        lblLibres.setText(String.valueOf(ubicaciones.size() - ocupadas));
        lblCriticas.setText(String.valueOf(criticas));
    }

    private List<String> ubicacionesVisibles() {
        List<String> ubicaciones = new ArrayList<>();
        for (String pasillo : pasillos) {
            for (int anaquel = 1; anaquel <= anaquelesPorPasillo.getOrDefault(pasillo, 4); anaquel++) {
                for (int nivel = 1; nivel <= niveles; nivel++) {
                    ubicaciones.add(pasillo + "-" + String.format("%02d", anaquel) + "-" + nivel);
                }
            }
        }
        return ubicaciones;
    }

    private boolean debeResaltar(String ubicacion, List<Lote> lotes, int cantidad) {
        String busqueda = filtroBusqueda();
        String estado = cmbFiltroMapa.getValue() == null ? "Todos" : cmbFiltroMapa.getValue();
        boolean coincideTexto = busqueda.isBlank() || coincideBusqueda(ubicacion, lotes, busqueda);
        boolean coincideEstado = "Todos".equals(estado)
                || ("Libre".equals(estado) && lotes.isEmpty())
                || ("Con stock".equals(estado) && !lotes.isEmpty() && cantidad > 0)
                || ("Sin stock".equals(estado) && !lotes.isEmpty() && cantidad == 0)
                || ("Stock bajo".equals(estado) && "warehouse-low".equals(estiloCelda(lotes, cantidad)));
        return coincideTexto && coincideEstado;
    }

    private boolean coincideBusqueda(String ubicacion, List<Lote> lotes, String filtro) {
        return ubicacion.toLowerCase(Locale.ROOT).contains(filtro)
                || lotes.stream().anyMatch(lote ->
                texto(lote.getCodigoLote()).toLowerCase(Locale.ROOT).contains(filtro)
                        || texto(lote.getProducto().getDescripcion()).toLowerCase(Locale.ROOT).contains(filtro)
                        || texto(lote.getProducto().getSku()).toLowerCase(Locale.ROOT).contains(filtro));
    }

    private String filtroBusqueda() {
        return txtBuscarUbicacion == null || txtBuscarUbicacion.getText() == null
                ? ""
                : txtBuscarUbicacion.getText().trim().toLowerCase(Locale.ROOT);
    }

    private List<Lote> lotesSeleccionados() {
        if (ubicacionSeleccionada == null) return List.of();
        return lotesPorUbicacion.getOrDefault(ubicacionSeleccionada, List.of());
    }

    private Lote elegirLoteSeleccionado(String titulo) {
        List<Lote> lotes = lotesSeleccionados();
        if (lotes.isEmpty()) {
            mostrarInfo("Ubicación libre", "Selecciona una ubicación ocupada.");
            return null;
        }
        LoteUbicacionItem itemSeleccionado = tablaHistorialLotes.getSelectionModel().getSelectedItem();
        if (itemSeleccionado != null && itemSeleccionado.loteEntidad() != null) {
            return itemSeleccionado.loteEntidad();
        }
        if (lotes.size() == 1) return lotes.get(0);

        Dialog<Lote> dialog = dialogElegirLote(titulo, lotes);
        return dialog.showAndWait().orElse(null);
    }

    private Dialog<Lote> dialogAsignarLote(List<Lote> lotes) {
        Dialog<Lote> dialog = dialogBase("Asignar lote a " + ubicacionSeleccionada, "Asignar lote");
        ComboBox<Lote> combo = comboLotes(lotes);
        combo.setValue(lotes.get(0));
        VBox contenido = new VBox(14,
                new Label("Selecciona el lote disponible que quieres colocar en esta ubicación."),
                campoVertical("Lote disponible", combo),
                resumenLote(combo.getValue()));
        contenido.getStyleClass().add("custom-modal");
        dialog.getDialogPane().setContent(contenido);
        dialog.setResultConverter(button -> button == ButtonType.OK ? combo.getValue() : null);
        return dialog;
    }

    private Dialog<Lote> dialogElegirLote(String titulo, List<Lote> lotes) {
        Dialog<Lote> dialog = dialogBase(titulo, "Continuar");
        ComboBox<Lote> combo = comboLotes(lotes);
        combo.setValue(lotes.get(0));
        VBox contenido = new VBox(14,
                new Label("Hay varios lotes en " + ubicacionSeleccionada + ". Elige cuál quieres modificar."),
                campoVertical("Lote", combo),
                resumenLote(combo.getValue()));
        contenido.getStyleClass().add("custom-modal");
        dialog.getDialogPane().setContent(contenido);
        dialog.setResultConverter(button -> button == ButtonType.OK ? combo.getValue() : null);
        return dialog;
    }

    private Dialog<String> dialogMoverLote(Lote lote) {
        Dialog<String> dialog = dialogBase("Mover mercadería", "Mover lote");
        TextField destino = new TextField(texto(lote.getUbicacion()));
        destino.setPromptText("Ej: B-02-3");
        VBox contenido = new VBox(14,
                resumenLote(lote),
                campoVertical("Nueva ubicación", destino),
                nota("Formato requerido: Pasillo-Anaquel-Nivel, por ejemplo A-01-1."));
        contenido.getStyleClass().add("custom-modal");
        dialog.getDialogPane().setContent(contenido);
        dialog.setResultConverter(button -> button == ButtonType.OK ? destino.getText().trim() : null);
        return dialog;
    }

    private Dialog<Integer> dialogCambiarCantidad(Lote lote) {
        Dialog<Integer> dialog = dialogBase("Cambiar cantidad", "Guardar cantidad");
        TextField cantidad = new TextField(String.valueOf(entero(lote.getCantidadDisponible())));
        cantidad.setPromptText("0 a " + entero(lote.getCantidadInicial()));
        Label error = new Label("");
        error.getStyleClass().add("chip-peligro");
        error.setVisible(false);
        error.setManaged(false);
        VBox contenido = new VBox(14,
                resumenLote(lote),
                campoVertical("Cantidad disponible", cantidad),
                nota("Máximo permitido: " + entero(lote.getCantidadInicial()) + ". Para aumentar más, registra compra o agrega lote."),
                error);
        contenido.getStyleClass().add("custom-modal");
        dialog.getDialogPane().setContent(contenido);
        ((Button) dialog.getDialogPane().lookupButton(ButtonType.OK)).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            Integer valor = enteroDesdeTexto(cantidad.getText());
            if (valor == null || valor < 0 || valor > entero(lote.getCantidadInicial())) {
                error.setText("Ingresa una cantidad entre 0 y " + entero(lote.getCantidadInicial()) + ".");
                error.setVisible(true); error.setManaged(true); event.consume();
            }
        });
        dialog.setResultConverter(button -> {
            if (button != ButtonType.OK) return null;
            Integer nuevaCantidad = enteroDesdeTexto(cantidad.getText());
            if (nuevaCantidad == null || nuevaCantidad < 0) {
                error.setText("Ingresa una cantidad mayor o igual a cero.");
                error.setVisible(true);
                error.setManaged(true);
                return null;
            }
            if (nuevaCantidad > entero(lote.getCantidadInicial())) {
                error.setText("No puede superar la cantidad inicial del lote.");
                error.setVisible(true);
                error.setManaged(true);
                return null;
            }
            return nuevaCantidad;
        });
        return dialog;
    }

    private boolean confirmarVaciar(List<Lote> lotes) {
        Dialog<Boolean> dialog = dialogBase("Vaciar ubicación " + ubicacionSeleccionada, "Vaciar ubicación");
        int cantidad = lotes.stream().mapToInt(lote -> entero(lote.getCantidadDisponible())).sum();
        VBox contenido = new VBox(14,
                nota("Esta acción pondrá en 0 la cantidad disponible de " + lotes.size()
                        + " lote(s), en total " + cantidad + " unidades."),
                nota("El stock del producto se recalculará automáticamente en la base de datos."));
        contenido.getStyleClass().add("custom-modal");
        dialog.getDialogPane().setContent(contenido);
        dialog.setResultConverter(button -> button == ButtonType.OK);
        return Boolean.TRUE.equals(dialog.showAndWait().orElse(false));
    }

    private Dialog dialogBase(String titulo, String textoPrimario) {
        Dialog dialog = new Dialog<>();
        dialog.setTitle(titulo);
        dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.getDialogPane().getStyleClass().add("app-dialog");
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
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
        });
        return dialog;
    }

    private ComboBox<Lote> comboLotes(List<Lote> lotes) {
        ComboBox<Lote> combo = new ComboBox<>(FXCollections.observableArrayList(lotes));
        combo.setMaxWidth(Double.MAX_VALUE);
        combo.setConverter(new StringConverter<>() {
            @Override
            public String toString(Lote lote) {
                if (lote == null) return "";
                return texto(lote.getProducto().getDescripcion()) + " · " + texto(lote.getCodigoLote())
                        + " · " + entero(lote.getCantidadDisponible()) + " uds.";
            }

            @Override
            public Lote fromString(String string) {
                return null;
            }
        });
        return combo;
    }

    private VBox campoVertical(String etiqueta, Control campo) {
        Label label = new Label(etiqueta);
        label.getStyleClass().add("field-label");
        VBox box = new VBox(6, label, campo);
        campo.setMaxWidth(Double.MAX_VALUE);
        return box;
    }

    private VBox resumenLote(Lote lote) {
        VBox box = new VBox(5);
        box.getStyleClass().add("warehouse-lot-summary");
        if (lote == null) {
            box.getChildren().add(nota("Sin lote seleccionado."));
            return box;
        }
        Label producto = new Label(texto(lote.getProducto().getDescripcion()));
        producto.getStyleClass().add("detail-value");
        Label meta = new Label("Lote " + texto(lote.getCodigoLote()) + " · SKU " + texto(lote.getProducto().getSku()));
        meta.getStyleClass().add("text-subtle");
        Label cantidad = new Label("Actual: " + entero(lote.getCantidadDisponible())
                + " · Inicial: " + entero(lote.getCantidadInicial())
                + " · Ubicación: " + texto(lote.getUbicacion()));
        cantidad.getStyleClass().add("text-subtle");
        box.getChildren().addAll(producto, meta, cantidad);
        return box;
    }

    private Label nota(String texto) {
        Label label = new Label(texto);
        label.setWrapText(true);
        label.getStyleClass().add("text-subtle");
        return label;
    }

    private void recalcularStockProducto(Producto producto) {
        int total = loteRepository.findByProductoIdOrderByFechaIngresoAsc(producto.getId()).stream()
                .mapToInt(lote -> entero(lote.getCantidadDisponible()))
                .sum();
        producto.setStockActual(total);
        productoRepository.save(producto);
    }

    private void asegurarUbicacionVisible(String ubicacion) {
        Ubicacion partes = parseUbicacion(ubicacion);
        if (partes == null) return;
        guardarUbicacion(ubicacion);
        if (!pasillos.contains(partes.pasillo())) {
            pasillos.add(partes.pasillo());
            pasillos.sort(String::compareTo);
        }
        anaqueles = Math.max(anaqueles, partes.anaquel());
        anaquelesPorPasillo.merge(partes.pasillo(), partes.anaquel(), Math::max);
        niveles = Math.max(niveles, partes.nivel());
    }

    private void cargarEstructuraDesdeBase() {
        List<UbicacionAlmacen> ubicaciones = ubicacionAlmacenRepository.findByActivoTrueOrderByPasilloAscAnaquelAscNivelAsc();
        if (ubicaciones.isEmpty()) return;
        pasillos.clear();
        ubicaciones.stream()
                .map(UbicacionAlmacen::getPasillo)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .forEach(pasillos::add);
        anaqueles = ubicaciones.stream().map(UbicacionAlmacen::getAnaquel).filter(Objects::nonNull).max(Integer::compareTo).orElse(4);
        anaquelesPorPasillo.clear();
        ubicaciones.forEach(u -> anaquelesPorPasillo.merge(u.getPasillo(), u.getAnaquel(), Math::max));
        niveles = ubicaciones.stream().map(UbicacionAlmacen::getNivel).filter(Objects::nonNull).max(Integer::compareTo).orElse(3);
    }

    private void guardarUbicacionesVisibles() {
        for (String codigo : ubicacionesVisibles()) {
            guardarUbicacion(codigo);
        }
    }

    private void guardarUbicacionesPasillo(String pasillo) {
        for (int anaquel = 1; anaquel <= anaquelesPorPasillo.getOrDefault(pasillo, 4); anaquel++) {
            for (int nivel = 1; nivel <= niveles; nivel++) {
                guardarUbicacion(pasillo + "-" + String.format("%02d", anaquel) + "-" + nivel);
            }
        }
    }

    private void guardarUbicacion(String codigo) {
        Ubicacion partes = parseUbicacion(codigo);
        if (partes == null) return;
        String normalizada = partes.pasillo() + "-" + String.format("%02d", partes.anaquel()) + "-" + partes.nivel();
        UbicacionAlmacen ubicacion = ubicacionAlmacenRepository.findByCodigo(normalizada).orElseGet(UbicacionAlmacen::new);
        ubicacion.setCodigo(normalizada);
        ubicacion.setPasillo(partes.pasillo());
        ubicacion.setAnaquel(partes.anaquel());
        ubicacion.setNivel(partes.nivel());
        ubicacion.setActivo(true);
        ubicacionAlmacenRepository.save(ubicacion);
    }

    private boolean retirarUbicaciones(List<UbicacionAlmacen> retirar) {
        Set<String> codigos = retirar.stream().map(UbicacionAlmacen::getCodigo).collect(Collectors.toSet());
        if (loteRepository.findAllWithProductoOrderByUbicacion().stream().anyMatch(l -> codigos.contains(normalizarUbicacion(l.getUbicacion())))) {
            mostrarError("Ubicación ocupada", "Mueve primero los lotes del último estante o nivel. No se ocultará mercadería."); return false;
        }
        retirar.forEach(u -> u.setActivo(false)); ubicacionAlmacenRepository.saveAll(retirar);
        if (codigos.contains(ubicacionSeleccionada)) ubicacionSeleccionada = null;
        return true;
    }

    private String normalizarUbicacion(String valor) {
        Ubicacion ubicacion = parseUbicacion(valor);
        if (ubicacion == null) return null;
        return ubicacion.pasillo() + "-" + String.format("%02d", ubicacion.anaquel()) + "-" + ubicacion.nivel();
    }

    private Ubicacion parseUbicacion(String valor) {
        if (valor == null || valor.isBlank()) return null;
        String[] partes = valor.trim().toUpperCase(Locale.ROOT).split("-");
        if (partes.length != 3 || partes[0].isBlank()) return null;
        try {
            int anaquel = Integer.parseInt(partes[1]);
            int nivel = Integer.parseInt(partes[2]);
            if (anaquel <= 0 || nivel <= 0) return null;
            return new Ubicacion(partes[0], anaquel, nivel);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Button botonIcono(String icono) {
        Button boton = new Button();
        boton.getStyleClass().add("btn-secundario");
        FontIcon graphic = new FontIcon(icono);
        graphic.setIconSize(13);
        boton.setGraphic(graphic);
        return boton;
    }

    private String tooltipUbicacion(String ubicacion, List<Lote> lotes, int cantidad) {
        if (lotes.isEmpty()) return ubicacion + "\nLibre";
        return ubicacion + "\n" + cantidad + " unidades\n" + lotes.stream()
                .map(lote -> lote.getCodigoLote() + " · " + lote.getProducto().getDescripcion())
                .collect(Collectors.joining("\n"));
    }

    private Integer enteroDesdeTexto(String valor) {
        try {
            return Integer.parseInt(valor.trim());
        } catch (Exception e) {
            return null;
        }
    }

    private int entero(Integer valor) {
        return valor == null ? 0 : valor;
    }

    private String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    private void mostrarInfo(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, mensaje);
        alert.setHeaderText(titulo);
        alert.showAndWait();
    }

    private void mostrarError(String titulo, String mensaje) {
        Alert alert = new Alert(Alert.AlertType.ERROR, mensaje);
        alert.setHeaderText(titulo);
        alert.showAndWait();
    }

    public record LoteUbicacionItem(Lote loteEntidad, String producto, String lote, String cantidad, String inicial) {
        public LoteUbicacionItem(Lote lote) {
            this(lote,
                    lote.getProducto() == null ? "Producto" : lote.getProducto().getDescripcion(),
                    lote.getCodigoLote(),
                    String.valueOf(lote.getCantidadDisponible() == null ? 0 : lote.getCantidadDisponible()),
                    String.valueOf(lote.getCantidadInicial() == null ? 0 : lote.getCantidadInicial()));
        }
    }

    private record Ubicacion(String pasillo, int anaquel, int nivel) {
    }
}
