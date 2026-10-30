package com.ejemplo.crudpersona.ui;

import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.layout.Pane;

public class ResponsiveSplitPane extends Pane {
    private static final double GAP = 18;
    private static final double BREAKPOINT = 1100;

    @Override
    public Orientation getContentBias() {
        return Orientation.HORIZONTAL;
    }

    @Override
    protected double computeMinWidth(double height) {
        return 0;
    }

    @Override
    protected double computePrefWidth(double height) {
        return 1200;
    }

    @Override
    protected double computeMinHeight(double width) {
        if (getManagedChildren().size() != 2) return super.computeMinHeight(width);
        Node first = getManagedChildren().get(0);
        Node second = getManagedChildren().get(1);
        if (width >= BREAKPOINT) {
            double side = sideWidth(second);
            return Math.max(first.minHeight(width - side - GAP), second.minHeight(side));
        }
        return Math.max(280, first.minHeight(width)) + GAP + Math.max(260, second.minHeight(width));
    }

    @Override
    protected double computePrefHeight(double width) {
        if (getManagedChildren().size() != 2) return super.computePrefHeight(width);
        Node first = getManagedChildren().get(0);
        Node second = getManagedChildren().get(1);
        if (width >= BREAKPOINT) {
            double side = sideWidth(second);
            return Math.max(first.prefHeight(width - side - GAP), second.prefHeight(side));
        }
        return Math.max(340, first.prefHeight(width)) + GAP + Math.max(280, second.prefHeight(width));
    }

    @Override
    protected void layoutChildren() {
        if (getManagedChildren().size() != 2) return;
        Node first = getManagedChildren().get(0);
        Node second = getManagedChildren().get(1);
        double width = getWidth();
        if (width >= BREAKPOINT) {
            double side = sideWidth(second);
            first.resizeRelocate(0, 0, width - side - GAP, getHeight());
            second.resizeRelocate(width - side, 0, side, getHeight());
        } else {
            double firstHeight = Math.max(280, first.minHeight(width));
            double secondHeight = Math.max(260, second.minHeight(width));
            double extra = Math.max(0, getHeight() - firstHeight - secondHeight - GAP);
            firstHeight += extra * 0.6;
            secondHeight += extra * 0.4;
            first.resizeRelocate(0, 0, width, firstHeight);
            second.resizeRelocate(0, firstHeight + GAP, width, secondHeight);
        }
    }

    private double sideWidth(Node node) {
        return Math.max(340, Math.min(430, node.prefWidth(-1)));
    }
}
