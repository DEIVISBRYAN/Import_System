package com.ejemplo.crudpersona.controller;

import com.ejemplo.crudpersona.model.Lote;
import com.ejemplo.crudpersona.model.Producto;
import com.ejemplo.crudpersona.repository.LoteRepository;
import com.ejemplo.crudpersona.repository.ProductoRepository;
import com.ejemplo.crudpersona.service.ERQZDatabaseDateNormalizer;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.geometry.Pos;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import com.ejemplo.crudpersona.ui.ArchivosDuaPane;
import com.ejemplo.crudpersona.ui.DocumentosDuaDialog;
import com.ejemplo.crudpersona.ui.ImportacionesDuaDialog;
import com.ejemplo.crudpersona.service.DBQPBorradoresDuaServiceI;
import com.ejemplo.crudpersona.service.DBQPDocumentosDuaServiceI;
import com.ejemplo.crudpersona.service.ERQZDuaPdfService;
import com.ejemplo.crudpersona.service.ERQZReporteTabular;
import com.ejemplo.crudpersona.ui.ExportarMenu;
import com.ejemplo.crudpersona.model.ERQZArchivoDua;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Component
public class DBQPNuevaCompraController {

    private final ProductoRepository productoRepository;
    private final LoteRepository loteRepository;
    private final JdbcTemplate jdbcTemplate;
    private final ERQZDatabaseDateNormalizer dateNormalizer;
    private final com.ejemplo.crudpersona.service.ERQZCompraServiceI compras;
    private final com.ejemplo.crudpersona.service.DBQPTipoCambioServiceI cambios;
    private final DBQPDocumentosDuaServiceI documentosDua;
    private final DBQPBorradoresDuaServiceI borradores;
    private final ERQZDuaPdfService lectorPdf;
    private final java.util.Set<String> pdfLeidos=new java.util.HashSet<>();
    private ERQZDuaPdfService.Lectura ultimaLectura;
    @FXML private Label lblLecturaPdf;
    @FXML private ExportarMenu exportarReporte;
    @FXML private Button btnTextoPdf;
    @FXML private TableColumn<CompraLinea,String> colRevision;
    @FXML private VBox boxArchivosDua;
    @FXML private Button btnGuardarDocumentosDua;
    @FXML private Button btnOtroDocumentoDua;
    @FXML private Button btnRegistrarCompra;
    @FXML private Button btnAgregarLinea;
    @FXML private ToggleButton btnSoloDocumentos;
    @FXML private ToggleButton btnModoCompra;
    @FXML private javafx.scene.layout.Pane cabeceraCompra;
    @FXML private VBox productoCompra;
    @FXML private TitledPane editorProducto;
    @FXML private javafx.scene.layout.Pane resumenCompra;
    private ArchivosDuaPane archivosDua;
    private Long documentoDuaId;
    private Long borradorId;
    private int versionBorrador;
    private CompraLinea editandoLinea;
    private EstadoFormulario estadoGuardado;

    @FXML private ComboBox<ProveedorCompra> cmbProveedor;
    @FXML private TextField txtRuc;
    @FXML private TextField txtDua;
    @FXML private DatePicker dpFecha;
    @FXML private TextField txtTipoCambio;
    @FXML private TextField txtMargen;
    @FXML private TextField txtSku;
    @FXML private TextField txtProducto;
    @FXML private TextField txtCodigoBarras;
    @FXML private TextField txtLote;
    @FXML private TextField txtUbicacion;
    @FXML private TextField txtCantidad;
    @FXML private TextField txtFobUsd;
    @FXML private TextField txtGastosUsd;
    @FXML private Label lblEstado;
    @FXML private TableView<CompraLinea> tablaLineas;
    @FXML private TableColumn<CompraLinea, String> colSku;
    @FXML private TableColumn<CompraLinea, String> colProducto;
    @FXML private TableColumn<CompraLinea, String> colLote;
    @FXML private TableColumn<CompraLinea, String> colUbicacion;
    @FXML private TableColumn<CompraLinea, Integer> colCantidad;
    @FXML private TableColumn<CompraLinea, String> colFob;
    @FXML private TableColumn<CompraLinea, String> colGastos;
    @FXML private TableColumn<CompraLinea, String> colLanded;
    @FXML private TableColumn<CompraLinea, String> colPrecio;
    @FXML private TableColumn<CompraLinea, CompraLinea> colAcciones;
    @FXML private Label lblFobUsd;
    @FXML private Label lblGastosUsd;
    @FXML private Label lblLandedUsd;
    @FXML private Label lblLandedPen;
    @FXML private Label lblUnidades;
    @FXML private Label lblTituloTotales;

    private final ObservableList<CompraLinea> lineas = FXCollections.observableArrayList();
    private final ObservableList<ProveedorCompra> proveedores = FXCollections.observableArrayList();

