package com.julianas.stockflow.product;

import java.util.Optional;

public interface ProductLockingRepository {
    Optional<Product> findByIdForUpdate(Long id);
}
