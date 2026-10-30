package com.ejemplo.crudpersona.repository;

import com.ejemplo.crudpersona.model.CajaTurno;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface CajaTurnoRepository extends JpaRepository<CajaTurno, Long> {

    Optional<CajaTurno> findByNumeroCajaAndEstado(Integer numeroCaja, String estado);

    List<CajaTurno> findAllByOrderByFechaAperturaDesc();
    List<CajaTurno> findByNumeroCajaOrderByFechaAperturaDesc(Integer numeroCaja);
}
