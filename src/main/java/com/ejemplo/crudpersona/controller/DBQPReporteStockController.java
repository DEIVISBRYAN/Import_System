package com.ejemplo.crudpersona.controller;

import com.ejemplo.crudpersona.model.Producto;
import com.ejemplo.crudpersona.repository.ProductoRepository;
import com.ejemplo.crudpersona.service.ERQZReporteTabular;
import com.ejemplo.crudpersona.ui.ExportarMenu;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class DBQPReporteStockController {

    private final ProductoRepository productoRepository;

    @FXML private Label lblTotalProductos;
    @FXML private ExportarMenu exportarReporte;
    @FXML private Label lblStockBajo;
    @FXML private Label lblSinStock;
    @FXML private BarChart<String, Number> chartStock;
    @FXML private TableView<StockItem> tablaStock;
    @FXML private TableColumn<StockItem, String> colProducto;
    @FXML private TableColumn<StockItem, String> colSku;
    @FXML private TableColumn<StockItem, String> colStock;
    @FXML private TableColumn<StockItem, String> colMinimo;
    @FXML private TableColumn<StockItem, String> colEstado;

    public DBQPReporteStockController(ProductoRepository productoRepository) {
        this.productoRepository = productoRepository;
    }

    @FXML
    public void initialize() {
        exportarReporte.setReporte(this::reporteStock);
        colProducto.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().producto()));
        colSku.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().sku()));
        colStock.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().stock())));
        colMinimo.setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().minimo())));
        colEstado.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().estado()));
        cargar();
    }

    public ERQZReporteTabular reporteStock() {
        return new ERQZReporteTabular("Reporte de stock", "Inventario total por producto; incluye todos los productos de la tabla, no solo los diez del gráfico.",
                List.of("Producto", "SKU", "Stock", "Stock mínimo", "Estado"), tablaStock.getItems().stream()
                .map(i -> ERQZReporteTabular.fila(i.producto(), i.sku(), i.stock(), i.minimo(), i.estado())).toList());
    }

    @FXML
    public void onActualizar() {
        cargar();
    }

    private void cargar() {
        List<Producto> productos = productoRepository.findAll();
        List<StockItem> items = productos.stream()
                .sorted(Comparator.comparingInt((Producto p) -> entero(p.getStockActual())).reversed())
                .map(p -> new StockItem(
                        texto(p.getDescripcion()),
                        texto(p.getSku()),
                        entero(p.getStockActual()),
                        entero(p.getStockMinimo()),
                        estado(p)))
                .toList();

        lblTotalProductos.setText(String.valueOf(productos.size()));
        lblStockBajo.setText(String.valueOf(items.stream().filter(i -> "Stock bajo".equals(i.estado())).count()));
        lblSinStock.setText(String.valueOf(items.stream().filter(i -> "Sin stock".equals(i.estado())).count()));

        tablaStock.setItems(FXCollections.observableArrayList(items));
        chartStock.getData().clear();
        XYChart.Series<String, Number> serie = new XYChart.Series<>();
        serie.setName("Stock disponible");
        items.stream().limit(10).forEach(item -> serie.getData().add(new XYChart.Data<>(recortar(item.producto()), item.stock())));
        chartStock.getData().add(serie);
    }

    private String estado(Producto producto) {
        int stock = entero(producto.getStockActual());
        if (stock <= 0) return "Sin stock";
        if (stock <= entero(producto.getStockMinimo())) return "Stock bajo";
        return "Disponible";
    }

    private int entero(Integer value) {
        return value == null ? 0 : value;
    }

    private String texto(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String recortar(String value) {
        return value.length() <= 18 ? value : value.substring(0, 16) + "...";
    }

    public record StockItem(String producto, String sku, int stock, int minimo, String estado) {
    }
}
