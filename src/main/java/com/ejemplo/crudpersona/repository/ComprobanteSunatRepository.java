package com.ejemplo.crudpersona.repository;

import com.ejemplo.crudpersona.model.ComprobanteSunat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ComprobanteSunatRepository extends JpaRepository<ComprobanteSunat, Long> {
    long countByEstadoSunat(String estadoSunat);
    Optional<ComprobanteSunat> findBySerieAndCorrelativo(String serie, Integer correlativo);
    List<ComprobanteSunat> findByClienteDocumento(String clienteDocumento);
    Optional<ComprobanteSunat> findByVentaId(Long ventaId);
    List<ComprobanteSunat> findAllByOrderByFechaEmisionDescIdDesc();
}
