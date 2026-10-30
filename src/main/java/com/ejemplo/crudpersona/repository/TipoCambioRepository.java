package com.ejemplo.crudpersona.repository;

import com.ejemplo.crudpersona.model.TipoCambio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface TipoCambioRepository extends JpaRepository<TipoCambio, Long> {
    Optional<TipoCambio> findByFecha(LocalDate fecha);
    Optional<TipoCambio> findTopByOrderByFechaDesc();
}