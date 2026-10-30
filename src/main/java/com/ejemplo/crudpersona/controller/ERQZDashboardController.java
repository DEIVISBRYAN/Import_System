package com.ejemplo.crudpersona.controller;

import com.ejemplo.crudpersona.model.Producto;
import com.ejemplo.crudpersona.repository.DetalleVentaRepository;
import com.ejemplo.crudpersona.repository.ProductoRepository;
import com.ejemplo.crudpersona.repository.VentaRepository;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Component
public class ERQZDashboardController {

    private final ProductoRepository productoRepository;
    private final VentaRepository ventaRepository;
    private final DetalleVentaRepository detalleVentaRepository;

    private final com.ejemplo.crudpersona.service.DBQPTipoCambioServiceI cambios;

    @FXML private Label lblVentasHoy;
    @FXML private Label lblStockBajo;
    @FXML private Label lblSinStock;
    @FXML private Label lblInventarioUSD;
    @FXML private Label lblInventarioPEN;

    @FXML private Label lblMasVendido1;
    @FXML private Label lblMasVendido2;
    @FXML private Label lblMasVendido3;
    @FXML private Label lblMasVendido4;

    @FXML private Label lblMasVendidoCant1;
    @FXML private Label lblMasVendidoCant2;
    @FXML private Label lblMasVendidoCant3;
    @FXML private Label lblMasVendidoCant4;
    @FXML private ProgressBar barraMasVendido1;
    @FXML private ProgressBar barraMasVendido2;
    @FXML private ProgressBar barraMasVendido3;
    @FXML private ProgressBar barraMasVendido4;

    public ERQZDashboardController(ProductoRepository productoRepository, VentaRepository ventaRepository,
                              DetalleVentaRepository detalleVentaRepository, com.ejemplo.crudpersona.service.DBQPTipoCambioServiceI cambios) {
        this.productoRepository = productoRepository;
        this.ventaRepository = ventaRepository;
        this.detalleVentaRepository = detalleVentaRepository;
        this.cambios = cambios;
    }

    @FXML
    public void initialize() {
        cargarMetricas();
    }

    private void cargarMetricas() {
        // Ventas de hoy
        BigDecimal ventasHoy = ventaRepository.sumVentasHoy();
        if (ventasHoy == null) ventasHoy = BigDecimal.ZERO;
        lblVentasHoy.setText(String.format("S/ %,.2f", ventasHoy));

        // Stock bajo
        List<Producto> stockBajo = productoRepository.findStockBajo();
        lblStockBajo.setText(String.format("%d", stockBajo.size()));

        // Sin stock
        List<Producto> sinStock = productoRepository.findSinStock();
        lblSinStock.setText(String.format("%d", sinStock.size()));

        // Inventario valorizado
        BigDecimal inventarioPEN = productoRepository.sumValorInventarioPEN();
        if (inventarioPEN == null) inventarioPEN = BigDecimal.ZERO;
        BigDecimal inventarioUSD = inventarioPEN.divide(cambios.actual(), 2, RoundingMode.HALF_UP);

        lblInventarioUSD.setText(String.format("%,.0f", inventarioUSD));
        lblInventarioPEN.setText(String.format("S/ %,.2f", inventarioPEN));

        // Más vendidos
        cargarMasVendidos();
    }

    private void cargarMasVendidos() {
        Label[] nombres = {lblMasVendido1, lblMasVendido2, lblMasVendido3, lblMasVendido4};
        Label[] cantidades = {lblMasVendidoCant1, lblMasVendidoCant2, lblMasVendidoCant3, lblMasVendidoCant4};
        ProgressBar[] barras = {barraMasVendido1, barraMasVendido2, barraMasVendido3, barraMasVendido4};
        for (int i = 0; i < nombres.length; i++) {
            nombres[i].setText(i == 0 ? "Sin ventas registradas" : "-");
            cantidades[i].setText("0");
            barras[i].setProgress(0);
        }
        try {
            List<Object[]> ranking = detalleVentaRepository.findProductosMasVendidos();
            double maximo = ranking.stream().limit(4)
                    .mapToDouble(fila -> ((Number) fila[1]).doubleValue()).max().orElse(0);
            for (int i = 0; i < Math.min(4, ranking.size()); i++) {
                Object[] fila = ranking.get(i);
                long productoId = ((Number) fila[0]).longValue();
                long cantidad = ((Number) fila[1]).longValue();
                nombres[i].setText(productoRepository.findById(productoId)
                        .map(Producto::getDescripcion).orElse("Producto no disponible"));
                cantidades[i].setText(String.valueOf(cantidad));
                barras[i].setProgress(maximo > 0 ? cantidad / maximo : 0);
            }
        } catch (Exception e) {
            lblMasVendido1.setText("No se pudo cargar el ranking");
        }
    }
}
