package com.ejemplo.crudpersona.controller;

import com.ejemplo.crudpersona.service.*;
import com.ejemplo.crudpersona.model.*;
import com.ejemplo.crudpersona.ui.*;
import javafx.fxml.FXML;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import org.springframework.stereotype.Component;
import java.util.*;
import java.time.LocalDate;
import java.nio.file.Files;
import java.util.function.Consumer;

@Component
public class ERQZSunarpController {
    final DBQPSunarpServiceI service;
    @FXML private TextField txtBuscarMoto;
    @FXML private Label lblResumenMotos;
    @FXML private Label lblAdvertenciaSunarp;
    @FXML private Button btnRegistrarMoto;
    @FXML private TableView<Map<String,Object>> tablaMotos;
    @FXML private TableColumn<Map<String,Object>,String> colVinMoto,colDescripcionMoto,colDuaMoto,colPlacaMoto,colTitularMoto;
    @FXML private TableColumn<Map<String,Object>,Void> colAccionesMoto;
    @FXML private ExportarMenu exportarMotos;
    public ERQZSunarpController(DBQPSunarpServiceI service){this.service=service;}
    boolean editar(){return service.puedeEditar();}
    @FXML public void initialize(){
        lblAdvertenciaSunarp.setMinHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        bind(colVinMoto,"vin");colDescripcionMoto.setCellValueFactory(c->new SimpleStringProperty(s(c.getValue(),"marca")+" "+s(c.getValue(),"modelo")));bind(colDuaMoto,"numero_dua");bind(colPlacaMoto,"placa");bind(colTitularMoto,"titular_nombre");
        colAccionesMoto.setCellFactory(c->new TableCell<>(){final Button view=icono("fth-folder","Ver expedientes"),edit=icono("fth-edit-2","Corregir datos de la moto");final HBox actions=new HBox(6,view,edit);{view.setOnAction(e->safe(()->mostrarMoto(getTableRow().getItem())));edit.setDisable(!editar());edit.setOnAction(e->safe(()->formMoto(getTableRow().getItem())));}protected void updateItem(Void v,boolean empty){super.updateItem(v,empty);setGraphic(empty?null:actions);}});
        btnRegistrarMoto.setDisable(!editar());exportarMotos.setReporte(()->reporte("Motos importadas - seguimiento interno",tablaMotos.getItems(),List.of("vin","motor","marca","modelo","anio","numero_dua","placa","partida","titular_documento","titular_nombre")));
        onBuscar();
    }
    static void bind(TableColumn<Map<String,Object>,String> col,String key){col.setCellValueFactory(c->new SimpleStringProperty(s(c.getValue(),key)));}
    static String s(Map<String,Object> map,String key){return map.get(key)==null?"":map.get(key).toString();}
    static long id(Map<String,Object> map){return ((Number)map.get("id")).longValue();}
    @FXML public void onBuscar(){safe(()->{tablaMotos.setItems(FXCollections.observableArrayList(service.motos(txtBuscarMoto.getText())));lblResumenMotos.setText(tablaMotos.getItems().size()+" motos registradas");});}
    void safe(Runnable r){try{r.run();}catch(Exception ex){var a=new Alert(Alert.AlertType.ERROR,ex.getMessage());a.initOwner(tablaMotos.getScene()==null?null:tablaMotos.getScene().getWindow());a.setHeaderText("Revisa la operacion");a.showAndWait();}}
    Dialog<Void> dialog(String title,Window owner){var d=new Dialog<Void>();d.setTitle(title);d.initOwner(owner);d.initStyle(StageStyle.TRANSPARENT);d.getDialogPane().getStyleClass().add("app-dialog");d.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);d.setResizable(true);return d;}
    Window owner(){return tablaMotos.getScene().getWindow();}
    static VBox campo(String label,javafx.scene.Node input){return new VBox(5,new Label(label),input);}
    static TextField text(String value){return new TextField(value);}
    static Button icono(String icon,String title){var b=new Button(null,new org.kordamp.ikonli.javafx.FontIcon(icon));b.getStyleClass().add("btn-icono");b.setTooltip(new Tooltip(title));b.setAccessibleText(title);b.setMinSize(34,34);b.setPrefSize(34,34);b.setMaxSize(34,34);return b;}
    static GridPane grid(){var g=new GridPane();g.setHgap(16);g.setVgap(14);for(int i=0;i<2;i++){var c=new ColumnConstraints();c.setPercentWidth(50);c.setHgrow(Priority.ALWAYS);g.getColumnConstraints().add(c);}return g;}
    void form(String title,VBox body,Consumer<Label> save){var d=dialog(title,owner());d.getDialogPane().getButtonTypes().setAll(ButtonType.CANCEL,ButtonType.OK);var error=new Label();error.setWrapText(true);error.getStyleClass().add("validation-error");body.getChildren().add(error);body.getStyleClass().add("custom-modal");body.setPrefWidth(640);d.getDialogPane().setContent(body);var button=(Button)d.getDialogPane().lookupButton(ButtonType.OK);button.setText("Guardar");button.getStyleClass().add("btn-primario");button.addEventFilter(javafx.event.ActionEvent.ACTION,e->{try{save.accept(error);}catch(RuntimeException ex){error.setText(ex.getMessage());e.consume();}});d.showAndWait();}
    @FXML public void onRegistrar(){safe(()->formMoto(null));}
    void formMoto(Map<String,Object> existing){
        var purchases=service.compras();var select=new ComboBox<Map<String,Object>>(FXCollections.observableArrayList(purchases));select.setMaxWidth(Double.MAX_VALUE);select.setPromptText("Selecciona compra DUA existente (opcional si adjuntas imagen/PDF)");
        select.setConverter(new javafx.util.StringConverter<>(){public String toString(Map<String,Object> m){return m==null?"":s(m,"numero_dua")+" · "+s(m,"descripcion")+" · "+s(m,"registradas")+"/"+s(m,"cantidad")+" identificadas";}public Map<String,Object> fromString(String v){return null;}});
        if(existing!=null){select.getItems().stream().filter(r->Objects.equals(r.get("id"),existing.get("detalle_compra_id"))).findFirst().ifPresent(select::setValue);select.setDisable(true);}
        var values=existing==null?Map.<String,Object>of():existing;
        var duaManual=text(s(values,"numero_dua"));duaManual.setPromptText("Ej. 118-2026-10-009800");
        if(existing!=null)duaManual.setDisable(true);
        var vin=text(s(values,"vin"));var motor=text(s(values,"motor"));var brand=text(s(values,"marca"));var model=text(s(values,"modelo"));var year=text(existing==null?Integer.toString(LocalDate.now().getYear()):s(values,"anio"));var color=text(s(values,"color"));
        var archivoAdjunto=new ERQZArchivoDua[]{null};
        var lblArchivo=new Label("Sin archivo seleccionado (admite PDF, JPG o PNG)");lblArchivo.getStyleClass().add("text-subtle");lblArchivo.setWrapText(true);
        var btnCargar=new Button("Cargar imagen o PDF de DUA",new org.kordamp.ikonli.javafx.FontIcon("fth-upload"));btnCargar.getStyleClass().add("btn-secundario");
        var btnVerDoc=new Button("Ver documento",new org.kordamp.ikonli.javafx.FontIcon("fth-eye"));btnVerDoc.getStyleClass().add("btn-secundario");btnVerDoc.setDisable(true);
        btnCargar.setOnAction(e->safe(()->{
            var chooser=new FileChooser();chooser.setTitle("Seleccionar imagen o PDF de la DUA de la moto");
            chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Imagen o PDF (*.pdf, *.jpg, *.jpeg, *.png)","*.pdf","*.PDF","*.jpg","*.jpeg","*.png","*.JPG","*.JPEG","*.PNG"),
                new FileChooser.ExtensionFilter("Archivos PDF (*.pdf)","*.pdf","*.PDF"),
                new FileChooser.ExtensionFilter("Imágenes (*.jpg, *.jpeg, *.png)","*.jpg","*.jpeg","*.png","*.JPG","*.JPEG","*.PNG")
            );
            var f=chooser.showOpenDialog(owner());if(f==null)return;
            try{
                var arch=ERQZArchivoDua.leer(f.toPath());
                archivoAdjunto[0]=arch;btnVerDoc.setDisable(false);
                lblArchivo.setText("Archivo cargado: "+arch.nombre()+" ("+(arch.tipo().equals("application/pdf")?"PDF":"Imagen")+")");
                extraerDatosMotoDesdeArchivo(arch,duaManual,vin,motor,brand,model,year,color);
            }catch(java.io.IOException ex){throw new IllegalStateException("No se pudo leer el archivo seleccionado.",ex);}
        }));
        btnVerDoc.setOnAction(e->safe(()->{if(archivoAdjunto[0]!=null)VisorDocumentoDua.mostrar(owner(),archivoAdjunto[0]);}));
        var g=grid();g.add(campo("N° DUA / DAM *",duaManual),0,0);g.add(campo("Compra DUA registrada (opcional)",select),1,0);
        g.add(campo("VIN / chasis *",vin),0,1);g.add(campo("Numero de motor *",motor),1,1);
        g.add(campo("Marca *",brand),0,2);g.add(campo("Modelo *",model),1,2);
        g.add(campo("Anio del modelo *",year),0,3);g.add(campo("Color *",color),1,3);
        select.valueProperty().addListener((obs,oldVal,newVal)->{if(newVal!=null && s(newVal,"numero_dua")!=null)duaManual.setText(s(newVal,"numero_dua"));});
        var docBox=new VBox(8,new Label("Documento DUA (Imagen JPG/PNG o archivo PDF)"),new HBox(10,btnCargar,btnVerDoc),lblArchivo);
        var note=new Label("Puedes registrar la moto adjuntando su DUA en imagen (JPG/PNG) o PDF, o seleccionando una compra aprobada.");note.setWrapText(true);note.getStyleClass().add("text-subtle");
        VBox formBody = existing==null
            ? new VBox(16,new Label("Registrar moto nueva importada"),docBox,g,note)
            : new VBox(16,new Label("Corregir moto antes de tramitar"),g,note);
        form(existing==null?"Registrar moto nueva importada":"Corregir moto",formBody,err->{
            int y;try{y=Integer.parseInt(year.getText().trim());}catch(NumberFormatException ex){throw new IllegalArgumentException("Ingresa un anio numerico.");}
            Long detalleId = select.getValue()!=null ? id(select.getValue()) : (existing!=null && existing.get("detalle_compra_id") instanceof Number n ? n.longValue() : null);
            var request=new DBQPSunarpServiceI.Moto(detalleId,vin.getText(),motor.getText(),brand.getText(),model.getText(),y,color.getText());
            if(existing==null)service.registrarConArchivo(request,duaManual.getText(),archivoAdjunto[0]);
            else service.editar(id(existing),((Number)existing.get("version")).intValue(),request);
            onBuscar();
        });
    }
    private void extraerDatosMotoDesdeArchivo(ERQZArchivoDua arch,TextField dua,TextField vin,TextField motor,TextField marca,TextField modelo,TextField anio,TextField color){
        try{
            var m = new ERQZDuaPdfService().extraerMotoDua(arch);
            if (m != null) {
                if (!m.dua().isBlank()) dua.setText(m.dua());
                if (!m.vin().isBlank()) vin.setText(m.vin());
                if (!m.motor().isBlank()) motor.setText(m.motor());
                if (!m.marca().isBlank()) marca.setText(m.marca());
                if (!m.modelo().isBlank()) modelo.setText(m.modelo());
                if (m.anio() > 1900) anio.setText(Integer.toString(m.anio()));
                if (!m.color().isBlank()) color.setText(m.color());
            } else if (dua.getText().isBlank()) {
                dua.setText("DUA-" + arch.nombre().replaceAll("\\.[^.]+$", "").replaceAll("[^A-Za-z0-9-]", "-").toUpperCase(Locale.ROOT));
            }
        }catch(Exception ignored){}
    }
    void mostrarMoto(Map<String,Object> m){
        var d=dialog("Moto "+s(m,"vin"),owner());d.getDialogPane().getStyleClass().add("wide-dialog");var expedientes=new TableView<Map<String,Object>>();expedientes.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);expedientes.setPrefHeight(260);
        addColumn(expedientes,"Tramite","tipo",180);addColumn(expedientes,"Propietario propuesto","propietario_nombre",220);addColumn(expedientes,"Estado informado","estado",150);addColumn(expedientes,"Titulo","titulo",110);
        var action=new TableColumn<Map<String,Object>,Void>("Acciones");action.setCellFactory(c->new TableCell<>(){final Button b=new Button("Ver / seguimiento");{b.setOnAction(e->safe(()->{mostrarExpediente(id(getTableRow().getItem()),d.getDialogPane().getScene().getWindow());expedientes.setItems(FXCollections.observableArrayList(service.expedientes(id(m))));onBuscar();}));}protected void updateItem(Void v,boolean empty){super.updateItem(v,empty);setGraphic(empty?null:b);}});expedientes.getColumns().add(action);
        Runnable reload=()->expedientes.setItems(FXCollections.observableArrayList(service.expedientes(id(m))));reload.run();var first=new Button("Primera inscripcion");var transfer=new Button("Transferencia");first.setDisable(!editar());transfer.setDisable(!editar());first.setOnAction(e->nuevoExpediente(m,"PRIMERA_INSCRIPCION",reload));transfer.setOnAction(e->nuevoExpediente(m,"TRANSFERENCIA",reload));
        var history=new TableView<Map<String,Object>>(FXCollections.observableArrayList(service.historial(id(m))));history.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);history.setPrefHeight(180);addColumn(history,"Fecha","fecha",180);addColumn(history,"Responsable","usuario",170);addColumn(history,"Accion","accion",190);addColumn(history,"Detalle","detalle",320);
        var refresh=new Button("Actualizar historial");refresh.setOnAction(e->safe(()->history.setItems(FXCollections.observableArrayList(service.historial(id(m))))));
        var box=new VBox(16,new Label(s(m,"marca")+" "+s(m,"modelo")+" · VIN "+s(m,"vin")),new Label("Motor: "+s(m,"motor")+" · DUA/DAM: "+s(m,"numero_dua")),new HBox(10,first,transfer),expedientes,new Label("Historial de la moto"),refresh,history);box.setPrefWidth(920);box.getStyleClass().add("custom-modal");d.getDialogPane().setContent(box);d.showAndWait();
    }
    void nuevoExpediente(Map<String,Object> m,String type,Runnable reload){var doc=text("");var name=text("");form(type.equals("TRANSFERENCIA")?"Nueva transferencia":"Primera inscripcion",new VBox(16,new Label("VIN: "+s(m,"vin")),campo("DNI / RUC / CE del nuevo propietario *",doc),campo("Nombre o razon social *",name)),err->{service.crearExpediente(id(m),new DBQPSunarpServiceI.Tramite(type,doc.getText(),name.getText()));reload.run();});}
    void mostrarExpediente(long expediente,Window window){
        var detail=service.expediente(expediente);var d=dialog("Expediente interno #"+expediente,window);d.getDialogPane().getStyleClass().add("wide-dialog");var states=new ComboBox<String>(FXCollections.observableArrayList("BORRADOR","PREPARADO","PRESENTADO","OBSERVADO","INSCRITO","CANCELADO"));states.setValue(s(detail,"estado"));states.setMaxWidth(Double.MAX_VALUE);
        var title=text(s(detail,"titulo"));var year=text(s(detail,"anio_titulo"));var office=text(s(detail,"oficina"));var responsible=text(s(detail,"responsable"));var plate=text(s(detail,"placa"));var entry=text(s(detail,"partida"));var notes=new TextArea(s(detail,"observaciones"));notes.setPrefRowCount(3);notes.setWrapText(true);var reviewed=new CheckBox("El responsable reviso los documentos y los requisitos aplicables");reviewed.setWrapText(true);reviewed.setSelected(Boolean.TRUE.equals(detail.get("revision_documental"))||detail.get("revision_documental") instanceof Number n&&n.intValue()!=0);
        var g=grid();g.add(campo("Estado informado por el responsable",states),0,0);g.add(campo("Notario / gestor responsable",responsible),1,0);g.add(campo("Numero de titulo",title),0,1);g.add(campo("Anio del titulo",year),1,1);g.add(campo("Oficina registral",office),0,2);g.add(campo("Placa (al informar inscripcion)",plate),1,2);g.add(campo("Partida registral",entry),0,3);
        var ownerDoc=text(s(detail,"propietario_documento"));var ownerName=text(s(detail,"propietario_nombre"));ownerDoc.setEditable("BORRADOR".equals(detail.get("estado")));ownerName.setEditable(ownerDoc.isEditable());g.add(campo("Documento del propietario propuesto",ownerDoc),0,4);g.add(campo("Nombre del propietario propuesto",ownerName),1,4);
        var files=new TableView<Map<String,Object>>();files.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);files.setPrefHeight(150);addColumn(files,"Tipo","categoria",200);addColumn(files,"Archivo","nombre",320);addColumn(files,"Bytes","bytes",100);files.setItems(FXCollections.observableArrayList(service.archivos(expediente)));
        var category=new ComboBox<String>(FXCollections.observableArrayList(DBQPSunarpService.CATEGORIAS));category.setValue("DUA_DAM");var attach=new Button("Adjuntar PDF / imagen");var view=new Button("Ver archivo");var download=new Button("Descargar original");
        boolean closed=Set.of("INSCRITO","CANCELADO").contains(detail.get("estado"));attach.setDisable(!editar()||closed);var version=new int[]{((Number)detail.get("version")).intValue()};
        attach.setOnAction(e->safe(()->{var chooser=new FileChooser();chooser.setTitle("Adjuntar documento al expediente");chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PDF e imagenes","*.pdf","*.png","*.jpg","*.jpeg"));var f=chooser.showOpenDialog(d.getDialogPane().getScene().getWindow());if(f==null)return;try{service.adjuntar(expediente,version[0],category.getValue(),ERQZArchivoDua.leer(f.toPath()));version[0]=((Number)service.expediente(expediente).get("version")).intValue();files.setItems(FXCollections.observableArrayList(service.archivos(expediente)));}catch(java.io.IOException ex){throw new IllegalStateException("No se pudo leer el archivo.",ex);}}));
        view.setOnAction(e->safe(()->{var f=files.getSelectionModel().getSelectedItem();if(f==null)throw new IllegalArgumentException("Selecciona el archivo que quieres abrir.");VisorDocumentoDua.mostrar(d.getDialogPane().getScene().getWindow(),service.descargar(id(f)));}));
        download.setOnAction(e->safe(()->{var f=files.getSelectionModel().getSelectedItem();if(f==null)throw new IllegalArgumentException("Selecciona un archivo.");var original=service.descargar(id(f));var chooser=new FileChooser();chooser.setInitialFileName(original.nombre());var target=chooser.showSaveDialog(d.getDialogPane().getScene().getWindow());if(target!=null)try{ArchivoDescarga.guardarEn(target.toPath(),original.contenido());}catch(java.io.IOException ex){throw new IllegalStateException("No se pudo descargar el original.",ex);}}));
        var export=new ExportarMenu();export.setReporte(()->{var actual=service.expediente(expediente);var fields=List.of("vin","motor","marca","modelo","numero_dua","tipo","propietario_documento","propietario_nombre","vendedor_documento","vendedor_nombre","estado","titulo","anio_titulo","oficina","responsable","placa","partida","observaciones");return new ERQZReporteTabular("Expediente interno SUNARP #"+expediente,"Ficha interna. No acredita presentacion ni inscripcion oficial.",List.of("Campo","Dato"),fields.stream().map(k->ERQZReporteTabular.fila(k.replace('_',' '),s(actual,k))).toList());});
        var banner=new Label("Los estados son informados manualmente. PREPARADO requiere DUA/DAM o acta de transferencia. PRESENTADO requiere cargo. INSCRITO requiere constancia, placa y partida. Estos controles internos no sustituyen la calificacion de SUNARP.");banner.setWrapText(true);banner.getStyleClass().add("chip-info");
        var save=new Button("Guardar seguimiento");save.getStyleClass().add("btn-primario");save.setDisable(!editar()||closed);var message=new Label();message.setWrapText(true);message.getStyleClass().add("validation-error");
        save.setOnAction(e->{try{Integer y=year.getText().isBlank()?null:Integer.valueOf(year.getText().trim());service.actualizar(expediente,new DBQPSunarpServiceI.Seguimiento(version[0],states.getValue(),title.getText(),y,office.getText(),responsible.getText(),notes.getText(),reviewed.isSelected(),plate.getText(),entry.getText(),ownerDoc.getText(),ownerName.getText()));d.close();onBuscar();}catch(RuntimeException ex){message.setText(ex instanceof NumberFormatException?"Revisa el anio del titulo.":ex.getMessage());}});
        var box=new VBox(16,new Label(s(detail,"tipo")+" · VIN "+s(detail,"vin")),new Label("Propietario propuesto: "+s(detail,"propietario_nombre")+" · "+s(detail,"propietario_documento")),banner,g,campo("Observaciones / motivo de cancelacion",notes),reviewed,new Label("Documentos del expediente"),new HBox(10,category,attach),files,new HBox(10,view,download,export),message,save);box.setPrefWidth(850);box.getStyleClass().add("custom-modal");var scroll=new ScrollPane(box);scroll.setFitToWidth(true);scroll.setPrefViewportHeight(650);d.getDialogPane().setContent(scroll);d.showAndWait();
    }
    static void addColumn(TableView<Map<String,Object>> table,String title,String key,double width){var col=new TableColumn<Map<String,Object>,String>(title);col.setPrefWidth(width);bind(col,key);table.getColumns().add(col);}
    static ERQZReporteTabular reporte(String title,List<? extends Map<String,Object>> rows,List<String> keys){return new ERQZReporteTabular(title,"Seguimiento interno. No acredita presentacion ni inscripcion oficial ante SUNARP.",keys,rows.stream().map(r->keys.stream().map(k->r.get(k)).toList()).toList());}
}
