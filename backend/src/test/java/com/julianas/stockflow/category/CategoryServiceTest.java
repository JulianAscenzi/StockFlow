package com.julianas.stockflow.category;

import com.julianas.stockflow.product.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private ProductRepository productRepository;

    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryService = new CategoryService(categoryRepository, productRepository);
    }

    @Test
    void createsValidCategory() {
        when(categoryRepository.save(org.mockito.ArgumentMatchers.any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Category created = categoryService.create("Electronics", "Electronic products");

        assertEquals("Electronics", created.getName());
        assertEquals("Electronic products", created.getDescription());
        verify(categoryRepository).existsByNameIgnoreCase("Electronics");
        verify(categoryRepository).save(created);
    }

    @Test
    void normalizesNameWhitespace() {
        when(categoryRepository.save(org.mockito.ArgumentMatchers.any(Category.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Category created = categoryService.create("  Electronics  ", null);

        assertEquals("Electronics", created.getName());
        verify(categoryRepository).existsByNameIgnoreCase("Electronics");
    }

    @Test
    void rejectsBlankName() {
        assertThrows(
                IllegalArgumentException.class,
                () -> categoryService.create("   ", null)
        );

        verify(categoryRepository, never()).existsByNameIgnoreCase(org.mockito.ArgumentMatchers.anyString());
        verify(categoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsDuplicateNameIgnoringCase() {
        when(categoryRepository.existsByNameIgnoreCase("electronics")).thenReturn(true);

        assertThrows(
                DuplicateCategoryNameException.class,
                () -> categoryService.create(" electronics ", null)
        );

        verify(categoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void getsExistingCategory() {
        Category category = new Category("Electronics", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        Category result = categoryService.getById(1L);

        assertSame(category, result);
    }

    @Test
    void throwsWhenCategoryDoesNotExist() {
        when(categoryRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(CategoryNotFoundException.class, () -> categoryService.getById(99L));
    }

    @Test
    void listsCategoriesUsingProvidedPageable() {
        Pageable pageable = PageRequest.of(1, 2);
        Page<Category> page = new PageImpl<>(List.of(new Category("Furniture", null)));
        when(categoryRepository.findAll(pageable)).thenReturn(page);

        Page<Category> result = categoryService.findAll(pageable);

        assertSame(page, result);
        verify(categoryRepository).findAll(pageable);
    }

    @Test
    void updatesNameAndDescription() {
        Category category = new Category("Electronics", "Old description");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.save(category)).thenReturn(category);

        Category updated = categoryService.update(1L, "  Home appliances  ", "New description");

        assertSame(category, updated);
        assertEquals("Home appliances", updated.getName());
        assertEquals("New description", updated.getDescription());
        verify(categoryRepository).existsByNameIgnoreCase("Home appliances");
        verify(categoryRepository).save(category);
    }

    @Test
    void allowsKeepingCurrentNameIgnoringCase() {
        Category category = new Category("Electronics", "Old description");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.save(category)).thenReturn(category);

        Category updated = categoryService.update(1L, "  ELECTRONICS  ", "New description");

        assertEquals("ELECTRONICS", updated.getName());
        assertEquals("New description", updated.getDescription());
        verify(categoryRepository, never()).existsByNameIgnoreCase(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsAnotherCategoryName() {
        Category category = new Category("Electronics", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(categoryRepository.existsByNameIgnoreCase("Furniture")).thenReturn(true);

        assertThrows(
                DuplicateCategoryNameException.class,
                () -> categoryService.update(1L, " Furniture ", null)
        );

        assertEquals("Electronics", category.getName());
        verify(categoryRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void deletesCategoryWithoutProducts() {
        Category category = new Category("Electronics", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));

        categoryService.delete(1L);

        verify(productRepository).existsByCategoryId(1L);
        verify(categoryRepository).delete(category);
        verify(categoryRepository).flush();
    }

    @Test
    void rejectsDeletingCategoryWithProducts() {
        Category category = new Category("Electronics", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.existsByCategoryId(1L)).thenReturn(true);

        assertThrows(CategoryInUseException.class, () -> categoryService.delete(1L));
    }

    @Test
    void doesNotDeleteCategoryWhenItHasProducts() {
        Category category = new Category("Electronics", null);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        when(productRepository.existsByCategoryId(1L)).thenReturn(true);

        assertThrows(CategoryInUseException.class, () -> categoryService.delete(1L));

        verify(categoryRepository, never()).delete(org.mockito.ArgumentMatchers.any());
        verify(categoryRepository, never()).flush();
    }

    @Test
    void translatesDeleteConstraintRaceToCategoryInUse() {
        Category category = new Category("Electronics", null);
        DataIntegrityViolationException databaseException =
                new DataIntegrityViolationException("foreign key violation");
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category));
        org.mockito.Mockito.doThrow(databaseException).when(categoryRepository).flush();

        CategoryInUseException exception = assertThrows(
                CategoryInUseException.class,
                () -> categoryService.delete(1L)
        );

        assertSame(databaseException, exception.getCause());
        verify(categoryRepository).delete(category);
    }
}
