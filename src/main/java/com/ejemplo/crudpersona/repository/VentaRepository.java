package com.ejemplo.crudpersona.repository;

import com.ejemplo.crudpersona.model.Venta;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VentaRepository extends JpaRepository<Venta, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Venta v WHERE v.id = :id")
    Optional<Venta> bloquear(Long id);

    Optional<Venta> findBySerieAndCorrelativo(String serie, Integer correlativo);

    @Query("SELECT COALESCE(MAX(v.correlativo), 0) FROM Venta v WHERE v.serie = :serie")
    Integer findMaxCorrelativoBySerie(String serie);

    @Query("SELECT SUM(v.total) FROM Venta v WHERE v.fechaEmision >= :inicio AND v.fechaEmision < :fin AND v.estado = 'COMPLETADA'")
    BigDecimal sumVentasPeriodo(LocalDateTime inicio, LocalDateTime fin);
    default BigDecimal sumVentasHoy() {
        LocalDate hoy = LocalDate.now();
        return sumVentasPeriodo(hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay());
    }

    @Query("SELECT COUNT(v) FROM Venta v WHERE v.fechaEmision >= :inicio AND v.fechaEmision < :fin AND v.estado = 'COMPLETADA'")
    Long countVentasPeriodo(LocalDateTime inicio, LocalDateTime fin);
    default Long countVentasHoy() {
        LocalDate hoy = LocalDate.now();
        return countVentasPeriodo(hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay());
    }

    @Query("SELECT SUM(v.igv) FROM Venta v WHERE v.fechaEmision >= :inicio AND v.fechaEmision < :fin AND v.estado = 'COMPLETADA'")
    BigDecimal sumIgvPeriodo(LocalDateTime inicio, LocalDateTime fin);
    default BigDecimal sumIgvHoy() {
        LocalDate hoy = LocalDate.now();
        return sumIgvPeriodo(hoy.atStartOfDay(), hoy.plusDays(1).atStartOfDay());
    }

    List<Venta> findAllByOrderByFechaEmisionDesc();

    List<Venta> findByFechaEmisionBetweenOrderByFechaEmisionDesc(LocalDateTime inicio, LocalDateTime fin);
}