    public DBQPNuevaCompraController(ProductoRepository productoRepository, LoteRepository loteRepository,
                                 JdbcTemplate jdbcTemplate, ERQZDatabaseDateNormalizer dateNormalizer,
                                 com.ejemplo.crudpersona.service.ERQZCompraServiceI compras, com.ejemplo.crudpersona.service.DBQPTipoCambioServiceI cambios,
                                 DBQPDocumentosDuaServiceI documentosDua, DBQPBorradoresDuaServiceI borradores, ERQZDuaPdfService lectorPdf) {
        this.productoRepository = productoRepository;
        this.loteRepository = loteRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.dateNormalizer = dateNormalizer;
        this.compras = compras; this.cambios = cambios; this.documentosDua = documentosDua; this.borradores=borradores;
        this.lectorPdf=lectorPdf;
    }

    @FXML
    public void initialize() {
        exportarReporte.setReporte(this::reporteDua);
        dateNormalizer.normalize();
        txtTipoCambio.setText(cambios.actual().toPlainString());
        dpFecha.setValue(LocalDate.now());
        txtDua.clear();
        documentoDuaId = null;
        borradorId=null; versionBorrador=0; editandoLinea=null; estadoGuardado=null; lineas.clear();
        archivosDua = new ArchivosDuaPane(); boxArchivosDua.getChildren().setAll(archivosDua);
        pdfLeidos.clear();ultimaLectura=null;archivosDua.alAdjuntar(this::leerAdjuntosPdf);lblLecturaPdf.setText("");btnTextoPdf.setDisable(true);
        btnGuardarDocumentosDua.disableProperty().bind(archivosDua.ocupadoProperty().or(javafx.beans.binding.Bindings.isEmpty(archivosDua.seleccion())));
        btnRegistrarCompra.disableProperty().bind(archivosDua.ocupadoProperty());
        btnOtroDocumentoDua.disableProperty().bind(archivosDua.ocupadoProperty());
        for(var zona:List.of(cabeceraCompra,editorProducto,resumenCompra)) zona.disableProperty().bind(archivosDua.ocupadoProperty());
        txtLote.setText("L-" + (2400 + (int) (System.currentTimeMillis() % 300)));
        txtUbicacion.setText("A-01-1");
        txtCantidad.setText("1");
        txtFobUsd.setText("0.00");
        txtGastosUsd.setText("0.00");
        editorProducto.setAnimated(false);
        cmbProveedor.valueProperty().addListener((o,a,p)->txtRuc.setText(p==null?"":p.ruc()));
        cargarProveedores();

        colSku.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().sku()));
        colProducto.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().descripcion()));
        colLote.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().lote()));
        colUbicacion.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().ubicacion()));
        colCantidad.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().cantidad()));
        colRevision.setCellValueFactory(data->new SimpleStringProperty(porRevisar(data.getValue()).equals("Completa")?"Completos":porRevisar(data.getValue())));
        colFob.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().fobUsd()==null?"Por revisar":"US$ " + data.getValue().fobUsd()));
        colGastos.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().gastosUsd()==null?"Por revisar":"US$ " + data.getValue().gastosUsd()));
        colLanded.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().landedUsd()==null?"Por revisar":"US$ " + data.getValue().landedUsd()));
        colPrecio.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().landedUsd()==null?"Por revisar":"S/ " + data.getValue().landedUsd().multiply(decimal(txtTipoCambio.getText()))
                .multiply(BigDecimal.ONE.add(data.getValue().margen().divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP))).setScale(2, RoundingMode.HALF_UP)));
        colAcciones.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue()));
        colAcciones.setCellFactory(col -> new TableCell<>() {
            private final Button editar = ArchivosDuaPane.icono("fth-edit-2", "Editar producto");
            private final Button eliminar = ArchivosDuaPane.icono("fth-trash-2", "Eliminar producto");
            private final HBox acciones = new HBox(6, editar, eliminar);
            {
                editar.getStyleClass().addAll("dua-row-action", "dua-edit-line");
                eliminar.getStyleClass().addAll("dua-row-action", "dua-delete-line");
                editar.setAccessibleText("Editar producto");
                eliminar.setAccessibleText("Eliminar producto");
                acciones.setAlignment(Pos.CENTER);
                editar.setOnAction(e -> editarLinea(getItem()));
                eliminar.setOnAction(e -> quitarLinea(getItem()));
            }
            @Override protected void updateItem(CompraLinea item, boolean empty) {
                super.updateItem(item, empty);
                setText(null);
                setGraphic(empty || item == null ? null : acciones);
                if (!empty && item != null) {
                    editar.getTooltip().setText("Editar: " + item.descripcion());
                    eliminar.getTooltip().setText("Eliminar: " + item.descripcion());
                }
            }
        });
        tablaLineas.setItems(lineas);
        lineas.addListener((javafx.collections.ListChangeListener<CompraLinea>) change -> recalcularTotales());
        txtTipoCambio.textProperty().addListener((obs, old, value) -> { recalcularTotales(); tablaLineas.refresh(); });
        recalcularTotales();
        onCambiarModo();
    }
    private static String porRevisar(CompraLinea l) {
        var faltan=new java.util.ArrayList<String>();
        if(l.sku().isBlank())faltan.add("SKU");if(l.descripcion().isBlank())faltan.add("nombre");
        if(l.lote().isBlank())faltan.add("lote");if(l.ubicacion().isBlank())faltan.add("ubicación");
        if(l.cantidad()<=0)faltan.add("cantidad");if(l.fobUsd()==null)faltan.add("FOB");if(l.gastosUsd()==null)faltan.add("gastos");
        return faltan.isEmpty()?"Completa":String.join(", ",faltan);
    }
    public ERQZReporteTabular reporteDua() {
        return new ERQZReporteTabular("Productos DUA", "DUA: " + txtDua.getText() + " | Vista previa del formulario, sin aprobación ni cambio de stock. Los importes son unitarios.",
                List.of("SKU", "Producto", "Código barras", "Lote", "Ubicación", "Cantidad", "FOB USD", "Gastos USD", "Datos pendientes"),
                tablaLineas.getItems().stream().map(l -> ERQZReporteTabular.fila(l.sku(), l.descripcion(), l.codigoBarras(), l.lote(), l.ubicacion(), l.cantidad(),
                        l.fobUsd(), l.gastosUsd(), porRevisar(l).equals("Completa") ? "Completos" : porRevisar(l))).toList());
    }
    private void leerAdjuntosPdf(List<ERQZArchivoDua> nuevos) {
        var candidatos=nuevos.stream().filter(a->a.tipo().equals("application/pdf") || a.tipo().startsWith("image/")).toList();
        if(candidatos.isEmpty())return;
        ERQZArchivoDua archivo=candidatos.get(0);
        if(candidatos.size()>1){var opciones=candidatos.stream().map(OpcionPdf::new).toList();var elegir=new ChoiceDialog<>(opciones.get(0),opciones);
            elegir.initOwner(boxArchivosDua.getScene().getWindow());elegir.setTitle("Leer datos de DUA (PDF o Imagen)");elegir.setHeaderText("Selecciona el documento o imagen de la DUA");
            var elegido=elegir.showAndWait();if(elegido.isEmpty())return;archivo=elegido.get().archivo();}
        if(pdfLeidos.contains(archivo.huella())){archivosDua.mensaje("Los datos de este archivo ya están cargados en el formulario.",false);return;}
        if(editandoLinea!=null){archivosDua.mensaje("Hay un producto en edición. Guarda o cancela sus cambios antes de leer el documento.",true);return;}
        if(!lineas.isEmpty()){
            Alert confirmar=new Alert(Alert.AlertType.CONFIRMATION,"Se añadirán los productos detectados a las líneas actuales, sin reemplazarlas ni modificar stock.",ButtonType.OK,ButtonType.CANCEL);
            confirmar.initOwner(boxArchivosDua.getScene().getWindow());confirmar.setHeaderText("¿Añadir productos de la DUA?");if(confirmar.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return;
        }
        ERQZArchivoDua elegido=archivo;var formulario=archivosDua;
        formulario.procesar(()->lectorPdf.leer(elegido),lectura->{if(formulario!=archivosDua || formulario.getScene()==null)return;aplicarLectura(lectura);});
    }
    private record OpcionPdf(ERQZArchivoDua archivo){@Override public String toString(){return archivo.nombre();}}
    @FXML public void onLeerProductosPdf() {
        if(archivosDua.ocupadoProperty().get())return;
        var pendientes=archivosDua.archivos();Long documento=documentoDuaId;var formulario=archivosDua;
        if(pendientes.stream().anyMatch(a->a.tipo().equals("application/pdf") || a.tipo().startsWith("image/"))){leerAdjuntosPdf(pendientes);return;}
        if(documento==null){formulario.mensaje("Adjunta primero un archivo PDF o imagen (JPG/PNG) de la DUA.",true);return;}
        formulario.procesar(()->documentosDua.archivos(documento).stream().map(a->documentosDua.descargar(a.id())).toList(),archivos->{
            if(formulario!=archivosDua || formulario.getScene()==null)return;
            if(archivos.isEmpty())formulario.mensaje("El expediente no tiene archivos PDF ni imágenes.",true);else leerAdjuntosPdf(archivos);
        });
    }
    private void aplicarLectura(ERQZDuaPdfService.Lectura lectura) {
        ultimaLectura=lectura;btnTextoPdf.setDisable(false);
        if(lectura.productos().isEmpty()) {
            btnModoCompra.setSelected(true);onCambiarModo();editorProducto.setExpanded(true);
            if(txtDua.getText().isBlank() && !lectura.dua().isBlank())txtDua.setText(lectura.dua());
            lblLecturaPdf.setText(String.join("\n",lectura.avisos()));
            lblEstado.setText("Documento adjunto · Completa datos del producto");
            archivosDua.mensaje("Documento adjunto listo para registrar. Completa los datos del producto y guarda la DUA.",false);
            mostrarZona(editorProducto);
            return;
        }
        if(lineas.size()+lectura.productos().size()>500){archivosDua.mensaje("La importación supera las 500 líneas.",true);return;}
        boolean nueva=lineas.isEmpty() && borradorId==null && txtDua.getText().isBlank();
        if(nueva){
            if(!lectura.dua().isBlank())txtDua.setText(lectura.dua());
            if(lectura.fecha()!=null)dpFecha.setValue(lectura.fecha());if(lectura.cambio()!=null)txtTipoCambio.setText(lectura.cambio().toPlainString());
            if(!lectura.ruc().isBlank()){
                ProveedorCompra prov = proveedores.stream().filter(p->p.ruc().equals(lectura.ruc())).findFirst().orElse(null);
                if(prov == null){
                    try {
                        String razon = lectura.texto() != null && lectura.texto().toUpperCase().contains("HONDA DEL PERU")
                                ? "HONDA DEL PERU S.A" : "IMPORTADOR DUA " + lectura.ruc();
                        jdbcTemplate.update("INSERT INTO proveedores(razon_social,ruc,estado) VALUES (?,?,'ACTIVO')", razon, lectura.ruc());
                        cargarProveedores();
                        prov = proveedores.stream().filter(p->p.ruc().equals(lectura.ruc())).findFirst().orElse(null);
                    } catch (Exception ignored) {}
                }
                if(prov != null) cmbProveedor.setValue(prov);
            }
        }
        BigDecimal cambio=decimal(txtTipoCambio.getText()),margenPorDefecto=decimal(txtMargen.getText());
        for(var p:lectura.productos()){
            BigDecimal usd=p.fob()==null||p.gastos()==null?null:p.fob().add(p.gastos());
            BigDecimal pen=usd==null?null:usd.multiply(cambio);BigDecimal margen=p.margen()==null?margenPorDefecto:p.margen();
            lineas.add(new CompraLinea(p.sku(),p.nombre(),p.barras(),p.lote(),p.ubicacion(),p.cantidad()==null?0:p.cantidad(),p.fob(),p.gastos(),usd,pen,margen,
                    pen==null?null:pen.multiply(BigDecimal.ONE.add(margen.movePointLeft(2))).setScale(2,RoundingMode.HALF_UP)));
        }
        pdfLeidos.add(lectura.huella());btnModoCompra.setSelected(true);onCambiarModo();
        editorProducto.setExpanded(false);
        long incompletas=lineas.stream().filter(l->!porRevisar(l).equals("Completa")).count();
        lblLecturaPdf.setText(lectura.archivo()+" · "+lectura.productos().size()+" productos detectados · "+incompletas+" líneas con datos pendientes"
                +(lectura.avisos().isEmpty()?"":"\n"+String.join("\n",lectura.avisos()))
                +(nueva && !lectura.ruc().isBlank() && cmbProveedor.getValue()==null?"\nProveedor del PDF no registrado: "+lectura.ruc():""));
        lblEstado.setText("PDF cargado · Pendiente de revisión");archivosDua.mensaje("Productos cargados en el formulario. Sin cambios en stock.",false);
        mostrarZona(resumenCompra);
    }
    private void mostrarZona(javafx.scene.Node zona) {
        javafx.application.Platform.runLater(()->{
            if(zona.getScene()==null)return;
            for(var padre=zona.getParent();padre!=null;padre=padre.getParent())if(padre instanceof ScrollPane scroll){
                var contenido=scroll.getContent();contenido.applyCss();if(contenido instanceof javafx.scene.Parent p)p.layout();
                double alto=contenido.getLayoutBounds().getHeight()-scroll.getViewportBounds().getHeight();
                if(alto>0){double y=contenido.sceneToLocal(zona.localToScene(zona.getBoundsInLocal())).getMinY();scroll.setVvalue(Math.max(0,Math.min(1,y/alto)));}
                break;
            }
        });
    }
    @FXML public void onTextoPdf() {
        if(ultimaLectura==null)return;
        Dialog<Void> dialog=new Dialog<>();dialog.setTitle("Texto extraído del PDF");dialog.initOwner(boxArchivosDua.getScene().getWindow());
        dialog.getDialogPane().getStyleClass().addAll("app-dialog","wide-dialog");dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        TextArea texto=new TextArea(String.join("\n",ultimaLectura.avisos())+"\n"+ultimaLectura.texto());texto.setEditable(false);texto.setWrapText(true);texto.setPrefSize(800,470);texto.setId("textoExtraidoPdf");
        dialog.getDialogPane().setContent(texto);dialog.showAndWait();
    }

    @FXML public void onCambiarModo() {
        if (!btnSoloDocumentos.isSelected() && !btnModoCompra.isSelected()) btnSoloDocumentos.setSelected(true);
        boolean compra = btnModoCompra.isSelected();
        for (var zona : List.of(cabeceraCompra, editorProducto, resumenCompra)) { zona.setVisible(compra); zona.setManaged(compra); }
        for (var boton : List.of(btnSoloDocumentos, btnModoCompra)) {
            boton.getStyleClass().removeAll("btn-primario", "btn-secundario");
            boton.getStyleClass().add(boton.isSelected() ? "btn-primario" : "btn-secundario");
        }
    }

    @FXML
    public void onAgregarLinea() {
        String sku = texto(txtSku.getText());
        String descripcion = texto(txtProducto.getText());
        if (sku.isBlank() || descripcion.isBlank()) {
            mostrarError("Datos incompletos", "Ingresa SKU y nombre del producto.");
            return;
        }
        if(texto(txtLote.getText()).isBlank() || texto(txtUbicacion.getText()).isBlank()){
            mostrarError("Datos incompletos","Completa el lote y la ubicación reales del producto.");return;
        }
        int cantidad = entero(txtCantidad.getText(), -1);
        if (cantidad <= 0) {
            mostrarError("Cantidad inválida", "Ingresa una cantidad mayor a cero.");
            return;
        }
        BigDecimal fob = decimal(txtFobUsd.getText());
        BigDecimal gastos = decimal(txtGastosUsd.getText());
        BigDecimal margen = decimal(txtMargen.getText());
        BigDecimal tc = decimal(txtTipoCambio.getText());
        try {
            new BigDecimal(txtFobUsd.getText().trim().replace(',', '.'));
            new BigDecimal(txtGastosUsd.getText().trim().replace(',', '.'));
            new BigDecimal(txtMargen.getText().trim().replace(',', '.'));
        } catch (NumberFormatException e) {
            mostrarError("Importe inválido", "Revisa los costos y el margen."); return;
        }
        if (fob.signum() < 0 || gastos.signum() < 0 || margen.signum() < 0) {
            mostrarError("Importe inválido", "Los costos y el margen no pueden ser negativos."); return;
        }
        if (tc.compareTo(BigDecimal.ZERO) <= 0) {
            mostrarError("Tipo de cambio inválido", "Ingresa un tipo de cambio mayor a cero.");
            return;
        }

        BigDecimal landedUsd = fob.add(gastos).setScale(4, RoundingMode.HALF_UP);
        BigDecimal landedPen = landedUsd.multiply(tc).setScale(4, RoundingMode.HALF_UP);
        BigDecimal precio = landedPen.multiply(BigDecimal.ONE.add(margen.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP)))
                .setScale(2, RoundingMode.HALF_UP);

        CompraLinea nueva=new CompraLinea(
                sku,
                descripcion,
                texto(txtCodigoBarras.getText()),
                texto(txtLote.getText()).isBlank() ? "L-" + (System.currentTimeMillis() % 10000) : texto(txtLote.getText()),
                texto(txtUbicacion.getText()).isBlank() ? "A-01-1" : texto(txtUbicacion.getText()).toUpperCase(),
                cantidad,
                fob.setScale(4, RoundingMode.HALF_UP),
                gastos.setScale(4, RoundingMode.HALF_UP),
                landedUsd,
                landedPen,
                margen.setScale(2, RoundingMode.HALF_UP),
                precio
        );
        boolean edicion = editandoLinea != null;
        if(!edicion) lineas.add(nueva);
        else {
            int indice=indiceLinea(editandoLinea);
            if(indice<0){mostrarInfo("Producto no disponible","La línea ya no está en el formulario.");onLimpiarLinea();return;}
            lineas.set(indice,nueva);
        }
        onLimpiarLinea();
        if(edicion){editorProducto.setExpanded(false);mostrarZona(resumenCompra);}
    }

    @FXML
    public void onLimpiarLinea() {
        editandoLinea=null; btnAgregarLinea.setText("Agregar producto y lote");
        txtSku.clear();
        txtProducto.clear();
        txtCodigoBarras.clear();
        txtLote.setText("L-" + (2400 + (int) (System.currentTimeMillis() % 300)));
        txtUbicacion.setText("A-01-1");
        txtCantidad.setText("1");
        txtFobUsd.setText("0.00");
        txtGastosUsd.setText("0.00");
    }

    @FXML
    public void onQuitarLinea() {
        quitarLinea(tablaLineas.getSelectionModel().getSelectedItem());
    }
    @FXML public void onEditarLinea() {
        var l=tablaLineas.getSelectionModel().getSelectedItem();
        if(l==null){mostrarInfo("Selecciona un producto","Selecciona la línea que deseas editar.");return;}
        editarLinea(l);
    }
    private int indiceLinea(CompraLinea linea) {
        for(int i=0;i<lineas.size();i++)if(lineas.get(i)==linea)return i;
        return -1;
    }
    private void quitarLinea(CompraLinea item) {
        if(item==null || archivosDua.ocupadoProperty().get() || indiceLinea(item)<0)return;
        Alert confirmar=new Alert(Alert.AlertType.CONFIRMATION,
                item.descripcion()+" · "+item.sku()+" · Lote "+item.lote()+" · "+item.cantidad()+" unidades\n"
                +"Se quitará del formulario. Guarda la DUA para conservar el cambio. El stock no cambiará.",ButtonType.OK,ButtonType.CANCEL);
        confirmar.initOwner(tablaLineas.getScene().getWindow());confirmar.setHeaderText("¿Eliminar este producto de la DUA?");
        if(confirmar.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return;
        int indice=indiceLinea(item);
        if(indice>=0)lineas.remove(indice);
        if(item==editandoLinea)onLimpiarLinea();
    }
    private void editarLinea(CompraLinea l) {
        if(l==null || archivosDua.ocupadoProperty().get() || indiceLinea(l)<0)return;
        if(editandoLinea!=null && editandoLinea!=l){
            Alert confirmar=new Alert(Alert.AlertType.CONFIRMATION,"Hay otro producto en edición. Sus cambios sin guardar se descartarán.",ButtonType.OK,ButtonType.CANCEL);
            confirmar.initOwner(tablaLineas.getScene().getWindow());confirmar.setHeaderText("¿Editar otro producto?");
            if(confirmar.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return;
        }
        if(editandoLinea==l){editorProducto.setExpanded(true);mostrarZona(editorProducto);return;}
        editandoLinea=l;txtSku.setText(l.sku());txtProducto.setText(l.descripcion());txtCodigoBarras.setText(l.codigoBarras());
        editorProducto.setExpanded(true);
        txtLote.setText(l.lote());txtUbicacion.setText(l.ubicacion());txtCantidad.setText(""+l.cantidad());
        txtFobUsd.setText(l.fobUsd()==null?"":l.fobUsd().toPlainString());txtGastosUsd.setText(l.gastosUsd()==null?"":l.gastosUsd().toPlainString());txtMargen.setText(l.margen().toPlainString());
        btnAgregarLinea.setText("Guardar cambios del producto");txtProducto.requestFocus();
        mostrarZona(editorProducto);
    }

    @FXML
    public void onRegistrarCompra() {
        if(editandoLinea!=null){mostrarInfo("Producto en edición","Guarda los cambios del producto o cancela con Limpiar línea antes de guardar la DUA.");return;}
        if (!validarCabecera()) return;
        for(var l:lineas)if(!porRevisar(l).equals("Completa")){tablaLineas.getSelectionModel().select(l);mostrarError("Producto por revisar",l.descripcion()+": completa "+porRevisar(l)+".");return;}
        if (lineas.isEmpty()) {
            mostrarError("Sin productos", "Agrega al menos un producto a la compra.");
            return;
        }

        ProveedorCompra proveedor = cmbProveedor.getValue();
        Long id=borradorId, documento=documentoDuaId;int version=versionBorrador;
        String dua=txtDua.getText();var fecha=dpFecha.getValue();var cambio=decimal(txtTipoCambio.getText());var archivos=archivosDua.archivos();
        var formulario=archivosDua;
        var detalle=lineas.stream().map(l -> new com.ejemplo.crudpersona.service.ERQZCompraServiceI.Linea(l.sku(), l.descripcion(),
                l.codigoBarras(), l.lote(), l.ubicacion(), l.cantidad(), l.fobUsd(), l.gastosUsd(), l.margen())).toList();
        formulario.procesar(()->{
            Long guardado=borradores.guardar(id,version,proveedor.id(),dua,fecha,cambio,detalle,documento,archivos);
            return borradores.obtener(guardado);
        },d->{
            if(archivosDua!=formulario || formulario.getScene()==null)return;
            borradorId=d.resumen().id();versionBorrador=d.resumen().version();documentoDuaId=d.resumen().documentoId();
            archivosDua.reiniciar();if(documentoDuaId!=null)archivosDua.guardado(documentoDuaId);
            lblEstado.setText("Pendiente #"+borradorId+" · Guardada en BD");btnRegistrarCompra.setText("Guardar cambios de la DUA");
            estadoGuardado=estadoActual();
            mostrarInfo("DUA registrada como pendiente","Se guardaron "+d.resumen().productos()+" líneas y "+d.resumen().unidades()+" unidades en PostgreSQL. El stock no cambió. Abre Pendientes y aprobadas para revisarla y aprobarla.");
        });
    }

    @FXML public void onGuardarDocumentosDua() {
        if(borradorId!=null){onRegistrarCompra();return;}
        var archivos = archivosDua.archivos(); Long id = documentoDuaId;var formulario=archivosDua;
        formulario.procesar(() -> {
            if (id == null) return documentosDua.crear(archivos);
            documentosDua.adjuntar(id, archivos); return id;
        }, guardado -> {
            if(archivosDua!=formulario || formulario.getScene()==null)return;
            documentoDuaId = guardado; archivosDua.guardado(guardado);
            lblEstado.setText("Documentos guardados · Sin cambio de stock");
        });
    }
    @FXML public void onDocumentosDua() {
        if(archivosDua.ocupadoProperty().get())return;
        if(borradorId!=null || documentoDuaId!=null) DocumentosDuaDialog.mostrar(boxArchivosDua.getScene().getWindow(), documentosDua, documentoDuaId);
        else DocumentosDuaDialog.mostrar(boxArchivosDua.getScene().getWindow(),documentosDua,null,d->{
            documentoDuaId=d.id();archivosDua.guardado(documentoDuaId);lblEstado.setText("Documento #"+documentoDuaId+" seleccionado");
        });
        if(borradorId!=null) {
            try {if(borradores.obtener(borradorId).resumen().version()!=versionBorrador)
                mostrarInfo("Registro actualizado","Los archivos o datos cambiaron. Abre Pendientes y aprobadas y vuelve a editar esta DUA para trabajar con su última versión.");}
            catch(RuntimeException ex){mostrarError("Registro actualizado",ex.getMessage());}
        }
    }
    @FXML public void onOtroDocumentoDua() {
        if(borradorId!=null){onNuevaDua();return;}
        if (!archivosDua.archivos().isEmpty()) {
            Alert confirmacion = new Alert(Alert.AlertType.CONFIRMATION, "Hay archivos sin guardar. ¿Quitar esta selección para adjuntar otro DUA?", ButtonType.OK, ButtonType.CANCEL);
            confirmacion.initOwner(boxArchivosDua.getScene().getWindow());
            if (confirmacion.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
        }
        documentoDuaId = null; archivosDua.reiniciar(); lblEstado.setText("Lista para registrar");
    }

    @FXML
    public void onHistorial() {
        if(archivosDua.ocupadoProperty().get())return;
        ImportacionesDuaDialog.mostrar(boxArchivosDua.getScene().getWindow(),borradores,documentosDua,borradorId,d->{
            if(tieneCambios()) {
                Alert a=new Alert(Alert.AlertType.CONFIRMATION,"Se reemplazará el formulario actual. Los cambios sin guardar se perderán.",ButtonType.OK,ButtonType.CANCEL);
                a.initOwner(boxArchivosDua.getScene().getWindow());if(a.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)throw new IllegalStateException("Edición cancelada.");
            }
            cargarBorrador(d);
        },this::onHistorialAnterior);
        if(borradorId!=null) {
            try {var d=borradores.obtener(borradorId);if(!d.resumen().estado().equals("PENDIENTE"))reiniciarFormulario();}
            catch(IllegalArgumentException ex){reiniciarFormulario();}
        }
    }
    @FXML public void onNuevaDua() {
        if(archivosDua.ocupadoProperty().get())return;
        if(tieneCambios()) {
            Alert a=new Alert(Alert.AlertType.CONFIRMATION,"Se limpiará el formulario. Los registros ya guardados permanecerán en Pendientes y aprobadas.",ButtonType.OK,ButtonType.CANCEL);
            a.initOwner(boxArchivosDua.getScene().getWindow());if(a.showAndWait().orElse(ButtonType.CANCEL)!=ButtonType.OK)return;
        }
        reiniciarFormulario();
    }
    private void reiniciarFormulario() {
        borradorId=null;versionBorrador=0;documentoDuaId=null;estadoGuardado=null;lineas.clear();archivosDua.reiniciar();onLimpiarLinea();
        pdfLeidos.clear();ultimaLectura=null;lblLecturaPdf.setText("");btnTextoPdf.setDisable(true);
        editorProducto.setExpanded(true);
        txtDua.clear();dpFecha.setValue(LocalDate.now());btnRegistrarCompra.setText("Guardar DUA pendiente");lblEstado.setText("Lista para registrar");
    }
    private void cargarBorrador(DBQPBorradoresDuaServiceI.Detalle d) {
        pdfLeidos.clear();ultimaLectura=null;lblLecturaPdf.setText("");btnTextoPdf.setDisable(true);
        borradorId=d.resumen().id();versionBorrador=d.resumen().version();documentoDuaId=d.resumen().documentoId();
        cargarProveedores();cmbProveedor.setValue(proveedores.stream().filter(p->p.id().equals(d.proveedorId())).findFirst()
                .orElse(new ProveedorCompra(d.proveedorId(),"",d.resumen().proveedor())));
        txtDua.setText(d.resumen().dua());dpFecha.setValue(d.resumen().fecha());txtTipoCambio.setText(d.tipoCambio().toPlainString());
        lineas.setAll(d.lineas().stream().map(l->{
            var usd=l.fobUsd().add(l.gastosUsd());var pen=usd.multiply(d.tipoCambio());
            return new CompraLinea(l.sku(),l.descripcion(),l.codigoBarras(),l.lote(),l.ubicacion(),l.cantidad(),l.fobUsd(),l.gastosUsd(),usd,pen,l.margen(),
                    pen.multiply(BigDecimal.ONE.add(l.margen().movePointLeft(2))).setScale(2,RoundingMode.HALF_UP));
        }).toList());onLimpiarLinea();archivosDua.reiniciar();if(documentoDuaId!=null)archivosDua.guardado(documentoDuaId);
        btnModoCompra.setSelected(true);onCambiarModo();btnRegistrarCompra.setText("Guardar cambios de la DUA");lblEstado.setText("Editando pendiente #"+borradorId);
        estadoGuardado=estadoActual();
    }
    private EstadoFormulario estadoActual() {
        return new EstadoFormulario(cmbProveedor.getValue()==null?null:cmbProveedor.getValue().id(),texto(txtDua.getText()),dpFecha.getValue(),
                decimal(txtTipoCambio.getText()).stripTrailingZeros().toPlainString(),List.copyOf(lineas));
    }
    private boolean tieneCambios() {
        if(!archivosDua.archivos().isEmpty() || editandoLinea!=null || !texto(txtSku.getText()).isEmpty() || !texto(txtProducto.getText()).isEmpty())return true;
        return estadoGuardado==null ? !lineas.isEmpty() || !texto(txtDua.getText()).isEmpty() : !estadoGuardado.equals(estadoActual());
    }
    private record EstadoFormulario(Long proveedor,String dua,LocalDate fecha,String cambio,List<CompraLinea> lineas) {}
    private void onHistorialAnterior() {
        List<String> compras = jdbcTemplate.query(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbcTemplate, """
                SELECT fecha_ingreso, proveedor, numero_dua, total_landed_pen
                FROM compras_dua
                ORDER BY id DESC
                LIMIT 10
                """), (rs, rowNum) -> rs.getString("fecha_ingreso") + " | " + rs.getString("proveedor")
                + " | " + rs.getString("numero_dua") + " | S/ " + String.format("%.2f", rs.getDouble("total_landed_pen")));
        Alert alert = new Alert(Alert.AlertType.INFORMATION, compras.isEmpty() ? "Todavía no hay compras registradas." : String.join("\n", compras));
        alert.initOwner(boxArchivosDua.getScene().getWindow());
        alert.setHeaderText("Últimas compras DUA");
        alert.showAndWait();
    }

    private boolean validarCabecera() {
        if (cmbProveedor.getValue() == null || texto(txtDua.getText()).isBlank() || dpFecha.getValue() == null) {
            mostrarError("Cabecera incompleta", "Ingresa proveedor, DUA y fecha.");
            return false;
        }
        if (decimal(txtTipoCambio.getText()).compareTo(BigDecimal.ZERO) <= 0) {
            mostrarError("Tipo de cambio inválido", "Ingresa un tipo de cambio válido.");
            return false;
        }
        return true;
    }

    private void cargarProveedores() {
        proveedores.setAll(jdbcTemplate.query(com.ejemplo.crudpersona.service.DBQPSqlDialect.sql(jdbcTemplate, """
                SELECT id, COALESCE(ruc, '') ruc, razon_social
                FROM proveedores
                WHERE estado = 'ACTIVO'
                ORDER BY razon_social
                """), (rs, rowNum) -> new ProveedorCompra(
                rs.getLong("id"),
                texto(rs.getString("ruc")),
                texto(rs.getString("razon_social"))
        )));
        cmbProveedor.setItems(proveedores);
        if (!proveedores.isEmpty()) {
            cmbProveedor.getSelectionModel().selectFirst();
        }
    }

    private void recalcularTotales() {
        Totales t = totales();
        lblTituloTotales.setText(lineas.stream().anyMatch(l->l.fobUsd()==null||l.gastosUsd()==null||l.cantidad()<=0)?"Totales parciales":"Totales");
        lblFobUsd.setText("US$ " + money(t.fob()));
        lblGastosUsd.setText("US$ " + money(t.gastos()));
        lblLandedUsd.setText("US$ " + money(t.landedUsd()));
        lblLandedPen.setText("S/ " + money(t.landedPen()));
        lblUnidades.setText(String.valueOf(t.unidades()));
    }

    private Totales totales() {
        BigDecimal fob = BigDecimal.ZERO;
        BigDecimal gastos = BigDecimal.ZERO;
        BigDecimal landedUsd = BigDecimal.ZERO;
        BigDecimal landedPen = BigDecimal.ZERO;
        int unidades = 0;
        for (CompraLinea linea : lineas) {
            fob = fob.add(decimal(linea.fobUsd()).multiply(BigDecimal.valueOf(linea.cantidad())));
            gastos = gastos.add(decimal(linea.gastosUsd()).multiply(BigDecimal.valueOf(linea.cantidad())));
            landedUsd = landedUsd.add(decimal(linea.landedUsd()).multiply(BigDecimal.valueOf(linea.cantidad())));
            landedPen = landedPen.add(decimal(linea.landedUsd()).multiply(decimal(txtTipoCambio.getText())).multiply(BigDecimal.valueOf(linea.cantidad())));
            unidades += linea.cantidad();
        }
        return new Totales(fob, gastos, landedUsd, landedPen, unidades);
    }

    private BigDecimal decimal(String value) {
        try {
            return new BigDecimal(value.trim().replace(',', '.'));
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private BigDecimal decimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private int entero(String value, int fallback) {
        try {
            return Integer.parseInt(value.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private int entero(Integer value) {
        return value == null ? 0 : value;
    }

    private String texto(String value) {
        return value == null ? "" : value.trim();
    }

    private String money(BigDecimal value) {
        return String.format("%.2f", decimal(value));
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

    public record CompraLinea(String sku, String descripcion, String codigoBarras, String lote, String ubicacion,
                              int cantidad, BigDecimal fobUsd, BigDecimal gastosUsd, BigDecimal landedUsd,
                              BigDecimal landedPen, BigDecimal margen, BigDecimal precioSugeridoPen) {
    }

    public record ProveedorCompra(Long id, String ruc, String razonSocial) {
        @Override
        public String toString() {
            return ruc == null || ruc.isBlank() ? razonSocial : razonSocial + " · " + ruc;
        }
    }

    private record Totales(BigDecimal fob, BigDecimal gastos, BigDecimal landedUsd, BigDecimal landedPen, int unidades) {
    }
}
