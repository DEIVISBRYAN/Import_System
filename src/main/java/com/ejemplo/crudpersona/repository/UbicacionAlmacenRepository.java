package com.ejemplo.crudpersona.repository;

import com.ejemplo.crudpersona.model.UbicacionAlmacen;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UbicacionAlmacenRepository extends JpaRepository<UbicacionAlmacen, Long> {
    Optional<UbicacionAlmacen> findByCodigo(String codigo);
    List<UbicacionAlmacen> findByActivoTrueOrderByPasilloAscAnaquelAscNivelAsc();
}
