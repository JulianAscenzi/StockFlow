package com.julianas.stockflow.product;

import com.julianas.stockflow.category.Category;
import com.julianas.stockflow.category.CategoryRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class ProductRepositoryTest {

    @Container
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configurePostgresql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesProductWithCategoryAndAssignsId() {
        Category category = saveCategory("Electronics");

        Product saved = productRepository.saveAndFlush(product("Mouse", "MOU-01", true, category));

        assertNotNull(saved.getId());
        assertEquals(category.getId(), saved.getCategory().getId());
    }

    @Test
    void findsBySkuIgnoringCase() {
        Category category = saveCategory("Electronics");
        productRepository.saveAndFlush(product("Mouse", "MOU-01", true, category));

        Product found = productRepository.findBySkuIgnoreCase("mOu-01").orElseThrow();

        assertEquals("MOU-01", found.getSku());
    }

    @Test
    void checksExistenceBySkuIgnoringCase() {
        Category category = saveCategory("Electronics");
        productRepository.saveAndFlush(product("Mouse", "MOU-01", true, category));

        assertTrue(productRepository.existsBySkuIgnoreCase("mou-01"));
    }

    @Test
    void findsByCategoryIdWithPagination() {
        Category peripherals = saveCategory("Peripherals");
        Category furniture = saveCategory("Furniture");
        productRepository.save(product("Mouse", "MOU-01", true, peripherals));
        productRepository.save(product("Keyboard", "KEY-01", true, peripherals));
        productRepository.saveAndFlush(product("Desk", "DSK-01", true, furniture));

        Page<Product> firstPage = productRepository.findByCategoryId(
                peripherals.getId(),
                PageRequest.of(0, 1)
        );

        assertEquals(2, firstPage.getTotalElements());
        assertEquals(2, firstPage.getTotalPages());
        assertEquals(1, firstPage.getContent().size());
        assertEquals(peripherals.getId(), firstPage.getContent().getFirst().getCategory().getId());
    }

    @Test
    void findsByPartialNameIgnoringCase() {
        Category category = saveCategory("Peripherals");
        productRepository.save(product("Mechanical Keyboard", "KEY-01", true, category));
        productRepository.saveAndFlush(product("Wireless Mouse", "MOU-01", true, category));

        Page<Product> result = productRepository.findByNameContainingIgnoreCase(
                "KEYbo",
                PageRequest.of(0, 10)
        );

        assertEquals(1, result.getTotalElements());
        assertEquals("Mechanical Keyboard", result.getContent().getFirst().getName());
    }

    @Test
    void listsOnlyActiveProducts() {
        Category category = saveCategory("Peripherals");
        productRepository.save(product("Mouse", "MOU-01", true, category));
        productRepository.saveAndFlush(product("Keyboard", "KEY-01", false, category));

        Page<Product> result = productRepository.findByActiveTrue(PageRequest.of(0, 10));

        assertEquals(1, result.getTotalElements());
        assertTrue(result.getContent().getFirst().isActive());
        assertFalse(result.getContent().stream().anyMatch(product -> product.getSku().equals("KEY-01")));
    }

    @Test
    void retrievedProductPreservesDecimalsAndCategoryRelationship() {
        Category category = saveCategory("Peripherals");
        Product saved = productRepository.saveAndFlush(product("Mouse", "MOU-01", true, category));
        Long productId = saved.getId();
        Long categoryId = category.getId();
        entityManager.clear();

        Product found = productRepository.findById(productId).orElseThrow();

        assertEquals(new BigDecimal("123.45"), found.getPrice());
        assertEquals(new BigDecimal("67.89"), found.getCost());
        assertEquals(categoryId, found.getCategory().getId());
        assertEquals("Peripherals", found.getCategory().getName());
    }

    private Category saveCategory(String name) {
        return categoryRepository.saveAndFlush(new Category(name, null));
    }

    private Product product(String name, String sku, boolean active, Category category) {
        return new Product(
                name,
                sku,
                null,
                new BigDecimal("123.45"),
                new BigDecimal("67.89"),
                10,
                2,
                active,
                category
        );
    }
}
