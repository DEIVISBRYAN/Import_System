package com.ejemplo.crudpersona.ui;

import com.ejemplo.crudpersona.service.RPLCReposicionServiceI;
import com.ejemplo.crudpersona.service.ERQZCompraServiceI;
import com.ejemplo.crudpersona.service.DBQPTipoCambioServiceI;
import com.ejemplo.crudpersona.service.RPLCReposicionServiceI.*;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.function.Function;

public final class PedidosCompraDialog {
    private PedidosCompraDialog() {}
    public record Borrador(Long productoId, Proveedor proveedor, String producto, int sugerida, BigDecimal unitarioUsd, BigDecimal unitarioPen) {}
    private static class Fila {
        final Borrador dato;
        final TextField cantidad = new TextField();
        Fila(Borrador dato) { this.dato=dato; cantidad.setText(""+dato.sugerida()); cantidad.setId("cantidadPedido-"+dato.productoId()); cantidad.setPrefWidth(76); cantidad.setMaxWidth(90); }
        int cantidad() {
            try { int n=Integer.parseInt(cantidad.getText().trim()); if(n>0 && n<=1_000_000) return n; }
            catch(NumberFormatException ignored) {}
            throw new IllegalArgumentException("Cantidad de " + dato.producto()+": ingresa un entero de 1 a 1000000.");
        }
        BigDecimal total(boolean usd) { return (usd?dato.unitarioUsd():dato.unitarioPen()).multiply(BigDecimal.valueOf(cantidad())).setScale(2,RoundingMode.HALF_UP); }
    }
    public static boolean revisar(Window owner, RPLCReposicionServiceI service, ERQZCompraServiceI compras, DBQPTipoCambioServiceI cambios, List<Borrador> propuestas, int sinProveedor) {
        Dialog<List<Long>> dialog=dialogo(owner,"Revisar pedidos");
        var filas=FXCollections.observableArrayList(propuestas.stream().sorted(Comparator.comparing(b -> b.proveedor().nombre())).map(Fila::new).toList());
        TableView<Fila> tabla=tabla("revisionPedidos",290);
        tabla.setItems(filas); tabla.setFixedCellSize(64);
        columna(tabla,"Producto",200,f->f.dato.producto());
        columna(tabla,"Proveedor",165,f->f.dato.proveedor().nombre());
        columna(tabla,"Sugerida",75,f->""+f.dato.sugerida());
        TableColumn<Fila,String> cantidad=new TableColumn<>("Pedir"); cantidad.setPrefWidth(100); cantidad.setMinWidth(86);
        cantidad.setCellValueFactory(d -> new SimpleStringProperty(""+d.getValue().dato.productoId()));
        cantidad.setCellFactory(c -> new TableCell<>() {
            @Override protected void updateItem(String v,boolean empty) {
                super.updateItem(v,empty); setText(null); var fila=getTableRow().getItem(); setGraphic(empty||fila==null?null:fila.cantidad);
            }
        }); tabla.getColumns().add(cantidad);
        for (boolean usd : new boolean[]{true,false}) {
            var total=new TableColumn<Fila,String>(usd?"Total USD":"Total PEN"); total.setPrefWidth(110); total.setMinWidth(70);
            total.setCellValueFactory(d->javafx.beans.binding.Bindings.createStringBinding(()->totalTexto(d.getValue(),usd),d.getValue().cantidad.textProperty()));
            tabla.getColumns().add(total);
        }
        TableColumn<Fila,String> quitar=new TableColumn<>(); quitar.setPrefWidth(50); quitar.setMinWidth(50); quitar.setMaxWidth(50);
        quitar.setCellValueFactory(d -> new SimpleStringProperty(""+d.getValue().dato.productoId()));
        quitar.setCellFactory(c -> new TableCell<>() {
            final Button btn=icono("fth-x","Quitar producto");
            { btn.setOnAction(e -> filas.remove(getTableRow().getItem())); }
            @Override protected void updateItem(String v,boolean empty) { super.updateItem(v,empty); setGraphic(empty?null:btn); }
        }); tabla.getColumns().add(quitar);
        Label resumen=new Label(); resumen.setWrapText(true); resumen.getStyleClass().add("order-summary");
        Label aviso=new Label(sinProveedor==0?"":"Sin incluir: "+sinProveedor+(sinProveedor==1?" producto sin proveedor.":" productos sin proveedor.")); aviso.setWrapText(true);
        aviso.setVisible(sinProveedor>0); aviso.setManaged(sinProveedor>0); aviso.getStyleClass().add("validation-error");
        TextArea notas=new TextArea(); notas.setId("observacionesPedido"); notas.setPromptText("Observaciones para el proveedor (opcional)"); notas.setWrapText(true); notas.setPrefRowCount(2);
        Label error=error();
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,ButtonType.OK);
        Button confirmar=(Button)dialog.getDialogPane().lookupButton(ButtonType.OK); confirmar.setId("confirmarPedidos"); confirmar.setText("Confirmar pedidos"); confirmar.getStyleClass().add("btn-primario");
        ((Button)dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Cancelar");
        Runnable actualizar=() -> {
            long proveedores=filas.stream().map(f->f.dato.proveedor().id()).distinct().count();
            confirmar.setDisable(filas.isEmpty());
            try {
                var usd=filas.stream().map(f->f.total(true)).reduce(BigDecimal.ZERO,BigDecimal::add);
                var pen=filas.stream().map(f->f.total(false)).reduce(BigDecimal.ZERO,BigDecimal::add);
                resumen.setText(proveedores+(proveedores==1?" pedido · ":" pedidos · ")+filas.size()+(filas.size()==1?" producto":" productos")+"   |   USD "+dinero(usd)+" / S/ "+dinero(pen));
            } catch(IllegalArgumentException e) { resumen.setText("Revisa las cantidades."); }
        };
        filas.forEach(f->f.cantidad.textProperty().addListener((o,a,b)->actualizar.run()));
        filas.addListener((javafx.collections.ListChangeListener<Fila>)c->actualizar.run()); actualizar.run();
        VBox content=new VBox(14,titulo("Revisar pedidos"),resumen,aviso,new Separator(),tabla,notas,error); content.getStyleClass().add("custom-modal");
        dialog.getDialogPane().setContent(content);
        var resultado=new ArrayList<Long>();
        confirmar.addEventFilter(javafx.event.ActionEvent.ACTION,e -> {
            try {
                List<LineaRevisada> lineas=filas.stream().map(f->new LineaRevisada(f.dato.productoId(),f.dato.proveedor().id(),f.cantidad(),f.dato.unitarioUsd(),f.dato.unitarioPen())).toList();
                resultado.addAll(service.confirmarPedidos(lineas,notas.getText()));
            } catch(RuntimeException ex) { mostrarError(error,ex.getMessage()); e.consume(); }
        });
        dialog.setResultConverter(b->b==ButtonType.OK?List.copyOf(resultado):null);
        var guardados=dialog.showAndWait();
        guardados.ifPresent(ids->historial(owner,service,compras,cambios,ids.get(0)));
        return guardados.isPresent();
    }
    private static String totalTexto(Fila f,boolean usd) { try { return dinero(f.total(usd)); } catch(IllegalArgumentException e) { return "-"; } }

