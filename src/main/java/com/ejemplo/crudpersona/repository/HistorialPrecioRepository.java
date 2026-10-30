package com.ejemplo.crudpersona.repository;

import com.ejemplo.crudpersona.model.HistorialPrecio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistorialPrecioRepository extends JpaRepository<HistorialPrecio, Long> {

    List<HistorialPrecio> findByProductoIdOrderByFechaCambioDesc(Long productoId);
}
