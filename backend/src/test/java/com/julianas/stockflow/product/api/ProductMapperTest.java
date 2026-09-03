package com.julianas.stockflow.product.api;

import com.julianas.stockflow.category.Category;
import com.julianas.stockflow.common.api.PageResponse;
import com.julianas.stockflow.product.Product;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ProductMapperTest {

    private final ProductMapper productMapper = new ProductMapper();

    @Test
    void mapsAllFieldsToProductResponse() {
        Instant createdAt = Instant.parse("2026-01-10T12:00:00Z");
        Instant updatedAt = Instant.parse("2026-02-15T09:30:00Z");
        BigDecimal price = new BigDecimal("125.50");
        BigDecimal cost = new BigDecimal("80.25");
        Category category = categoryWithId(7L);
        Product product = productWithValues(
                21L, "Mechanical Keyboard", "KEY-01", "RGB keyboard",
                price, cost, 14, 3, true, category, createdAt, updatedAt
        );

        ProductResponse response = productMapper.toResponse(product);

        assertEquals(21L, response.id());
        assertEquals("Mechanical Keyboard", response.name());
        assertEquals("KEY-01", response.sku());
        assertEquals("RGB keyboard", response.description());
        assertEquals(price, response.price());
        assertEquals(cost, response.cost());
        assertEquals(14, response.stock());
        assertEquals(3, response.minimumStock());
        assertTrue(response.active());
        assertEquals(7L, response.categoryId());
        assertEquals(createdAt, response.createdAt());
        assertEquals(updatedAt, response.updatedAt());
    }

    @Test
    void preservesExactBigDecimalInstances() {
        BigDecimal price = new BigDecimal("10.2300");
        BigDecimal cost = new BigDecimal("4.5600");
        Product product = productWithValues(
                1L, "Mouse", "MOU-01", null, price, cost,
                0, 0, true, categoryWithId(2L), null, null
        );

        ProductResponse response = productMapper.toResponse(product);

        assertSame(price, response.price());
        assertSame(cost, response.cost());
    }

    @Test
    void mapsStockMinimumStockAndActive() {
        Product product = productWithValues(
                1L, "Mouse", "MOU-01", null, BigDecimal.ONE, BigDecimal.ZERO,
                18, 5, false, categoryWithId(2L), null, null
        );

        ProductResponse response = productMapper.toResponse(product);

        assertEquals(18, response.stock());
        assertEquals(5, response.minimumStock());
        assertFalse(response.active());
    }

    @Test
    void mapsCategoryOnlyAsId() {
        Category category = categoryWithId(42L);
        Product product = productWithValues(
                1L, "Mouse", "MOU-01", null, BigDecimal.ONE, BigDecimal.ZERO,
                0, 0, true, category, null, null
        );

        ProductResponse response = productMapper.toResponse(product);

        assertEquals(42L, response.categoryId());
    }

    @Test
    void accessesNoCategoryGetterOtherThanId() {
        Category category = categoryWithId(42L);
        Product product = productWithValues(
                1L, "Mouse", "MOU-01", null, BigDecimal.ONE, BigDecimal.ZERO,
                0, 0, true, category, null, null
        );

        productMapper.toResponse(product);

        verify(category).getId();
        verifyNoMoreInteractions(category);
    }

    @Test
    void preservesNullDescription() {
        Product product = productWithValues(
                1L, "Mouse", "MOU-01", null, BigDecimal.ONE, BigDecimal.ZERO,
                0, 0, true, categoryWithId(2L), null, null
        );

        ProductResponse response = productMapper.toResponse(product);

        assertNull(response.description());
    }

    @Test
    void rejectsNullProduct() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> productMapper.toResponse(null)
        );

        assertEquals("product must not be null", exception.getMessage());
    }

    @Test
    void mapsPagePreservingContentOrderAndMetadata() {
        Product first = productWithIdAndName(31L, "First");
        Product second = productWithIdAndName(32L, "Second");
        Page<Product> page = new PageImpl<>(
                List.of(first, second),
                PageRequest.of(2, 2),
                7
        );

        PageResponse<ProductResponse> response = productMapper.toPageResponse(page);

        assertEquals(List.of(31L, 32L), response.content().stream().map(ProductResponse::id).toList());
        assertEquals(List.of("First", "Second"), response.content().stream().map(ProductResponse::name).toList());
        assertEquals(2, response.page());
        assertEquals(2, response.size());
        assertEquals(7, response.totalElements());
        assertEquals(4, response.totalPages());
        assertFalse(response.first());
        assertFalse(response.last());
    }

    @Test
    void rejectsNullPage() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> productMapper.toPageResponse(null)
        );

        assertEquals("page must not be null", exception.getMessage());
    }

    private Product productWithIdAndName(Long id, String name) {
        return productWithValues(
                id, name, "SKU-" + id, null, BigDecimal.ONE, BigDecimal.ZERO,
                0, 0, true, categoryWithId(id + 100), null, null
        );
    }

    private Product productWithValues(
            Long id,
            String name,
            String sku,
            String description,
            BigDecimal price,
            BigDecimal cost,
            Integer stock,
            Integer minimumStock,
            boolean active,
            Category category,
            Instant createdAt,
            Instant updatedAt
    ) {
        Product product = mock(Product.class);
        when(product.getId()).thenReturn(id);
        when(product.getName()).thenReturn(name);
        when(product.getSku()).thenReturn(sku);
        when(product.getDescription()).thenReturn(description);
        when(product.getPrice()).thenReturn(price);
        when(product.getCost()).thenReturn(cost);
        when(product.getStock()).thenReturn(stock);
        when(product.getMinimumStock()).thenReturn(minimumStock);
        when(product.isActive()).thenReturn(active);
        when(product.getCategory()).thenReturn(category);
        when(product.getCreatedAt()).thenReturn(createdAt);
        when(product.getUpdatedAt()).thenReturn(updatedAt);
        return product;
    }

    private Category categoryWithId(Long id) {
        Category category = mock(Category.class);
        when(category.getId()).thenReturn(id);
        return category;
    }
}
