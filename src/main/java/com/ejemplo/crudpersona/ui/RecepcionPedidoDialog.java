package com.ejemplo.crudpersona.ui;

import com.ejemplo.crudpersona.service.ERQZCompraServiceI;
import com.ejemplo.crudpersona.service.ERQZCompraServiceI.*;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

public final class RecepcionPedidoDialog {
    private RecepcionPedidoDialog() {}
    private static final class Fila {
        final ProductoRecepcion producto;
        final TextField lote,ubicacion,fob,gastos,margen;
        final VBox vista;
        Fila(ProductoRecepcion p) {
            producto=p; lote=campo("recepcionLote-"+p.detalleId(),"", "Lote"); ubicacion=campo("recepcionUbicacion-"+p.detalleId(),"","A-01-1");
            fob=campo("recepcionFob-"+p.detalleId(),"","0.00"); gastos=campo("recepcionGastos-"+p.detalleId(),"0","0.00");
            margen=campo("recepcionMargen-"+p.detalleId(),"35","0");
            Label nombre=new Label(p.producto()); nombre.setWrapText(true); nombre.setMinWidth(0); nombre.getStyleClass().add("supplier-picker-name");
            HBox.setHgrow(nombre,Priority.ALWAYS);
            Label cantidad=new Label(p.cantidad()+(p.cantidad()==1?" unidad":" unidades")); cantidad.getStyleClass().add("order-summary"); cantidad.setMinWidth(Region.USE_PREF_SIZE);
            HBox encabezado=new HBox(12,nombre,cantidad); encabezado.setAlignment(Pos.CENTER_LEFT);
            Label sku=new Label(p.sku()+" · Estimado USD "+p.estimadoUsd().stripTrailingZeros().toPlainString()+" / unidad"); sku.setWrapText(true); sku.getStyleClass().add("text-subtle");
            GridPane campos=new GridPane(); campos.setHgap(10); campos.setVgap(8);
            for(int i=0;i<5;i++) { var c=new ColumnConstraints(); c.setPercentWidth(20); c.setMinWidth(0); campos.getColumnConstraints().add(c); }
            campos.add(etiqueta("Lote *",lote),0,0); campos.add(etiqueta("Ubicación *",ubicacion),1,0); campos.add(etiqueta("FOB unit. USD *",fob),2,0);
            campos.add(etiqueta("Gastos unit. USD",gastos),3,0); campos.add(etiqueta("Margen %",margen),4,0);
            vista=new VBox(8,encabezado,sku,campos); vista.setMinWidth(0); vista.getStyleClass().add("receipt-line");
        }
        LineaRecepcion linea() {
            if(lote.getText().isBlank() || ubicacion.getText().isBlank()) throw new IllegalArgumentException("Completa lote y ubicación de "+producto.producto()+".");
            return new LineaRecepcion(producto.detalleId(),producto.cantidad(),lote.getText().trim(),ubicacion.getText().trim(),
                    numero(fob,"FOB de "+producto.producto()),numero(gastos,"gastos de "+producto.producto()),numero(margen,"margen de "+producto.producto()));
        }
    }
    public static boolean mostrar(Window owner,ERQZCompraServiceI compras,Long pedidoId,BigDecimal tipoCambio) {
        Recepcion pedido=compras.prepararRecepcion(pedidoId);
        Dialog<Long> dialog=new Dialog<>(); dialog.setTitle("Recibir pedido completo"); dialog.initOwner(owner); dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.getDialogPane().getStyleClass().addAll("app-dialog","wide-dialog");
        Label titulo=new Label("Recibir pedido completo · "+pedido.numero()); titulo.setWrapText(true); titulo.getStyleClass().add("modal-title");
        Label proveedor=new Label(pedido.proveedor()); proveedor.setWrapText(true); proveedor.getStyleClass().add("text-subtle");
        TextField dua=campo("recepcionDua","","N° DUA"); TextField tc=campo("recepcionTipoCambio",tipoCambio.toPlainString(),"0.00");
        DatePicker fecha=new DatePicker(LocalDate.now()); fecha.setId("recepcionFecha"); fecha.setEditable(false); fecha.setMaxWidth(Double.MAX_VALUE); fecha.setMinWidth(0);
        GridPane cabecera=new GridPane(); cabecera.setHgap(12);
        for(int i=0;i<3;i++) {var c=new ColumnConstraints(); c.setPercentWidth(i==0?40:30); c.setMinWidth(0); cabecera.getColumnConstraints().add(c);}
        cabecera.add(etiqueta("N° DUA *",dua),0,0); cabecera.add(etiqueta("Fecha de ingreso *",fecha),1,0); cabecera.add(etiqueta("Tipo de cambio *",tc),2,0);
        List<Fila> filas=pedido.productos().stream().map(Fila::new).toList();
        ListView<Fila> lista=new ListView<>(javafx.collections.FXCollections.observableArrayList(filas)); lista.setId("recepcionProductos");
        lista.setPrefHeight(340); lista.setMinHeight(180); lista.getStyleClass().add("receipt-lines");
        lista.setCellFactory(view->new ListCell<>() {
            @Override protected void updateItem(Fila fila,boolean empty) {
                super.updateItem(fila,empty); setText(null); setGraphic(empty?null:fila.vista);
                if(!empty) { fila.vista.prefWidthProperty().unbind(); fila.vista.prefWidthProperty().bind(lista.widthProperty().subtract(40)); }
            }
        });
        Label totales=new Label(); totales.setWrapText(true); totales.getStyleClass().add("order-summary"); totales.setId("recepcionTotales");
        Runnable calcular=()->{
            try {
                BigDecimal usd=BigDecimal.ZERO;
                for(var f:filas) usd=usd.add(numero(f.fob,"FOB").add(numero(f.gastos,"gastos")).setScale(4,RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(f.producto.cantidad())));
                totales.setText(pedido.productos().size()+(pedido.productos().size()==1?" producto · ":" productos · ")+pedido.productos().stream().mapToLong(ProductoRecepcion::cantidad).sum()+
                        " unidades   |   USD "+dinero(usd)+" / S/ "+dinero(usd.multiply(numero(tc,"tipo de cambio"))));
            } catch(IllegalArgumentException e) {totales.setText("Costos de recepción pendientes");}
        };
        filas.forEach(f->{f.fob.textProperty().addListener((o,a,b)->calcular.run()); f.gastos.textProperty().addListener((o,a,b)->calcular.run());});
        tc.textProperty().addListener((o,a,b)->calcular.run()); calcular.run();
        Label error=new Label(); error.setId("errorRecepcion"); error.setWrapText(true); error.getStyleClass().add("validation-error"); error.setVisible(false); error.setManaged(false);
        VBox contenido=new VBox(14,titulo,proveedor,new Separator(),cabecera,lista,totales,error); contenido.getStyleClass().add("custom-modal");
        dialog.getDialogPane().setContent(contenido); dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,ButtonType.OK);
        ((Button)dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Cancelar");
        Button registrar=(Button)dialog.getDialogPane().lookupButton(ButtonType.OK); registrar.setText("Registrar recepción completa"); registrar.setId("registrarRecepcion"); registrar.getStyleClass().add("btn-primario");
        Long[] compraId={null};
        registrar.addEventFilter(javafx.event.ActionEvent.ACTION,e->{
            try {compraId[0]=compras.recibirPedido(pedidoId,dua.getText(),fecha.getValue(),numero(tc,"tipo de cambio"),filas.stream().map(Fila::linea).toList());}
            catch(RuntimeException ex) {error.setText(ex.getMessage()); error.setManaged(true); error.setVisible(true); e.consume();}
        });
        dialog.setResultConverter(b->b==ButtonType.OK?compraId[0]:null); return dialog.showAndWait().isPresent();
    }
    private static TextField campo(String id,String value,String prompt) {var f=new TextField(value); f.setId(id); f.setPromptText(prompt); f.setMinWidth(0); f.setMaxWidth(Double.MAX_VALUE); return f;}
    private static VBox etiqueta(String texto,Control campo) {var l=new Label(texto); l.setWrapText(true); l.getStyleClass().add("dialog-form-label"); return new VBox(6,l,campo);}
    private static BigDecimal numero(TextField f,String nombre) {
        try {var n=new BigDecimal(f.getText().trim().replace(',','.')); if(n.signum()>=0) return n;}
        catch(NumberFormatException ignored) {}
        throw new IllegalArgumentException("Ingresa un valor válido, mayor o igual a cero, para "+nombre+".");
    }
    private static String dinero(BigDecimal v) {return String.format(Locale.ROOT,"%.2f",v);}
}
