package com.julianas.stockflow.product;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    Optional<Product> findBySkuIgnoreCase(String sku);

    boolean existsBySkuIgnoreCase(String sku);

    Page<Product> findByCategoryId(Long categoryId, Pageable pageable);

    boolean existsByCategoryId(Long categoryId);

    Page<Product> findByNameContainingIgnoreCase(String name, Pageable pageable);

    Page<Product> findByActiveTrue(Pageable pageable);

    @Query("select p from Product p where (:sellable = false or (p.active = true and p.stock > 0)) "
            + "and (locate(lower(:query), lower(p.name)) > 0 or locate(lower(:query), lower(p.sku)) > 0)")
    Page<Product> lookup(@Param("query") String query, @Param("sellable") boolean sellable, Pageable pageable);

    @Query("select p from Product p where p.stock <= p.minimumStock order by p.stock, p.name, p.id")
    Page<Product> findLowStock(Pageable pageable);
}
