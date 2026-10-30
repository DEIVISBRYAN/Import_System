package com.ejemplo.crudpersona.controller;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import com.ejemplo.crudpersona.ui.AppStyles;
import com.ejemplo.crudpersona.model.Producto;
import com.ejemplo.crudpersona.model.Rol;
import com.ejemplo.crudpersona.model.Usuario;
import com.ejemplo.crudpersona.model.Venta;
import com.ejemplo.crudpersona.repository.ProductoRepository;
import com.ejemplo.crudpersona.repository.VentaRepository;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.kordamp.ikonli.javafx.FontIcon;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class ERQZMainController {

    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private ProductoRepository productoRepository;
    @Autowired
    private VentaRepository ventaRepository;
    @Autowired private com.ejemplo.crudpersona.service.RPLCTerminalSesion sesion;
    @Autowired private com.ejemplo.crudpersona.repository.CajaTurnoRepository turnos;
    @Autowired private com.ejemplo.crudpersona.service.DBQPTipoCambioServiceI cambios;
    @Autowired private com.ejemplo.crudpersona.service.RPLCReposicionServiceI reposicion;
    @Autowired private com.ejemplo.crudpersona.repository.ComprobanteSunatRepository comprobantes;

    private Usuario usuarioActual;
    private boolean isCollapsed = false;
    private Boolean manualSidebarChoice;
    private boolean isDarkTheme = false;

    @FXML private VBox sidebar;
    @FXML private Button btnToggleSidebar;
    @FXML private StackPane centerContent;

    // Secciones y Botones del Menú Lateral
    @FXML private VBox seccionGeneral;
    @FXML private VBox seccionVentas;
    @FXML private VBox seccionAlmacen;
    @FXML private VBox seccionCompras;
    @FXML private VBox seccionPrecios;
    @FXML private VBox seccionFacturacion;
    @FXML private VBox seccionAdmin;

    @FXML private Button btnNavDashboard;
    @FXML private Button btnNavNuevaVenta;
    @FXML private Button btnNavVentas;
    @FXML private Button btnNavCaja;
    @FXML private Button btnNavInventario;
    @FXML private Button btnNavReporteStock;
    @FXML private Button btnNavAlmacen;
    @FXML private Button btnNavReposicion;
    @FXML private Button btnNavNuevaCompra;
    @FXML private Button btnNavProveedores;
    @FXML private Button btnNavTipoCambio;
    @FXML private Button btnNavComprobantes;
    @FXML private Button btnNavUsuarios;
    @FXML private Button btnNavSunarp;

    @FXML private HBox navItemInventario;
    @FXML private HBox navItemReposicion;
    @FXML private HBox navItemComprobantes;

    // Badges de alerta
    @FXML private Label badgeInventario;
    @FXML private Label badgeReposicion;
    @FXML private Label badgeComprobantes;

    // Perfil inferior
    @FXML private Label lblUserInitials;
    @FXML private Label lblUserName;
    @FXML private Label lblUserRole;

    // Barra superior
    @FXML private Label lblTituloPantalla;
    @FXML private Label lblChipDolar;
    @FXML private Label lblChipCaja;
    @FXML private Label lblTopUserName;
    @FXML private Label lblTopUserRole;
    @FXML private Label lblSearchHint;
    @FXML private HBox btnTopBuscar;

    // Elementos para ocultar al colapsar
    @FXML private Label lblMarca;
    @FXML private Label lblMarcaSubtitulo;
    @FXML private VBox boxMarca;
    @FXML private VBox boxUserDetails;
    @FXML private FlowPane userActions;
    @FXML private Button btnTema;
    @FXML private Button btnSalir;

    // Constructor vacío por defecto necesario para FXMLLoader
    public ERQZMainController() {
    }

    public ERQZMainController(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    @FXML
    public void initialize() {
        isDarkTheme = AppStyles.isDarkTheme();
        actualizarBotonTema();
        isCollapsed = false;
        manualSidebarChoice = null;
        aplicarAnchoSidebar();

        // Se difiere la carga de la vista inicial para evitar fallos durante la carga de main-view.fxml
        Platform.runLater(() -> {
            registrarAtajos();
            cargarVistaInicial();
        });
    }

    private void registrarAtajos() {
        if (centerContent == null || centerContent.getScene() == null) return;
        Scene scene = centerContent.getScene();
        if (btnTopBuscar != null) Tooltip.install(btnTopBuscar, new Tooltip("Buscar en el sistema (Ctrl+K)"));
        scene.widthProperty().addListener((obs, old, width) -> ajustarMenu(width.doubleValue()));
        ajustarMenu(scene.getWidth());
        centerContent.getScene().addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.isControlDown() && event.getCode() == KeyCode.K) {
                event.consume();
                abrirBuscadorGlobal();
            }
        });
    }

    public void setUsuarioActual(Usuario usuario) {
        this.usuarioActual = usuario;
        sesion.setUsuario(usuario);
        actualizarChipCaja();
        actualizarDatosUsuario();
        filtrarMenuPorRol();
    }

    private void cargarVistaInicial() {
        Pantalla inicial = pantallaInicialPorRol();
        abrirPantalla(inicial, nodoParaPantalla(inicial));
    }

    public void refrescarIndicadores() { actualizarChipCaja(); }
    @org.springframework.transaction.event.TransactionalEventListener
    public void alActualizarDolar(com.ejemplo.crudpersona.service.ERQZTipoCambioActualizado evento) {
        if (lblChipDolar != null && lblChipDolar.getScene() != null) javafx.application.Platform.runLater(this::actualizarChipCaja);
    }

    private void actualizarChipCaja() {
        if (lblChipDolar != null) lblChipDolar.setText(cambios.actual().toPlainString());
        if (badgeInventario != null) badgeInventario.setText(String.valueOf(productoRepository.findStockBajo().size() + productoRepository.findSinStock().size()));
        if (badgeReposicion != null) badgeReposicion.setText(String.valueOf(reposicion.urgentes()));
        if (badgeComprobantes != null) badgeComprobantes.setText(String.valueOf(comprobantes.countByEstadoSunat("PENDIENTE")));
        if (lblChipCaja != null) {
            boolean abierta = turnos.findByNumeroCajaAndEstado(sesion.numeroCaja(), "ABIERTA").isPresent();
            lblChipCaja.setText("Caja " + sesion.numeroCaja() + (abierta ? " abierta" : " cerrada"));
        }
    }

    private Pantalla pantallaInicialPorRol() {
        Rol rol = usuarioActual == null || usuarioActual.getRol() == null ? Rol.ADMINISTRADOR : usuarioActual.getRol();
        Pantalla preferida = switch (rol) {
            case ADMINISTRADOR, GERENTE -> Pantalla.DASHBOARD;
            case CAJERO -> Pantalla.NUEVA_VENTA;
            case ALMACENERO -> Pantalla.ALMACEN;
            case CONTADOR -> Pantalla.COMPROBANTES;
        };
        if (tieneAcceso(preferida)) {
            return preferida;
        }
        return permisos(rol).stream().findFirst().orElse(Pantalla.DASHBOARD);
    }

    private void actualizarDatosUsuario() {
        if (usuarioActual == null) return;
        if (lblUserName != null) lblUserName.setText(usuarioActual.getNombreCompleto());
        if (lblUserRole != null) lblUserRole.setText(usuarioActual.getRol().getTitulo());
        if (lblTopUserName != null) lblTopUserName.setText(usuarioActual.getNombreCompleto());
        if (lblTopUserRole != null) lblTopUserRole.setText(usuarioActual.getRol().getTitulo());

        // Iniciales (ej: Carlos Mendoza -> CM)
        String[] partes = usuarioActual.getNombreCompleto().split(" ");
        String iniciales = partes.length >= 2
                ? (partes[0].substring(0, 1) + partes[1].substring(0, 1)).toUpperCase()
                : usuarioActual.getUsername().substring(0, Math.min(2, usuarioActual.getUsername().length())).toUpperCase();
        if (lblUserInitials != null) lblUserInitials.setText(iniciales);
    }

    private void filtrarMenuPorRol() {
        setVisible(btnNavDashboard, tieneAcceso(Pantalla.DASHBOARD));
        setVisible(btnNavNuevaVenta, tieneAcceso(Pantalla.NUEVA_VENTA));
        setVisible(btnNavVentas, tieneAcceso(Pantalla.VENTAS));
        setVisible(btnNavCaja, tieneAcceso(Pantalla.CAJA));
        setVisible(navItemInventario, tieneAcceso(Pantalla.INVENTARIO));
        setVisible(btnNavReporteStock, tieneAcceso(Pantalla.REPORTE_STOCK));
        setVisible(btnNavAlmacen, tieneAcceso(Pantalla.ALMACEN));
        setVisible(navItemReposicion, tieneAcceso(Pantalla.REPOSICION));
        setVisible(btnNavNuevaCompra, tieneAcceso(Pantalla.NUEVA_COMPRA));
        setVisible(btnNavProveedores, tieneAcceso(Pantalla.PROVEEDORES));
        setVisible(btnNavTipoCambio, tieneAcceso(Pantalla.TIPO_CAMBIO));
        setVisible(navItemComprobantes, tieneAcceso(Pantalla.COMPROBANTES));
        setVisible(btnNavUsuarios, tieneAcceso(Pantalla.USUARIOS));
        setVisible(btnNavSunarp, tieneAcceso(Pantalla.SUNARP));

        setVisible(seccionGeneral, tieneAcceso(Pantalla.DASHBOARD));
        setVisible(seccionVentas, tieneAcceso(Pantalla.NUEVA_VENTA) || tieneAcceso(Pantalla.VENTAS) || tieneAcceso(Pantalla.CAJA));
        setVisible(seccionAlmacen, tieneAcceso(Pantalla.INVENTARIO) || tieneAcceso(Pantalla.REPORTE_STOCK)
                || tieneAcceso(Pantalla.ALMACEN) || tieneAcceso(Pantalla.REPOSICION));
        setVisible(seccionCompras, tieneAcceso(Pantalla.NUEVA_COMPRA) || tieneAcceso(Pantalla.PROVEEDORES) || tieneAcceso(Pantalla.SUNARP));
        setVisible(seccionPrecios, tieneAcceso(Pantalla.TIPO_CAMBIO));
        setVisible(seccionFacturacion, tieneAcceso(Pantalla.COMPROBANTES));
        setVisible(seccionAdmin, tieneAcceso(Pantalla.USUARIOS));
    }

    private void setVisible(Node node, boolean visible) {
        if (node == null) return;
        node.setVisible(visible);
        node.setManaged(visible);
    }

    @FXML
    public void onToggleSidebar(ActionEvent event) {
        isCollapsed = !isCollapsed;
        manualSidebarChoice = isCollapsed;
        aplicarAnchoSidebar();
    }

    private void ajustarMenu(double width) {
        boolean compact = width < 1000;
        setVisible(lblSearchHint, !compact);
        setVisible(lblTopUserRole, !compact);
        if (btnTopBuscar != null) {
            btnTopBuscar.setMinWidth(compact ? 40 : 190);
            btnTopBuscar.setPrefWidth(compact ? 44 : 205);
            btnTopBuscar.setMaxWidth(compact ? 44 : Double.MAX_VALUE);
            btnTopBuscar.setAccessibleText("Buscar en el sistema");
        }
        if (manualSidebarChoice == null) {
            isCollapsed = width < 1040;
            aplicarAnchoSidebar();
        }
    }

    private void aplicarAnchoSidebar() {
        if (sidebar == null) return;

        double width = isCollapsed ? 80 : 306;
        sidebar.setPrefWidth(width);
        sidebar.setMinWidth(width);
        sidebar.setMaxWidth(width);

        sidebar.getStyleClass().remove("sidebar-collapsed");
        if (isCollapsed) sidebar.getStyleClass().add("sidebar-collapsed");
        ContentDisplay display = isCollapsed ? ContentDisplay.GRAPHIC_ONLY : ContentDisplay.LEFT;
        Button[] navegacion = {btnNavDashboard, btnNavNuevaVenta, btnNavVentas, btnNavCaja,
                btnNavInventario, btnNavReporteStock, btnNavAlmacen, btnNavReposicion,
                btnNavNuevaCompra, btnNavProveedores, btnNavTipoCambio, btnNavComprobantes, btnNavUsuarios, btnNavSunarp};
        for (Button boton : navegacion) {
            if (boton == null) continue;
            boton.setContentDisplay(display);
            boton.setMaxWidth(Double.MAX_VALUE);
            boton.setMinWidth(0);
            boton.setWrapText(!isCollapsed);
            boton.setAlignment(isCollapsed ? Pos.CENTER : Pos.CENTER_LEFT);
            boton.setGraphicTextGap(12);
            if (boton.getTooltip() == null) boton.setTooltip(new Tooltip(boton.getText()));
        }
        boolean showText = !isCollapsed;
        setVisible(boxMarca, showText);
        setVisible(boxUserDetails, showText);
        if (userActions != null) userActions.setAlignment(isCollapsed ? Pos.CENTER : Pos.CENTER_RIGHT);
        if (btnToggleSidebar != null) {
            btnToggleSidebar.setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            btnToggleSidebar.setAccessibleText(showText ? "Ocultar menú" : "Mostrar menú");
            btnToggleSidebar.setTooltip(new Tooltip(showText ? "Ocultar menú" : "Mostrar menú"));
            if (btnToggleSidebar.getGraphic() instanceof FontIcon icon) {
                icon.setIconLiteral(showText ? "fth-chevron-left" : "fth-chevron-right");
            }
        }
        if (lblUserName != null) {
            lblUserName.setVisible(showText);
            lblUserName.setManaged(showText);
        }
        if (lblUserRole != null) {
            lblUserRole.setVisible(showText);
            lblUserRole.setManaged(showText);
        }
        if (btnTema != null) btnTema.setContentDisplay(display);
        if (btnSalir != null) btnSalir.setContentDisplay(display);

        if (seccionGeneral != null && !seccionGeneral.getChildren().isEmpty()) {
            seccionGeneral.getChildren().get(0).setVisible(showText);
            seccionGeneral.getChildren().get(0).setManaged(showText);
        }
        if (seccionVentas != null && !seccionVentas.getChildren().isEmpty()) {
            seccionVentas.getChildren().get(0).setVisible(showText);
            seccionVentas.getChildren().get(0).setManaged(showText);
        }
        if (seccionAlmacen != null && !seccionAlmacen.getChildren().isEmpty()) {
            seccionAlmacen.getChildren().get(0).setVisible(showText);
            seccionAlmacen.getChildren().get(0).setManaged(showText);
        }
        if (seccionCompras != null && !seccionCompras.getChildren().isEmpty()) {
            seccionCompras.getChildren().get(0).setVisible(showText);
            seccionCompras.getChildren().get(0).setManaged(showText);
        }
        if (seccionPrecios != null && !seccionPrecios.getChildren().isEmpty()) {
            seccionPrecios.getChildren().get(0).setVisible(showText);
            seccionPrecios.getChildren().get(0).setManaged(showText);
        }
        if (seccionFacturacion != null && !seccionFacturacion.getChildren().isEmpty()) {
            seccionFacturacion.getChildren().get(0).setVisible(showText);
            seccionFacturacion.getChildren().get(0).setManaged(showText);
        }
        if (seccionAdmin != null && !seccionAdmin.getChildren().isEmpty()) {
            setVisible(seccionAdmin.getChildren().get(0), showText);
        }

        if (badgeInventario != null) {
            badgeInventario.setVisible(showText);
            badgeInventario.setManaged(showText);
        }
        if (badgeReposicion != null) {
            badgeReposicion.setVisible(showText);
            badgeReposicion.setManaged(showText);
        }
        if (badgeComprobantes != null) {
            badgeComprobantes.setVisible(showText);
            badgeComprobantes.setManaged(showText);
        }
    }

    public void irA(String fxmlPath, String titulo) {
        Pantalla pantalla = Pantalla.porFxml(fxmlPath);
        if (pantalla != null && !tieneAcceso(pantalla)) {
            mostrarAccesoDenegado(titulo);
            return;
        }
        try {
            var resource = getClass().getResource(fxmlPath);
            if (resource == null) {
                System.err.println("Advertencia: No se encontró la vista FXML: " + fxmlPath);
                return;
            }
            FXMLLoader loader = new FXMLLoader(resource);
            if (applicationContext != null) {
                loader.setControllerFactory(applicationContext::getBean);
            }
            Node view = loader.load();
            if (view instanceof Parent parent) AppStyles.prepareView(parent);
            if (!(view instanceof ScrollPane)) {
                ScrollPane scroll = new ScrollPane();
                scroll.setContent(view);
                scroll.setFitToWidth(true);
                scroll.setFitToHeight(true);
                scroll.getStyleClass().add("pagina-scroll");
                view = scroll;
            }

            if (centerContent != null) {
                centerContent.getChildren().setAll(view);
            }
            if (lblTituloPantalla != null) {
                lblTituloPantalla.setText(titulo);
            }

        } catch (Exception e) {
            System.err.println("Advertencia al cargar pantalla: " + fxmlPath + " - " + e.getMessage());
            e.printStackTrace();
            mostrarErrorVista(titulo, e);
        }
    }

    private void abrirPantalla(Pantalla pantalla, Node activo) {
        irA(pantalla.fxml, pantalla.titulo);
        actualizarChipCaja();
        marcarActivo(activo);
    }

    private boolean tieneAcceso(Pantalla pantalla) {
        Rol rol = usuarioActual == null || usuarioActual.getRol() == null ? Rol.ADMINISTRADOR : usuarioActual.getRol();
        return permisos(rol).contains(pantalla);
    }

    private Set<Pantalla> permisos(Rol rol) {
        return switch (rol) {
            case ADMINISTRADOR -> EnumSet.allOf(Pantalla.class);
            case GERENTE -> EnumSet.of(Pantalla.DASHBOARD, Pantalla.NUEVA_VENTA, Pantalla.VENTAS, Pantalla.CAJA,
                    Pantalla.INVENTARIO, Pantalla.REPORTE_STOCK, Pantalla.ALMACEN, Pantalla.REPOSICION, Pantalla.NUEVA_COMPRA,
                    Pantalla.PROVEEDORES, Pantalla.TIPO_CAMBIO, Pantalla.COMPROBANTES, Pantalla.SUNARP);
            case CAJERO -> EnumSet.of(Pantalla.NUEVA_VENTA, Pantalla.VENTAS, Pantalla.CAJA);
            case ALMACENERO -> EnumSet.of(Pantalla.INVENTARIO, Pantalla.REPORTE_STOCK, Pantalla.ALMACEN, Pantalla.REPOSICION,
                    Pantalla.NUEVA_COMPRA, Pantalla.PROVEEDORES, Pantalla.SUNARP);
            case CONTADOR -> EnumSet.of(Pantalla.DASHBOARD, Pantalla.VENTAS, Pantalla.CAJA,
                    Pantalla.TIPO_CAMBIO, Pantalla.COMPROBANTES, Pantalla.SUNARP);
        };
    }

    private void mostrarAccesoDenegado(String titulo) {
        if (centerContent == null) return;
        VBox box = new VBox(10);
        box.getStyleClass().addAll("tarjeta", "detail-panel");
        Label label = new Label("Acceso restringido");
        label.getStyleClass().add("title-4");
        Label detalle = new Label("Tu usuario no tiene permiso para abrir " + titulo + ".");
        detalle.getStyleClass().add("text-subtle");
        detalle.setWrapText(true);
        Label rol = new Label("Rol actual: " + (usuarioActual == null || usuarioActual.getRol() == null
                ? "Administrador"
                : usuarioActual.getRol().getTitulo()));
        rol.getStyleClass().add("chip-info");
        Button inicio = new Button("Ir a mi inicio");
        inicio.getStyleClass().add("btn-primario");
        inicio.setOnAction(event -> cargarVistaInicial());
        box.getChildren().addAll(label, detalle, rol, inicio);
        centerContent.getChildren().setAll(box);
    }

    private void mostrarErrorVista(String titulo, Exception e) {
        if (centerContent == null) {
            return;
        }
        VBox errorBox = new VBox(10);
        errorBox.getStyleClass().add("tarjeta");
        Label tituloError = new Label("No se pudo abrir " + titulo);
        tituloError.getStyleClass().add("title-4");
        Label detalle = new Label(e.getMessage() == null ? "Revisa la consola para más detalles." : e.getMessage());
        detalle.getStyleClass().add("text-subtle");
        detalle.setWrapText(true);
        errorBox.getChildren().addAll(tituloError, detalle);
        centerContent.getChildren().setAll(errorBox);
    }

    private void marcarActivo(Node activo) {
        Node[] botones = {
                btnNavDashboard, btnNavNuevaVenta, btnNavVentas, btnNavCaja, btnNavInventario,
                btnNavReporteStock, btnNavAlmacen, btnNavReposicion, btnNavNuevaCompra, btnNavProveedores,
                btnNavTipoCambio, btnNavComprobantes, btnNavUsuarios, btnNavSunarp,
                navItemInventario, navItemReposicion, navItemComprobantes
        };
        for (Node boton : botones) {
            if (boton != null) {
                boton.getStyleClass().remove("nav-activo");
            }
        }
        if (activo != null && !activo.getStyleClass().contains("nav-activo")) {
            activo.getStyleClass().add("nav-activo");
        }
    }

    @FXML public void onNavDashboard() { abrirPantalla(Pantalla.DASHBOARD, btnNavDashboard); }
    @FXML public void onNavNuevaVenta() { abrirPantalla(Pantalla.NUEVA_VENTA, btnNavNuevaVenta); }
    @FXML public void onNavVentas() { abrirPantalla(Pantalla.VENTAS, btnNavVentas); }
    @FXML public void onNavCaja() { abrirPantalla(Pantalla.CAJA, btnNavCaja); }
    @FXML public void onNavInventario() { abrirPantalla(Pantalla.INVENTARIO, navItemInventario != null ? navItemInventario : btnNavInventario); }
    @FXML public void onNavReporteStock() { abrirPantalla(Pantalla.REPORTE_STOCK, btnNavReporteStock); }
    @FXML public void onNavAlmacen() { abrirPantalla(Pantalla.ALMACEN, btnNavAlmacen); }
    @FXML public void onNavReposicion() { abrirPantalla(Pantalla.REPOSICION, navItemReposicion != null ? navItemReposicion : btnNavReposicion); }
    @FXML public void onNavNuevaCompra() { abrirPantalla(Pantalla.NUEVA_COMPRA, btnNavNuevaCompra); }
    @FXML public void onNavProveedores() { abrirPantalla(Pantalla.PROVEEDORES, btnNavProveedores); }
    @FXML public void onNavTipoCambio() { abrirPantalla(Pantalla.TIPO_CAMBIO, btnNavTipoCambio); }
    @FXML public void onNavComprobantes() { abrirPantalla(Pantalla.COMPROBANTES, navItemComprobantes != null ? navItemComprobantes : btnNavComprobantes); }
    @FXML public void onNavUsuarios() { abrirPantalla(Pantalla.USUARIOS, btnNavUsuarios); }
    @FXML public void onNavSunarp() { abrirPantalla(Pantalla.SUNARP, btnNavSunarp); }

    @FXML
    public void onToggleTheme() {
        isDarkTheme = !AppStyles.isDarkTheme();
        AppStyles.setDarkTheme(isDarkTheme);
        actualizarBotonTema();
    }

    private void actualizarBotonTema() {
        if (btnTema != null) {
            btnTema.setGraphic(new FontIcon(isDarkTheme ? "fth-sun" : "fth-moon"));
            btnTema.setTooltip(new Tooltip(isDarkTheme ? "Cambiar a modo claro" : "Cambiar a modo nocturno"));
        }
    }

    @FXML
    public void onTopUsuario(MouseEvent event) {
        onMiPerfil();
    }
    @FXML public void onMiPerfil() {
        try {com.ejemplo.crudpersona.ui.MiPerfilDialog.mostrar(sidebar.getScene().getWindow(),
                applicationContext.getBean(com.ejemplo.crudpersona.service.RPLCUsuariosServiceI.class),this::setUsuarioActual);}
        catch(RuntimeException ex){var alerta=new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.ERROR,ex.getMessage());alerta.initOwner(sidebar.getScene().getWindow());alerta.setHeaderText("No se pudo abrir Mi perfil");alerta.showAndWait();}
    }

    @FXML
    public void onTopDolar(MouseEvent event) {
        abrirPantalla(Pantalla.TIPO_CAMBIO, btnNavTipoCambio);
    }

    @FXML
    public void onTopCaja(MouseEvent event) {
        abrirPantalla(Pantalla.CAJA, btnNavCaja);
    }

    @FXML
    public void onTopBuscar(MouseEvent event) {
        abrirBuscadorGlobal();
    }

    private void abrirBuscadorGlobal() {
        Dialog<ResultadoBusqueda> dialog = new Dialog<>();
        dialog.setTitle("Buscar");
        dialog.getDialogPane().getStyleClass().add("app-dialog");
        dialog.getDialogPane().getButtonTypes().addAll(javafx.scene.control.ButtonType.OK, javafx.scene.control.ButtonType.CANCEL);

        TextField buscar = new TextField();
        buscar.setPromptText("Producto, venta, DNI/RUC, cliente o módulo");
        buscar.getStyleClass().add("dialog-form-field");
        ListView<ResultadoBusqueda> resultados = new ListView<>();
        resultados.setPrefHeight(320);
        resultados.getStyleClass().add("tabla");

        Runnable actualizar = () -> resultados.setItems(FXCollections.observableArrayList(resultadosBusqueda(buscar.getText())));
        buscar.textProperty().addListener((obs, old, value) -> actualizar.run());
        actualizar.run();

        VBox contenido = new VBox(14,
                new Label("Busca y abre rápido cualquier módulo, producto o venta."),
                buscar,
                resultados);
        contenido.getStyleClass().add("custom-modal");
        dialog.getDialogPane().setContent(contenido);
        dialog.setOnShown(e -> {
            buscar.requestFocus();
            Button ok = (Button) dialog.getDialogPane().lookupButton(javafx.scene.control.ButtonType.OK);
            Button cancel = (Button) dialog.getDialogPane().lookupButton(javafx.scene.control.ButtonType.CANCEL);
            if (ok != null) {
                ok.setText("Abrir");
                ok.getStyleClass().add("btn-primario");
            }
            if (cancel != null) {
                cancel.setText("Cancelar");
                cancel.getStyleClass().add("btn-secundario");
            }
        });
        resultados.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2 && resultados.getSelectionModel().getSelectedItem() != null) {
                dialog.setResult(resultados.getSelectionModel().getSelectedItem());
                dialog.close();
            }
        });
        dialog.setResultConverter(button -> button == javafx.scene.control.ButtonType.OK
                ? resultados.getSelectionModel().getSelectedItem()
                : null);
        dialog.showAndWait().ifPresent(resultado -> abrirPantalla(resultado.pantalla(), nodoParaPantalla(resultado.pantalla())));
    }

    private List<ResultadoBusqueda> resultadosBusqueda(String textoBusqueda) {
        String filtro = textoBusqueda == null ? "" : textoBusqueda.trim().toLowerCase(Locale.ROOT);
        Rol rol = usuarioActual == null || usuarioActual.getRol() == null ? Rol.ADMINISTRADOR : usuarioActual.getRol();
        List<ResultadoBusqueda> resultados = new ArrayList<>();

        permisos(rol).stream()
                .filter(p -> filtro.isBlank() || p.titulo.toLowerCase(Locale.ROOT).contains(filtro))
                .forEach(p -> resultados.add(new ResultadoBusqueda(p.titulo, "Módulo del sistema", p)));

        if (!filtro.isBlank()) {
            for (Producto producto : permisos(rol).contains(Pantalla.INVENTARIO) ? productoRepository.findAll() : List.<Producto>of()) {
                if (contiene(producto.getDescripcion(), filtro)
                        || contiene(producto.getSku(), filtro)
                        || contiene(producto.getCodigoBarras(), filtro)) {
                    resultados.add(new ResultadoBusqueda(producto.getDescripcion(),
                            "Producto · SKU " + producto.getSku() + " · Stock " + producto.getStockActual(),
                            Pantalla.INVENTARIO));
                }
                if (resultados.size() >= 20) break;
            }

            for (Venta venta : permisos(rol).contains(Pantalla.VENTAS) ? ventaRepository.findAllByOrderByFechaEmisionDesc() : List.<Venta>of()) {
                String comprobante = venta.getSerie() + "-" + String.format("%06d", venta.getCorrelativo() == null ? 0 : venta.getCorrelativo());
                if (contiene(venta.getClienteNombre(), filtro)
                        || contiene(venta.getClienteDocumento(), filtro)
                        || contiene(comprobante, filtro)
                        || contiene(venta.getEstado(), filtro)) {
                    resultados.add(new ResultadoBusqueda(comprobante,
                            "Venta · " + texto(venta.getClienteNombre()) + " · " + texto(venta.getClienteDocumento()),
                            Pantalla.VENTAS));
                }
                if (resultados.size() >= 32) break;
            }
        }
        return resultados.stream().limit(32).toList();
    }

    private boolean contiene(String valor, String filtro) {
        return valor != null && valor.toLowerCase(Locale.ROOT).contains(filtro);
    }

    private String texto(String valor) {
        return valor == null || valor.isBlank() ? "-" : valor;
    }

    private Node nodoParaPantalla(Pantalla pantalla) {
        return switch (pantalla) {
            case DASHBOARD -> btnNavDashboard;
            case NUEVA_VENTA -> btnNavNuevaVenta;
            case VENTAS -> btnNavVentas;
            case CAJA -> btnNavCaja;
            case INVENTARIO -> navItemInventario != null ? navItemInventario : btnNavInventario;
            case REPORTE_STOCK -> btnNavReporteStock;
            case ALMACEN -> btnNavAlmacen;
            case REPOSICION -> navItemReposicion != null ? navItemReposicion : btnNavReposicion;
            case NUEVA_COMPRA -> btnNavNuevaCompra;
            case PROVEEDORES -> btnNavProveedores;
            case TIPO_CAMBIO -> btnNavTipoCambio;
            case COMPROBANTES -> navItemComprobantes != null ? navItemComprobantes : btnNavComprobantes;
            case USUARIOS -> btnNavUsuarios;
            case SUNARP -> btnNavSunarp;
        };
    }

    @FXML
    public void onCerrarSesion() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/view/login-view.fxml"));
            if (applicationContext != null) {
                loader.setControllerFactory(applicationContext::getBean);
            }
            Parent root = loader.load();
            Stage stage = (Stage) sidebar.getScene().getWindow();
            Scene scene = new Scene(root, 1024, 680);
            AppStyles.apply(scene);
            stage.setScene(scene);
            sesion.setUsuario(null);
            usuarioActual = null;
            stage.setMaximized(false);
            stage.centerOnScreen();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private enum Pantalla {
        DASHBOARD("/view/dashboard-view.fxml", "Dashboard"),
        NUEVA_VENTA("/view/punto-venta-view.fxml", "Nueva venta"),
        VENTAS("/view/ventas-view.fxml", "Ventas"),
        CAJA("/view/caja-view.fxml", "Caja"),
        INVENTARIO("/view/inventario-view.fxml", "Inventario"),
        REPORTE_STOCK("/view/reporte-stock-view.fxml", "Reporte de stock"),
        ALMACEN("/view/almacen-view.fxml", "Almacén y ubicaciones"),
        REPOSICION("/view/reposicion-view.fxml", "Reposición"),
        NUEVA_COMPRA("/view/nueva-compra-view.fxml", "Nueva compra (DUA)"),
        PROVEEDORES("/view/proveedores-view.fxml", "Proveedores"),
        TIPO_CAMBIO("/view/tipo-cambio-view.fxml", "Tipo de cambio"),
        COMPROBANTES("/view/comprobantes-view.fxml", "Comprobantes Electrónicos SUNAT"),
        USUARIOS("/view/usuarios-view.fxml", "Usuarios"),
        SUNARP("/view/sunarp-view.fxml", "Motos y expedientes SUNARP");

        private final String fxml;
        private final String titulo;

        Pantalla(String fxml, String titulo) {
            this.fxml = fxml;
            this.titulo = titulo;
        }

        @Override
        public String toString() {
            return titulo;
        }

        private static Pantalla porFxml(String fxml) {
            for (Pantalla pantalla : values()) {
                if (pantalla.fxml.equals(fxml)) {
                    return pantalla;
                }
            }
            return null;
        }
    }

    private record ResultadoBusqueda(String titulo, String detalle, Pantalla pantalla) {
        @Override
        public String toString() {
            return titulo + "   " + detalle;
        }
    }
}
