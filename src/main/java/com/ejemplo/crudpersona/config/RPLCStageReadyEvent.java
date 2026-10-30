package com.ejemplo.crudpersona.config;

import javafx.stage.Stage;
import org.springframework.context.ApplicationEvent;

public class RPLCStageReadyEvent extends ApplicationEvent {
    public RPLCStageReadyEvent(Stage stage) {
        super(stage);
    }

    public Stage getStage() {
        return (Stage) getSource();
    }
}