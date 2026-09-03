package com.julianas.stockflow.product;

import com.julianas.stockflow.category.Category;
import com.julianas.stockflow.category.CategoryNotFoundException;
import com.julianas.stockflow.category.CategoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    private static final Long CATEGORY_ID = 10L;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, categoryRepository);
    }

    @Test
    void createsValidProductWithZeroStockAndActiveState() {
        Category category = configureValidCreation();

        Product created = createValidProduct();

        assertEquals("Mouse", created.getName());
        assertEquals("MOU-01", created.getSku());
        assertEquals(0, created.getStock());
        assertTrue(created.isActive());
        assertSame(category, created.getCategory());
        verify(productRepository).save(created);
    }

    @Test
    void normalizesNameAndSkuWhenCreating() {
        configureValidCreation();

        Product created = productService.create(
                "  Wireless Mouse  ",
                "  mou-01  ",
                "Description",
                new BigDecimal("25.50"),
                new BigDecimal("15.25"),
                2,
                CATEGORY_ID
        );

        assertEquals("Wireless Mouse", created.getName());
        assertEquals("MOU-01", created.getSku());
        verify(productRepository).existsBySkuIgnoreCase("MOU-01");
    }

    @Test
    void rejectsBlankName() {
        assertThrows(IllegalArgumentException.class, () -> productService.create(
                "   ", "MOU-01", null, BigDecimal.ONE, BigDecimal.ZERO, 0, CATEGORY_ID
        ));

        verify(productRepository, never()).save(any());
    }

    @Test
    void rejectsBlankSku() {
        assertThrows(IllegalArgumentException.class, () -> productService.create(
                "Mouse", "   ", null, BigDecimal.ONE, BigDecimal.ZERO, 0, CATEGORY_ID
        ));

        verify(productRepository, never()).save(any());
    }

    @Test
    void rejectsDuplicateSkuIgnoringCase() {
        when(productRepository.existsBySkuIgnoreCase("MOU-01")).thenReturn(true);

        assertThrows(DuplicateProductSkuException.class, () -> productService.create(
                "Mouse", " mou-01 ", null, BigDecimal.ONE, BigDecimal.ZERO, 0, CATEGORY_ID
        ));

        verify(productRepository, never()).save(any());
    }

    @Test
    void rejectsNullPrice() {
        assertThrows(NullPointerException.class, () -> productService.create(
                "Mouse", "MOU-01", null, null, BigDecimal.ZERO, 0, CATEGORY_ID
        ));

        verify(productRepository, never()).save(any());
    }

    @Test
    void rejectsNegativePriceUsingNumericComparison() {
        assertThrows(IllegalArgumentException.class, () -> productService.create(
                "Mouse", "MOU-01", null, new BigDecimal("-0.01"), BigDecimal.ZERO, 0, CATEGORY_ID
        ));
        verify(productRepository, never()).save(any());
    }

    @Test
    void acceptsNumericallyZeroPriceWithDifferentScale() {
        configureValidCreation();

        Product created = productService.create(
                "Mouse", "MOU-01", null, new BigDecimal("0.00"), BigDecimal.ZERO, 0, CATEGORY_ID
        );

        assertEquals(0, created.getPrice().compareTo(BigDecimal.ZERO));
    }

    @Test
    void rejectsNullCost() {
        assertThrows(NullPointerException.class, () -> productService.create(
                "Mouse", "MOU-01", null, BigDecimal.ONE, null, 0, CATEGORY_ID
        ));

        verify(productRepository, never()).save(any());
    }

    @Test
    void rejectsNegativeCost() {
        assertThrows(IllegalArgumentException.class, () -> productService.create(
                "Mouse", "MOU-01", null, BigDecimal.ONE, new BigDecimal("-0.01"), 0, CATEGORY_ID
        ));

        verify(productRepository, never()).save(any());
    }

    @Test
    void rejectsNullMinimumStock() {
        assertThrows(NullPointerException.class, () -> productService.create(
                "Mouse", "MOU-01", null, BigDecimal.ONE, BigDecimal.ZERO, null, CATEGORY_ID
        ));

        verify(productRepository, never()).save(any());
    }

    @Test
    void rejectsNegativeMinimumStock() {
        assertThrows(IllegalArgumentException.class, () -> productService.create(
                "Mouse", "MOU-01", null, BigDecimal.ONE, BigDecimal.ZERO, -1, CATEGORY_ID
        ));

        verify(productRepository, never()).save(any());
    }

    @Test
    void rejectsNullCategoryId() {
        assertThrows(NullPointerException.class, () -> productService.create(
                "Mouse", "MOU-01", null, BigDecimal.ONE, BigDecimal.ZERO, 0, null
        ));

        verify(productRepository, never()).save(any());
    }

    @Test
    void rejectsMissingCategoryWhenCreating() {
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.empty());

        assertThrows(CategoryNotFoundException.class, () -> createValidProduct());

        verify(productRepository, never()).save(any());
    }

    @Test
    void getsExistingProduct() {
        Product product = existingProduct(new Category("Peripherals", null), true);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        Product result = productService.getById(1L);

        assertSame(product, result);
    }

    @Test
    void throwsWhenProductDoesNotExist() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ProductNotFoundException.class, () -> productService.getById(99L));
    }

    @Test
    void listsProductsUsingProvidedPageable() {
        Pageable pageable = PageRequest.of(1, 2);
        Page<Product> page = new PageImpl<>(List.of(existingProduct(new Category("A", null), true)));
        when(productRepository.findAll(pageable)).thenReturn(page);

        Page<Product> result = productService.findAll(pageable);

        assertSame(page, result);
        verify(productRepository).findAll(pageable);
    }

    @Test
    void findsByCategoryUsingProvidedPageable() {
        Pageable pageable = PageRequest.of(2, 3);
        Page<Product> page = new PageImpl<>(List.of());
        when(categoryRepository.existsById(CATEGORY_ID)).thenReturn(true);
        when(productRepository.findByCategoryId(CATEGORY_ID, pageable)).thenReturn(page);

        Page<Product> result = productService.findByCategory(CATEGORY_ID, pageable);

        assertSame(page, result);
        verify(productRepository).findByCategoryId(CATEGORY_ID, pageable);
    }

    @Test
    void rejectsMissingCategoryBeforeSearchingProducts() {
        Pageable pageable = PageRequest.of(0, 10);
        when(categoryRepository.existsById(CATEGORY_ID)).thenReturn(false);

        assertThrows(
                CategoryNotFoundException.class,
                () -> productService.findByCategory(CATEGORY_ID, pageable)
        );

        verify(productRepository, never()).findByCategoryId(CATEGORY_ID, pageable);
    }

    @Test
    void searchesByNormalizedPartialNameUsingProvidedPageable() {
        Pageable pageable = PageRequest.of(1, 5);
        Page<Product> page = new PageImpl<>(List.of());
        when(productRepository.findByNameContainingIgnoreCase("wireless", pageable)).thenReturn(page);

        Page<Product> result = productService.searchByName("  wireless  ", pageable);

        assertSame(page, result);
        verify(productRepository).findByNameContainingIgnoreCase("wireless", pageable);
    }

    @Test
    void rejectsBlankNameSearch() {
        Pageable pageable = PageRequest.of(0, 10);

        assertThrows(IllegalArgumentException.class, () -> productService.searchByName("   ", pageable));

        verify(productRepository, never()).findByNameContainingIgnoreCase(anyString(), any());
    }

    @Test
    void listsOnlyActiveProductsUsingProvidedPageable() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Product> page = new PageImpl<>(List.of(existingProduct(new Category("A", null), true)));
        when(productRepository.findByActiveTrue(pageable)).thenReturn(page);

        Page<Product> result = productService.findActive(pageable);

        assertSame(page, result);
        verify(productRepository).findByActiveTrue(pageable);
    }

    @Test
    void updatesEditableFields() {
        Category oldCategory = new Category("Old", null);
        Category category = new Category("Peripherals", null);
        Product product = existingProduct(oldCategory, true);
        configureValidUpdate(product, category);

        Product updated = productService.update(
                1L,
                "  Mechanical Keyboard  ",
                "  key-01  ",
                "New description",
                new BigDecimal("120.50"),
                new BigDecimal("80.25"),
                3,
                CATEGORY_ID
        );

        assertSame(product, updated);
        assertEquals("Mechanical Keyboard", updated.getName());
        assertEquals("KEY-01", updated.getSku());
        assertEquals("New description", updated.getDescription());
        assertEquals(new BigDecimal("120.50"), updated.getPrice());
        assertEquals(new BigDecimal("80.25"), updated.getCost());
        assertEquals(3, updated.getMinimumStock());
        verify(productRepository).save(product);
    }

    @Test
    void updatesCategory() {
        Category oldCategory = new Category("Old", null);
        Category newCategory = new Category("New", null);
        Product product = existingProduct(oldCategory, true);
        configureValidUpdate(product, newCategory);

        Product updated = updateWithSku(product.getSku());

        assertSame(newCategory, updated.getCategory());
    }

    @Test
    void preservesStockAndActiveStateWhenUpdating() {
        Category category = new Category("Peripherals", null);
        Product inactiveProduct = existingProduct(category, false);
        configureValidUpdate(inactiveProduct, category);

        Product updated = updateWithSku(inactiveProduct.getSku());

        assertEquals(17, updated.getStock());
        assertFalse(updated.isActive());
    }

    @Test
    void allowsKeepingOwnSkuIgnoringCase() {
        Category category = new Category("Peripherals", null);
        Product product = existingProduct(category, true);
        configureValidUpdate(product, category);

        Product updated = updateWithSku("  mou-01  ");

        assertEquals("MOU-01", updated.getSku());
        verify(productRepository, never()).existsBySkuIgnoreCase(anyString());
    }

    @Test
    void rejectsSkuOwnedByAnotherProduct() {
        Category category = new Category("Peripherals", null);
        Product product = existingProduct(category, true);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.existsBySkuIgnoreCase("KEY-01")).thenReturn(true);

        assertThrows(DuplicateProductSkuException.class, () -> updateWithSku(" key-01 "));

        assertEquals("MOU-01", product.getSku());
        verify(categoryRepository, never()).findById(any());
        verify(productRepository, never()).save(any());
    }

    @Test
    void activatesProduct() {
        Product product = existingProduct(new Category("Peripherals", null), false);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        Product activated = productService.activate(1L);

        assertSame(product, activated);
        assertTrue(activated.isActive());
        assertEquals(17, activated.getStock());
        verify(productRepository).save(product);
    }

    @Test
    void deactivatesProduct() {
        Product product = existingProduct(new Category("Peripherals", null), true);
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(product)).thenReturn(product);

        Product deactivated = productService.deactivate(1L);

        assertSame(product, deactivated);
        assertFalse(deactivated.isActive());
        assertEquals(17, deactivated.getStock());
        verify(productRepository).save(product);
    }

    private Category configureValidCreation() {
        Category category = new Category("Peripherals", null);
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return category;
    }

    private void configureValidUpdate(Product product, Category category) {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(categoryRepository.findById(CATEGORY_ID)).thenReturn(Optional.of(category));
        when(productRepository.save(product)).thenReturn(product);
    }

    private Product createValidProduct() {
        return productService.create(
                "Mouse",
                "MOU-01",
                "Wireless mouse",
                new BigDecimal("25.50"),
                new BigDecimal("15.25"),
                2,
                CATEGORY_ID
        );
    }

    private Product updateWithSku(String sku) {
        return productService.update(
                1L,
                "Mouse",
                sku,
                "Updated",
                new BigDecimal("30.00"),
                new BigDecimal("20.00"),
                4,
                CATEGORY_ID
        );
    }

    private static Product existingProduct(Category category, boolean active) {
        return new Product(
                "Mouse",
                "MOU-01",
                "Wireless mouse",
                new BigDecimal("25.50"),
                new BigDecimal("15.25"),
                17,
                2,
                active,
                category
        );
    }
}