    public static void historial(Window owner, RPLCReposicionServiceI service, ERQZCompraServiceI compras, DBQPTipoCambioServiceI cambios, Long seleccionado) {
        Dialog<Void> dialog=dialogo(owner,"Pedidos a proveedores"); dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        ((Button)dialog.getDialogPane().lookupButton(ButtonType.CLOSE)).setText("Cerrar");
        TableView<ResumenPedido> tabla=tabla("historialPedidos",210); tabla.setFixedCellSize(46);
        columna(tabla,"Pedido",110,ResumenPedido::numero); columna(tabla,"Proveedor",220,ResumenPedido::proveedor);
        columna(tabla,"Fecha",155,ResumenPedido::fecha); columna(tabla,"Estado",110,ResumenPedido::estado);
        columna(tabla,"Productos",80,p->""+p.productos()); columna(tabla,"Est. USD",90,p->dinero(p.totalUsd())); columna(tabla,"Est. PEN",90,p->dinero(p.totalPen()));
        TableView<com.ejemplo.crudpersona.model.PedidoCompraDocumento.Linea> detalle=tabla("detallePedido",160); detalle.setFixedCellSize(46);
        columna(detalle,"Producto",280,l->l.producto()); columna(detalle,"Cantidad",85,l->""+l.cantidad());
        columna(detalle,"Unit. USD",100,l->l.unitarioUsd().stripTrailingZeros().toPlainString()); columna(detalle,"Total USD",100,l->dinero(l.totalUsd()));
        columna(detalle,"Unit. PEN",100,l->l.unitarioPen().stripTrailingZeros().toPlainString()); columna(detalle,"Total PEN",100,l->dinero(l.totalPen()));
        Label notas=new Label(); notas.setWrapText(true); notas.getStyleClass().add("text-subtle");
        Label recepcion=new Label(); recepcion.setWrapText(true); recepcion.getStyleClass().add("text-subtle"); recepcion.setId("pedidoRecepcion");
        Label error=error(); Label estado=new Label(); estado.setWrapText(true); estado.getStyleClass().add("text-subtle");
        Button pdf=boton("Descargar PDF","fth-file-text"); pdf.setId("descargarPedidoPdf");
        Button excel=boton("Descargar Excel","fth-download"); excel.setId("descargarPedidoExcel");
        Button actualizar=icono("fth-refresh-cw","Actualizar pedidos");
        Button recibir=boton("Recibir pedido completo","fth-package"); recibir.setId("recibirPedidoCompleto");
        recibir.disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(()->{
            var p=tabla.getSelectionModel().getSelectedItem(); return p==null || !"PENDIENTE".equals(p.estado());
        },tabla.getSelectionModel().selectedItemProperty()));
        pdf.disableProperty().bind(tabla.getSelectionModel().selectedItemProperty().isNull()); excel.disableProperty().bind(pdf.disableProperty());
        tabla.getSelectionModel().selectedItemProperty().addListener((o,a,p)-> {
            try {
                detalle.getItems().clear(); notas.setText(""); recepcion.setText(p==null?"":p.recepcion());
                if(p!=null) { var d=service.documentoPedido(p.id()); detalle.getItems().setAll(d.lineas()); notas.setText(d.observaciones().isBlank()?"":"Observaciones: "+d.observaciones()); }
            } catch(RuntimeException ex) { mostrarError(error,ex.getMessage()); }
        });
        Runnable cargar=()-> {
            try {
                var p=tabla.getSelectionModel().getSelectedItem(); Long id=p==null?seleccionado:p.id();
                tabla.getItems().setAll(service.listarPedidos());
                tabla.getItems().stream().filter(r->r.id().equals(id)).findFirst().ifPresent(tabla.getSelectionModel()::select);
                if(tabla.getSelectionModel().isEmpty()&&!tabla.getItems().isEmpty()) tabla.getSelectionModel().selectFirst();
            } catch(RuntimeException ex) { mostrarError(error,ex.getMessage()); }
        }; actualizar.setOnAction(e->cargar.run());
        recibir.setOnAction(e->{
            var p=tabla.getSelectionModel().getSelectedItem(); if(p==null) return;
            try {
                if(RecepcionPedidoDialog.mostrar(dialog.getDialogPane().getScene().getWindow(),compras,p.id(),cambios.actual())) {
                    cargar.run(); estado.setText("Recepción registrada. Stock y lotes actualizados.");
                }
            } catch(RuntimeException ex) {mostrarError(error,ex.getMessage()); cargar.run();}
        });
        java.util.function.Consumer<String> descargar=formato-> {
            var p=tabla.getSelectionModel().getSelectedItem(); if(p==null) return;
            try {
                error.setVisible(false); error.setManaged(false); estado.setText("");
                boolean guardado=ArchivoDescarga.guardar(dialog.getDialogPane().getScene().getWindow(),p.numero(),formato,
                        ()->formato.equals("pdf")?service.pdfPedido(p.id()):service.excelPedido(p.id()));
                estado.setText(guardado?"Archivo guardado.":"Descarga cancelada.");
            } catch(Exception ex) { mostrarError(error,"No se pudo descargar el archivo: "+ex.getMessage()); }
        };
        pdf.setOnAction(e->descargar.accept("pdf")); excel.setOnAction(e->descargar.accept("xlsx"));
        var acciones=new ResponsiveFlowPane(); acciones.setHgap(10); acciones.setVgap(10); acciones.getChildren().addAll(actualizar,recibir,pdf,excel);
        Label detalleTitulo=new Label("Detalle del pedido · Costos estimados"); detalleTitulo.getStyleClass().add("text-subtle");
        VBox content=new VBox(12,titulo("Pedidos a proveedores"),tabla,acciones,estado,recepcion,new Separator(),detalleTitulo,detalle,notas,error);
        content.getStyleClass().add("custom-modal"); dialog.getDialogPane().setContent(content); cargar.run(); dialog.showAndWait();
    }
    private static <T> TableView<T> tabla(String id,int height) {
        var table=new TableView<T>(); table.setId(id); table.setPrefHeight(height); table.setMinHeight(height);
        table.getStyleClass().addAll("tabla","fit-columns","orders-table"); table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setPlaceholder(new Label("Sin productos o pedidos.")); return table;
    }
    private static <T> void columna(TableView<T> table,String nombre,int ancho,Function<T,String> valor) {
        var col=new TableColumn<T,String>(nombre); col.setPrefWidth(ancho); col.setMinWidth(58);
        col.setCellValueFactory(d->new SimpleStringProperty(valor.apply(d.getValue())));
        col.setCellFactory(c->new TableCell<>() {
            @Override protected void updateItem(String value,boolean empty) { super.updateItem(value,empty); setText(empty?null:value); setTooltip(empty||value==null?null:new Tooltip(value)); }
        }); table.getColumns().add(col);
    }
    private static <T> Dialog<T> dialogo(Window owner,String titulo) {
        var dialog=new Dialog<T>(); dialog.setTitle(titulo); dialog.initOwner(owner); dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.getDialogPane().getStyleClass().addAll("app-dialog","wide-dialog"); return dialog;
    }
    private static Label titulo(String s) { var l=new Label(s); l.getStyleClass().add("modal-title"); return l; }
    private static Label error() { var l=new Label(); l.setId("errorPedido"); l.setWrapText(true); l.getStyleClass().add("validation-error"); l.setVisible(false); l.setManaged(false); return l; }
    private static void mostrarError(Label l,String mensaje) { l.setText(mensaje); l.setManaged(true); l.setVisible(true); }
    private static String dinero(BigDecimal v) { return String.format(Locale.ROOT,"%.2f",v); }
    private static Button boton(String text,String icon) { var b=new Button(text,new FontIcon(icon)); b.getStyleClass().add("btn-secundario"); return b; }
    private static Button icono(String icon,String tooltip) { var b=new Button(null,new FontIcon(icon)); b.getStyleClass().add("btn-icono"); b.setTooltip(new Tooltip(tooltip)); return b; }
}
