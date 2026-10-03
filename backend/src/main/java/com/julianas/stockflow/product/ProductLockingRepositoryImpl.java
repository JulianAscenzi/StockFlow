package com.julianas.stockflow.product;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public class ProductLockingRepositoryImpl implements ProductLockingRepository {
    private final EntityManager entityManager;

    public ProductLockingRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public Optional<Product> findByIdForUpdate(Long id) {
        // Preserve this transaction's pending changes before refreshing a managed product.
        entityManager.flush();
        Product product = entityManager.find(Product.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (product == null) return Optional.empty();
        // Acquiring a lock alone does not replace values already in the persistence context.
        entityManager.refresh(product, LockModeType.PESSIMISTIC_WRITE);
        return Optional.of(product);
    }
}
