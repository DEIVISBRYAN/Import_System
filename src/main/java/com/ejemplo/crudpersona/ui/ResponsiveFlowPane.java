package com.ejemplo.crudpersona.ui;

import javafx.scene.Node;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;

import java.util.Map;
import java.util.WeakHashMap;

public class ResponsiveFlowPane extends FlowPane {
    private final Map<Node, Double> maximumWidths = new WeakHashMap<>();

    public ResponsiveFlowPane() {
        setHgap(10);
        setVgap(10);
        setPrefWrapLength(900);
    }

    @Override
    protected double computeMinWidth(double height) {
        return 0;
    }

    @Override
    protected void layoutChildren() {
        double available = Math.max(0, getWidth() - snappedLeftInset() - snappedRightInset());
        for (Node node : getManagedChildren()) {
            if (node instanceof Region region) {
                double original = maximumWidths.computeIfAbsent(node, key -> region.getMaxWidth());
                double maximum = original < 0 ? available : Math.min(original, available);
                if (region.getMaxWidth() != maximum) region.setMaxWidth(maximum);
            }
        }
        super.layoutChildren();
    }
}
