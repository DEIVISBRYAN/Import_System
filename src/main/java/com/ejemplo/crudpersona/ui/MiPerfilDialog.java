package com.ejemplo.crudpersona.ui;

import com.ejemplo.crudpersona.model.Usuario;
import com.ejemplo.crudpersona.service.RPLCUsuariosServiceI;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.StageStyle;
import javafx.stage.Window;
import java.util.function.Consumer;

public final class MiPerfilDialog {
    private MiPerfilDialog() {}
    public static void mostrar(Window owner,RPLCUsuariosServiceI servicio,Consumer<Usuario> actualizado) {
        var perfil=servicio.miPerfil();Dialog<Void> dialog=new Dialog<>();dialog.setTitle("Mi perfil");dialog.initOwner(owner);dialog.initStyle(StageStyle.TRANSPARENT);
        dialog.getDialogPane().getStyleClass().add("app-dialog");dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL,ButtonType.OK);
        ((Button)dialog.getDialogPane().lookupButton(ButtonType.CANCEL)).setText("Cancelar");
        Button guardar=(Button)dialog.getDialogPane().lookupButton(ButtonType.OK);guardar.setText("Guardar cambios");guardar.setId("guardarMiPerfil");guardar.getStyleClass().add("btn-primario");
        Label titulo=new Label("Mi perfil");titulo.getStyleClass().add("modal-title");
        Label rol=new Label(perfil.rol()+" · "+(perfil.activo()?"Activo":"Inactivo"));rol.getStyleClass().add("chip-info");rol.setWrapText(true);
        TextField usuario=new TextField(perfil.usuario());usuario.setId("miPerfilUsuario");
        TextField nombre=new TextField(perfil.nombre());nombre.setId("miPerfilNombre");
        PasswordField actual=new PasswordField();actual.setId("miPerfilClaveActual");
        PasswordField nueva=new PasswordField();nueva.setId("miPerfilClaveNueva");
        PasswordField repetir=new PasswordField();repetir.setId("miPerfilClaveRepetir");
        Label error=new Label();error.setId("errorMiPerfil");error.setWrapText(true);error.getStyleClass().add("validation-error");
        for(var entrada:java.util.List.of(usuario,nombre,actual,nueva,repetir))entrada.textProperty().addListener((o,a,b)->error.setText(""));
        VBox contenido=new VBox(12,titulo,rol,campo("Usuario",usuario),campo("Nombre completo",nombre),new Separator(),
                campo("Contraseña actual",actual),campo("Nueva contraseña (opcional)",nueva),campo("Repetir nueva contraseña",repetir),error);
        contenido.getStyleClass().add("custom-modal");dialog.getDialogPane().setContent(contenido);
        guardar.addEventFilter(javafx.event.ActionEvent.ACTION,e->{
            try{
                if(!nueva.getText().equals(repetir.getText()))throw new IllegalArgumentException("Las contraseñas nuevas no coinciden.");
                var guardado=servicio.guardarMiPerfil(usuario.getText(),nombre.getText(),actual.getText(),nueva.getText());actualizado.accept(guardado);
                actual.clear();nueva.clear();repetir.clear();
            }catch(RuntimeException ex){error.setText(ex.getMessage());e.consume();}
        });dialog.showAndWait();actual.clear();nueva.clear();repetir.clear();
    }
    private static VBox campo(String texto,TextField entrada){Label label=new Label(texto);label.getStyleClass().add("dialog-form-label");entrada.setMaxWidth(Double.MAX_VALUE);return new VBox(5,label,entrada);}
}
