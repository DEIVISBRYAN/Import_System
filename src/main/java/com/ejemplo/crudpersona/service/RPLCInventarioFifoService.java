package com.ejemplo.crudpersona.service;

import com.ejemplo.crudpersona.model.Lote;
import com.ejemplo.crudpersona.model.Producto;
import com.ejemplo.crudpersona.repository.LoteRepository;
import com.ejemplo.crudpersona.repository.ProductoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RPLCInventarioFifoService implements RPLCInventarioFifoServiceI {

    private final ProductoRepository productoRepository;
    private final LoteRepository loteRepository;

    public RPLCInventarioFifoService(ProductoRepository productoRepository, LoteRepository loteRepository) {
        this.productoRepository = productoRepository;
        this.loteRepository = loteRepository;
    }

    /**
     * Regla estricta: Descuenta stock usando primero los lotes más antiguos (FIFO).
     */
    @Transactional
    public void despacharFifo(Long productoId, int cantidadADespachar) {
        despacharFifoConDetalle(productoId, cantidadADespachar);
    }

    @Override
    @Transactional
    public List<DespachoLote> despacharFifoConDetalle(Long productoId, int cantidadADespachar) {
        if (cantidadADespachar <= 0) throw new IllegalArgumentException("La cantidad debe ser mayor que cero.");
        Producto producto = productoRepository.bloquear(productoId)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));

        if (producto.getStockActual() == null || producto.getStockActual() < cantidadADespachar) {
            throw new RuntimeException("Stock insuficiente para: " + producto.getDescripcion());
        }

        List<Lote> lotesDisponibles = loteRepository.findByProductoIdAndCantidadDisponibleGreaterThanOrderByFechaIngresoAsc(productoId, 0);
        java.util.ArrayList<DespachoLote> despachos = new java.util.ArrayList<>();

        int pendiente = cantidadADespachar;
        for (Lote lote : lotesDisponibles) {
            if (pendiente <= 0) break;

            int disponible = lote.getCantidadDisponible() == null ? 0 : lote.getCantidadDisponible();
            int descuento = Math.min(disponible, pendiente);
            if (descuento <= 0) continue;
            lote.setCantidadDisponible(disponible - descuento);
            pendiente -= descuento;
            despachos.add(new DespachoLote(lote.getId(), lote.getCodigoLote(), descuento));
            loteRepository.save(lote);
        }

        if (pendiente > 0) {
            throw new RuntimeException("No hay lotes suficientes para: " + producto.getDescripcion());
        }

        producto.setStockActual(producto.getStockActual() - cantidadADespachar);
        productoRepository.save(producto);
        return despachos;
    }
}
