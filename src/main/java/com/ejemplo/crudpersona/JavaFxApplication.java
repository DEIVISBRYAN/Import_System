package com.ejemplo.crudpersona;

import com.ejemplo.crudpersona.config.RPLCStageReadyEvent;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import com.ejemplo.crudpersona.ui.AppStyles;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

public class JavaFxApplication extends Application {

    private ConfigurableApplicationContext applicationContext;
    private RuntimeException falloArranque;

    @Override
    public void init() {
        try {this.applicationContext=iniciarContexto();}
        catch(RuntimeException ex){falloArranque=ex;}
    }
    private ConfigurableApplicationContext iniciarContexto(){return new SpringApplicationBuilder().sources(RPLCSpringAppConfig.class).run(getParameters().getRaw().toArray(new String[0]));}

    @Override
    public void start(Stage primaryStage) {
        if(falloArranque==null)mostrarInicio(primaryStage);else mostrarRecuperacion(primaryStage);
    }
    private void mostrarInicio(Stage stage) {
        try{applicationContext.publishEvent(new RPLCStageReadyEvent(stage));}
        catch(RuntimeException ex){falloArranque=ex;mostrarRecuperacion(stage);}
    }
    private void mostrarRecuperacion(Stage stage) {
        AppStyles.install();
        Label titulo=new Label("No se pudo iniciar el sistema");titulo.getStyleClass().add("title-4");
        Label estado=new Label();estado.setWrapText(true);estado.setId("errorArranque");
        Throwable causa=falloArranque;
        while(causa.getCause()!=null)causa=causa.getCause();
        estado.setText("No se pudo abrir la base local. Revisa los permisos de la carpeta y que no este bloqueada por otra instancia. No borres el archivo de datos. Detalle: "+causa.getMessage());
        Button reintentar=new Button("Reintentar");reintentar.setId("reintentarArranque");reintentar.getStyleClass().add("btn-primario");
        ProgressIndicator progreso=new ProgressIndicator();progreso.setMaxSize(30,30);progreso.setVisible(false);progreso.setManaged(false);
        Runnable recuperar=()->{
            reintentar.setDisable(true);progreso.setVisible(true);progreso.setManaged(true);estado.setText("Abriendo la base de datos local...");
            Task<ConfigurableApplicationContext> tarea=new Task<>(){@Override protected ConfigurableApplicationContext call()throws Exception{
                return iniciarContexto();}};
            tarea.setOnSucceeded(e->{applicationContext=tarea.getValue();falloArranque=null;mostrarInicio(stage);});
            tarea.setOnFailed(e->{falloArranque=new IllegalStateException(tarea.getException());mostrarRecuperacion(stage);});
            Thread hilo=new Thread(tarea,"reapertura-base-local");hilo.setDaemon(true);hilo.start();
        };
        reintentar.setOnAction(e->recuperar.run());
        VBox contenido=new VBox(18,titulo,estado,reintentar,progreso);contenido.setPadding(new Insets(32));contenido.getStyleClass().add("pagina");
        stage.setTitle(com.ejemplo.crudpersona.ui.Marca.NOMBRE + " · Base de datos local");
        com.ejemplo.crudpersona.ui.Marca.aplicarIcono(stage);stage.setScene(new Scene(contenido,690,360));stage.setMinWidth(440);stage.setMinHeight(330);stage.show();
    }

    @Override
    public void stop() {
        if (applicationContext != null) {
            applicationContext.close();
        }
        Platform.exit();
    }
}
