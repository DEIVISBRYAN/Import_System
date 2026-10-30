package com.ejemplo.crudpersona.ui;

import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;

public class ResponsiveMetricsPane extends GridPane {
    public ResponsiveMetricsPane() {
        setHgap(18);
        setVgap(18);
        widthProperty().addListener((obs, old, width) -> arrange());
        getChildren().addListener((ListChangeListener<Node>) change -> arrange());
    }

    private void arrange() {
        int columns = Math.max(1, Math.min(getManagedChildren().size(), (int) (getWidth() / 190)));
        getColumnConstraints().clear();
        for (int i = 0; i < columns; i++) {
            ColumnConstraints constraint = new ColumnConstraints();
            constraint.setPercentWidth(100.0 / columns);
            constraint.setMinWidth(0);
            getColumnConstraints().add(constraint);
        }
        int index = 0;
        for (Node child : getManagedChildren()) {
            setColumnIndex(child, index % columns);
            setRowIndex(child, index / columns);
            if (child instanceof Region region) region.setMinWidth(0);
            index++;
        }
    }
}
