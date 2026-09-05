package com.julianas.stockflow.sale;

import com.julianas.stockflow.category.Category;
import com.julianas.stockflow.category.CategoryRepository;
import com.julianas.stockflow.product.Product;
import com.julianas.stockflow.product.ProductRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Testcontainers
@TestPropertySource(properties = "spring.jpa.open-in-view=false")
class SaleServiceIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configurePostgresql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Autowired private SaleService saleService;
    @Autowired private SaleRepository saleRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    @AfterEach
    void clearDatabase() {
        jdbcTemplate.execute("TRUNCATE TABLE sale_items, sales, stock_movements, products, categories RESTART IDENTITY");
    }

    @Test
    void persistsConfirmedSaleWithItsConsistentTotal() {
        Product product = saveProduct();
        Sale sale = new Sale("Counter sale");
        sale.addItem(product, 2);

        Sale confirmed = saleService.confirm(sale);

        assertNotNull(confirmed.getId());
        assertEquals(new BigDecimal("25.00"), confirmed.getTotal());
        assertEquals(1, saleRepository.count());
        assertEquals(2, productRepository.findById(product.getId()).orElseThrow().getStock());
    }

    @Test
    void doesNotPersistEmptySale() {
        assertThrows(EmptySaleException.class, () -> saleService.confirm(new Sale(null)));

        assertEquals(0, saleRepository.count());
    }

    private Product saveProduct() {
        Category category = categoryRepository.saveAndFlush(new Category("Peripherals", null));
        return productRepository.saveAndFlush(new Product(
                "Mouse", "MOU-01", null, new BigDecimal("12.50"), new BigDecimal("7.25"), 2, 0, true, category
        ));
    }
}
