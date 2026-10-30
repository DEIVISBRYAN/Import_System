package com.ejemplo.crudpersona.repository;

import com.ejemplo.crudpersona.model.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Long> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Producto p WHERE p.id = :id")
    Optional<Producto> bloquear(Long id);
    Optional<Producto> findByCodigoBarras(String codigoBarras);
    Optional<Producto> findBySku(String sku);
    Optional<Producto> findBySkuIgnoreCase(String sku);
    @Query("SELECT p.id FROM Producto p ORDER BY p.id")
    List<Long> idsParaActualizar();
    @Query("SELECT p.id FROM Producto p WHERE lower(p.sku) = lower(:sku)")
    Optional<Long> idPorSku(String sku);
    List<Producto> findByDescripcionContainingIgnoreCase(String query);

    @Query("SELECT p FROM Producto p WHERE p.stockActual > 0 AND p.stockActual <= p.stockMinimo")
    List<Producto> findStockBajo();

    @Query("SELECT p FROM Producto p WHERE p.stockActual = 0")
    List<Producto> findSinStock();

    @Query("SELECT SUM(p.stockActual * p.costoPromedioPen) FROM Producto p")
    BigDecimal sumValorInventarioPEN();
}
