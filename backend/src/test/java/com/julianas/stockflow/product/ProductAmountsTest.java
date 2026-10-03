package com.julianas.stockflow.product;

import com.julianas.stockflow.category.Category;
import com.julianas.stockflow.category.CategoryRepository;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductAmountsTest {
    @ParameterizedTest
    @ValueSource(strings = {"1.001", "1.000", "10000000000", "-0.01"})
    void invalidAmountsRejectConstructorsUpdatesAndServicesBeforeMutation(String amount) {
        BigDecimal invalid = new BigDecimal(amount);
        Category category = new Category("Category", null);
        for (boolean price : new boolean[]{true, false}) {
            BigDecimal requestedPrice = price ? invalid : BigDecimal.ONE;
            BigDecimal requestedCost = price ? BigDecimal.ONE : invalid;
            assertThrows(IllegalArgumentException.class, () -> new Product("New", "NEW", null,
                    requestedPrice, requestedCost, 0, 0, true, category));
            Product product = new Product("Original", "OLD", null, BigDecimal.ONE, BigDecimal.ZERO, 5, 0, true, category);
            assertThrows(IllegalArgumentException.class, () -> product.update("New", "NEW", "Changed",
                    requestedPrice, requestedCost, 2, new Category("New category", null)));
            assertEquals("Original", product.getName());
            assertEquals("OLD", product.getSku());
            assertEquals(new BigDecimal("1.00"), product.getPrice());
            assertEquals(new BigDecimal("0.00"), product.getCost());
            assertSame(category, product.getCategory());
            ProductRepository repository = mock(ProductRepository.class);
            CategoryRepository categories = mock(CategoryRepository.class);
            ProductService service = new ProductService(repository, categories);
            assertThrows(IllegalArgumentException.class, () -> service.create("New", "NEW", null,
                    requestedPrice, requestedCost, 0, 1L));
            assertThrows(IllegalArgumentException.class, () -> service.update(1L, "New", "NEW", null,
                    requestedPrice, requestedCost, 0, 1L));
            verifyNoInteractions(repository, categories);
        }
    }
}
