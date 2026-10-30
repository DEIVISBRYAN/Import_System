package com.ejemplo.crudpersona.ui;

import com.ejemplo.crudpersona.service.DBQPBorradoresDuaServiceI;
import com.ejemplo.crudpersona.service.DBQPBorradoresDuaServiceI.*;
import com.ejemplo.crudpersona.service.ERQZCompraServiceI.Linea;
import com.ejemplo.crudpersona.service.DBQPDocumentosDuaServiceI;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import java.util.function.Consumer;
import java.util.function.Function;
import static com.ejemplo.crudpersona.ui.ArchivosDuaPane.*;

public final class ImportacionesDuaDialog {
    private ImportacionesDuaDialog() {}
    public static void mostrar(Window owner, DBQPBorradoresDuaServiceI servicio, DBQPDocumentosDuaServiceI documentos,
                               Long seleccionado, Consumer<Detalle> editar, Runnable anteriores) {
        Dialog<Void> dialog=new Dialog<>(); dialog.setTitle("Importaciones DUA · Pendientes y aprobadas"); dialog.initOwner(owner); dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.getDialogPane().getStyleClass().addAll("app-dialog","wide-dialog"); dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
        ((Button)dialog.getDialogPane().lookupButton(ButtonType.CLOSE)).setText("Cerrar");
        Label titulo=new Label("Pendientes y aprobadas"); titulo.getStyleClass().add("modal-title");
        TextField buscar=new TextField(); buscar.setPromptText("DUA o proveedor"); buscar.setPrefWidth(330); buscar.setId("buscarImportacionesDua");
        ComboBox<String> estado=new ComboBox<>(); estado.getItems().addAll("Todos","Pendientes","Aprobadas"); estado.setValue("Todos");
        Button actualizar=icono("fth-refresh-cw","Actualizar lista"), historial=boton("Historial anterior","fth-clock");
        var filtros=new ResponsiveFlowPane(); filtros.getChildren().addAll(buscar,estado,actualizar,historial);
        TableView<Resumen> tabla=tabla("importacionesDua",215);
        columna(tabla,"DUA",175,Resumen::dua); columna(tabla,"Proveedor",185,Resumen::proveedor);
        columna(tabla,"Fecha",95,r->r.fecha().toString()); columna(tabla,"Estado",115,r->r.estado().equals("PENDIENTE")?"Pendiente":"Aprobada / stock");
        columna(tabla,"Líneas",60,r->""+r.productos()); columna(tabla,"Unidades",75,r->""+r.unidades());
        columna(tabla,"Total PEN",100,r->dinero(r.totalPen()));
        ExportarMenu exportar = new ExportarMenu();
        exportar.setId("exportarImportacionesDua");
        exportar.setReporte(() -> new com.ejemplo.crudpersona.service.ERQZReporteTabular("Importaciones DUA", "Pendientes y aprobadas según los filtros de la lista. Solo las aprobadas ingresaron al stock.",
                java.util.List.of("DUA", "Proveedor", "Fecha", "Estado", "Líneas", "Unidades", "Total PEN"),
                tabla.getItems().stream().map(r -> com.ejemplo.crudpersona.service.ERQZReporteTabular.fila(r.dua(), r.proveedor(), r.fecha(), r.estado(), r.productos(), r.unidades(), r.totalPen())).toList()));
        filtros.getChildren().add(exportar);
        TableView<Linea> detalle=tabla("detalleImportacionDua",180);
        columna(detalle,"SKU",100,Linea::sku); columna(detalle,"Producto",245,Linea::descripcion); columna(detalle,"Lote",100,Linea::lote);
        columna(detalle,"Ubicación",90,Linea::ubicacion); columna(detalle,"Cantidad",80,l->""+l.cantidad());
        columna(detalle,"FOB USD",85,l->dinero(l.fobUsd())); columna(detalle,"Gastos USD",85,l->dinero(l.gastosUsd()));
        Label resumen=new Label(); resumen.setWrapText(true); resumen.getStyleClass().add("order-summary");
        Label error=new Label(); error.setId("errorImportacionDua"); error.setWrapText(true); error.getStyleClass().add("validation-error");
        Button modificar=boton("Editar","fth-edit-2"), ver=boton("Ver documentos","fth-eye"), eliminar=boton("Eliminar pendiente","fth-trash-2"), aprobar=boton("Aprobar e ingresar a stock","fth-check-circle");
        modificar.setId("editarImportacionDua"); ver.setId("verDocumentosImportacionDua"); eliminar.setId("eliminarImportacionDua"); aprobar.setId("aprobarImportacionDua");
        aprobar.getStyleClass().setAll("btn-primario");
        Runnable seleccionar=()->{
            var r=tabla.getSelectionModel().getSelectedItem(); detalle.getItems().clear(); resumen.setText("");
            modificar.setDisable(r==null || !r.estado().equals("PENDIENTE")); eliminar.setDisable(modificar.isDisabled());
            aprobar.setDisable(modificar.isDisabled() || !servicio.puedeAprobar()); ver.setDisable(r==null || r.documentoId()==null);
            boolean aprobada=r!=null && r.estado().equals("APROBADA");
            aprobar.setText(aprobada?"Aprobada y en stock":"Aprobar e ingresar a stock");
            aprobar.getStyleClass().setAll(aprobada?"btn-secundario":"btn-primario");
            aprobar.setTooltip(new Tooltip(servicio.puedeAprobar()?"Ingresa todas las cantidades una sola vez":"Solo un administrador o gerente puede aprobar"));
            if(r!=null) {
                var d=servicio.obtener(r.id()); detalle.getItems().setAll(d.lineas());
                resumen.setText(r.dua()+"\n"+r.unidades()+" unidades · "+r.productos()+" líneas · "+(r.estado().equals("PENDIENTE")?"Sin ingreso a stock · Registró: "+d.creadoPor():"Compra #"+r.compraId()+" · Aprobó: "+d.aprobadoPor()));
            }
        };
        Runnable cargar=()->{
            try {
                error.setText(""); var r=tabla.getSelectionModel().getSelectedItem(); Long id=r==null?seleccionado:r.id();
                tabla.getItems().setAll(servicio.listar(buscar.getText()).stream().filter(x->estado.getValue().equals("Todos")
                        || x.estado().equals(estado.getValue().equals("Pendientes")?"PENDIENTE":"APROBADA")).toList());
                tabla.getItems().stream().filter(x->x.id().equals(id)).findFirst().ifPresent(tabla.getSelectionModel()::select);
                if(tabla.getSelectionModel().isEmpty() && !tabla.getItems().isEmpty()) tabla.getSelectionModel().selectFirst();
                seleccionar.run();
            } catch(RuntimeException ex) { error.setText(ex.getMessage()); }
        };
        tabla.getSelectionModel().selectedItemProperty().addListener((o,a,b)->{try{seleccionar.run();}catch(RuntimeException ex){error.setText(ex.getMessage());}});
        actualizar.setOnAction(e->cargar.run()); buscar.setOnAction(e->cargar.run()); estado.setOnAction(e->cargar.run()); historial.setOnAction(e->anteriores.run());
        modificar.setOnAction(e->{
            try {var r=tabla.getSelectionModel().getSelectedItem();if(r==null)return;var d=servicio.obtener(r.id());
                if(!d.resumen().estado().equals("PENDIENTE"))throw new IllegalStateException("La DUA ya fue aprobada.");
                editar.accept(d);dialog.close();
            }catch(RuntimeException ex){cargar.run();error.setText(ex.getMessage());}
        });
        ver.setOnAction(e->{var r=tabla.getSelectionModel().getSelectedItem();if(r!=null){DocumentosDuaDialog.mostrar(dialog.getDialogPane().getScene().getWindow(),documentos,r.documentoId());cargar.run();}});
        eliminar.setOnAction(e->{
            var r=tabla.getSelectionModel().getSelectedItem(); if(r==null)return;
            if(!confirmar(dialog,"¿Eliminar la DUA pendiente?",r.dua()+"\nSe eliminarán sus líneas y documentos guardados. El stock no cambiará."))return;
            try {servicio.eliminar(r.id(),r.version());cargar.run();}catch(RuntimeException ex){cargar.run();error.setText(ex.getMessage());}
        });
        aprobar.setOnAction(e->{
            var r=tabla.getSelectionModel().getSelectedItem();if(r==null)return;
            if(!confirmar(dialog,"¿Aprobar e ingresar a stock?",r.dua()+"\n"+r.productos()+" líneas · "+r.unidades()+" unidades\nSe crearán los lotes y se actualizarán costos y precios. No podrá editarse ni eliminarse después."))return;
            try {Long compra=servicio.aprobar(r.id(),r.version());cargar.run();resumen.setText("Aprobada · Compra #"+compra+" · Stock actualizado correctamente.");}
            catch(RuntimeException ex){cargar.run();error.setText(ex.getMessage());}
        });
        var acciones=new ResponsiveFlowPane();acciones.getChildren().addAll(ver,modificar,eliminar,aprobar);
        VBox content=new VBox(12,titulo,filtros,tabla,acciones,resumen,new Separator(),detalle,error);content.getStyleClass().add("custom-modal");
        dialog.getDialogPane().setContent(content);cargar.run();dialog.showAndWait();
    }
    private static boolean confirmar(Dialog<?> owner,String titulo,String mensaje) {
        Alert alert=new Alert(Alert.AlertType.CONFIRMATION,mensaje,ButtonType.OK,ButtonType.CANCEL);alert.initOwner(owner.getDialogPane().getScene().getWindow());alert.setHeaderText(titulo);
        return alert.showAndWait().orElse(ButtonType.CANCEL)==ButtonType.OK;
    }
    private static String dinero(java.math.BigDecimal v){return v.setScale(2,java.math.RoundingMode.HALF_UP).toPlainString();}
    private static <T> TableView<T> tabla(String id,int alto) {
        TableView<T> t=new TableView<>();t.setId(id);t.setPrefHeight(alto);t.setMinHeight(100);t.setFixedCellSize(43);
        t.getStyleClass().addAll("tabla","fit-columns","orders-table");t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);t.setPlaceholder(new Label("Sin registros"));return t;
    }
    private static <T> void columna(TableView<T> tabla,String nombre,int ancho,Function<T,String> texto) {
        TableColumn<T,String> c=new TableColumn<>(nombre);c.setPrefWidth(ancho);c.setMinWidth(55);c.setCellValueFactory(d->new SimpleStringProperty(texto.apply(d.getValue())));
        c.setCellFactory(col->new TableCell<>(){@Override protected void updateItem(String valor,boolean vacio){super.updateItem(valor,vacio);setText(vacio?null:valor);setTooltip(vacio?null:new Tooltip(valor));}});tabla.getColumns().add(c);
    }
}
