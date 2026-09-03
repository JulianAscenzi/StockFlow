package com.julianas.stockflow.category;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=validate")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class CategoryRepositoryTest {

    @Container
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void configurePostgresql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRESQL::getUsername);
        registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    }

    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    void savesCategoryAndAssignsId() {
        Category saved = categoryRepository.saveAndFlush(new Category("Electronics", "Electronic products"));

        assertNotNull(saved.getId());
    }

    @Test
    void findsByNameIgnoringCase() {
        categoryRepository.saveAndFlush(new Category("Electronics", null));

        Category found = categoryRepository.findByNameIgnoreCase("eLeCtRoNiCs").orElseThrow();

        assertEquals("Electronics", found.getName());
    }

    @Test
    void checksExistenceByNameIgnoringCase() {
        categoryRepository.saveAndFlush(new Category("Electronics", null));

        assertTrue(categoryRepository.existsByNameIgnoreCase("ELECTRONICS"));
    }

    @Test
    void databaseRejectsNamesThatDifferOnlyByCase() {
        categoryRepository.saveAndFlush(new Category("Electronics", null));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> categoryRepository.saveAndFlush(new Category("ELECTRONICS", null))
        );
    }
}
