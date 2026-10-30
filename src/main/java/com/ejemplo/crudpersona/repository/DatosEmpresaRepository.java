package com.ejemplo.crudpersona.repository;

import com.ejemplo.crudpersona.model.DatosEmpresa;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DatosEmpresaRepository extends JpaRepository<DatosEmpresa, Long> {
}
