package com.ejemplo.crudpersona.repository;

import com.ejemplo.crudpersona.model.Caja;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface CajaRepository extends JpaRepository<Caja, Integer> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Caja c WHERE c.numero = :numero")
    Optional<Caja> bloquear(@Param("numero") int numero);
}
