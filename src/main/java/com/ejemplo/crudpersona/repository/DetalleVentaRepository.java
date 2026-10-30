package com.ejemplo.crudpersona.repository;

import com.ejemplo.crudpersona.model.DetalleVenta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DetalleVentaRepository extends JpaRepository<DetalleVenta, Long> {

    @Query("SELECT dv FROM DetalleVenta dv JOIN FETCH dv.producto WHERE dv.venta.id = :ventaId")
    List<DetalleVenta> findByVentaId(@Param("ventaId") Long ventaId);

    @Query("SELECT dv.producto.id, SUM(dv.cantidad) FROM DetalleVenta dv WHERE dv.venta.estado = 'COMPLETADA' GROUP BY dv.producto.id ORDER BY SUM(dv.cantidad) DESC")
    List<Object[]> findProductosMasVendidos();

    @Query("SELECT dv.producto.id, SUM(dv.cantidad) FROM DetalleVenta dv WHERE dv.venta.estado = 'COMPLETADA' AND dv.venta.fechaEmision >= :inicio GROUP BY dv.producto.id")
    List<Object[]> findCantidadesDesde(java.time.LocalDateTime inicio);
    default List<Object[]> findVentasDiaUltimos30Dias() {
        return findCantidadesDesde(java.time.LocalDateTime.now().minusDays(30)).stream()
                .map(row -> new Object[]{row[0], ((Number) row[1]).doubleValue() / 30.0}).toList();
    }
}
