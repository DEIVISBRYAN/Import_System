package com.ejemplo.crudpersona.ui;

import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.scene.Scene;
import javafx.scene.Parent;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TableView;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.Screen;
import javafx.stage.StageStyle;
import javafx.stage.Window;

public final class AppStyles {
    private static final String STYLESHEET = AppStyles.class.getResource("/css/app.css").toExternalForm();
    private static final String INSTALLED = AppStyles.class.getName();
    private static boolean installed;
    private static final String DARK_STYLESHEET = AppStyles.class.getResource("/css/dark.css").toExternalForm();
    private static final java.util.prefs.Preferences PREFERENCES = java.util.prefs.Preferences.userRoot().node("Importadora/UI");
    private static boolean darkTheme = PREFERENCES.getBoolean("darkTheme", false);

    public static boolean isDarkTheme() { return darkTheme; }

    public static void setDarkTheme(boolean dark) {
        darkTheme = dark;
        PREFERENCES.putBoolean("darkTheme", dark);
        javafx.application.Application.setUserAgentStylesheet(dark
                ? new atlantafx.base.theme.PrimerDark().getUserAgentStylesheet()
                : new atlantafx.base.theme.PrimerLight().getUserAgentStylesheet());
        Window.getWindows().forEach(w -> apply(w.getScene()));
    }

    private AppStyles() {
    }

    // Dialogs and receipt windows use separate scenes from the main window.
    public static void install() {
        if (installed) return;
        installed = true;
        setDarkTheme(darkTheme);
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                if (change.wasAdded()) change.getAddedSubList().forEach(AppStyles::apply);
            }
        });
        Window.getWindows().forEach(AppStyles::apply);
    }

    public static void apply(Scene scene) {
        if (scene != null && !scene.getStylesheets().contains(STYLESHEET)) {
            scene.getStylesheets().add(STYLESHEET);
        }
        if (scene == null) return;
        if (!scene.getStylesheets().contains(DARK_STYLESHEET)) scene.getStylesheets().add(DARK_STYLESHEET);
        if (!scene.getProperties().containsKey(INSTALLED)) {
            scene.getProperties().put(INSTALLED, true);
            scene.rootProperty().addListener((obs, old, root) -> themeRoot(root));
        }
        themeRoot(scene.getRoot());
    }

    private static void themeRoot(Parent root) {
        root.getStyleClass().remove("theme-dark");
        if (darkTheme) root.getStyleClass().add("theme-dark");
    }

    public static void prepareView(Parent root) {
        for (var node : root.lookupAll(".button")) {
            ((Button) node).setMinWidth(Region.USE_PREF_SIZE);
        }
        for (var node : root.lookupAll(".radio-button")) {
            ((RadioButton) node).setMinWidth(Region.USE_PREF_SIZE);
        }
        for (var node : root.lookupAll(".label")) {
            Label label = (Label) node;
            if (label.getStyleClass().contains("header-subtitle") || label.getStyleClass().contains("kpi-label")) {
                label.setWrapText(true);
            }
        }
        for (var node : root.lookupAll(".table-view")) {
            TableView<?> table = (TableView<?>) node;
            if (table.getStyleClass().contains("fit-columns")) {
                table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
                continue;
            }
            double preferred = table.getColumns().stream().mapToDouble(column -> column.getPrefWidth()).sum();
            Runnable resize = () -> table.setColumnResizePolicy(table.getWidth() < preferred + 24
                    ? TableView.UNCONSTRAINED_RESIZE_POLICY : TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
            table.widthProperty().addListener((obs, old, width) -> resize.run());
            resize.run();
        }
    }

    private static void apply(Window window) {
        if (!window.getProperties().containsKey(INSTALLED)) {
            window.getProperties().put(INSTALLED, true);
            window.sceneProperty().addListener((obs, oldScene, scene) -> apply(scene));
        }
        Scene scene = window.getScene();
        apply(scene);
        if (scene != null && window instanceof Stage stage) {
            if (stage.getStyle() == StageStyle.TRANSPARENT) scene.setFill(Color.TRANSPARENT);
            if (scene.getRoot() instanceof DialogPane pane) {
                Platform.runLater(() -> {
                    if (stage.isShowing()) {
                        for (var type : pane.getButtonTypes()) {
                            if (pane.lookupButton(type) instanceof Button button) {
                                ButtonBar.setButtonUniformSize(button, false);
                                button.setMinWidth(Region.USE_PREF_SIZE);
                                button.setPrefWidth(Region.USE_COMPUTED_SIZE);
                            }
                        }
                        var screens = Screen.getScreensForRectangle(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
                        var bounds = (screens.isEmpty() ? Screen.getPrimary() : screens.get(0)).getVisualBounds();
                        double availableWidth = Math.max(320, bounds.getWidth() - 40);
                        double availableHeight = Math.max(240, bounds.getHeight() - 140);
                        if (pane.getStyleClass().contains("app-dialog")) {
                            double width = Math.min(pane.getStyleClass().contains("wide-dialog") ? 940 : 640, availableWidth);
                            pane.setMinWidth(width);
                            pane.setPrefWidth(width);
                            pane.setMaxWidth(width);
                            var content = pane.getContent();
                            if (content != null && !(content instanceof ScrollPane)) {
                                ScrollPane scroll = new ScrollPane(content);
                                scroll.setFitToWidth(true);
                                scroll.setMaxHeight(availableHeight);
                                scroll.getStyleClass().add("modal-scroll");
                                pane.setContent(scroll);
                            }
                        }
                        scene.getRoot().applyCss();
                        stage.sizeToScene();
                        stage.centerOnScreen();
                    }
                });
            }
        }
    }
}
