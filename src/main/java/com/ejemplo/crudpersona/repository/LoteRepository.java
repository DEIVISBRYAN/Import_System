package com.ejemplo.crudpersona.repository;

import com.ejemplo.crudpersona.model.Lote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoteRepository extends JpaRepository<Lote, Long> {
    List<Lote> findByProductoIdAndCantidadDisponibleGreaterThanOrderByFechaIngresoAsc(Long productoId, int minCantidad);
    List<Lote> findByProductoIdOrderByFechaIngresoAsc(Long productoId);
    List<Lote> findByCodigoLote(String codigoLote);

    @Query("SELECT l FROM Lote l JOIN FETCH l.producto ORDER BY l.ubicacion, l.fechaIngreso")
    List<Lote> findAllWithProductoOrderByUbicacion();
}
