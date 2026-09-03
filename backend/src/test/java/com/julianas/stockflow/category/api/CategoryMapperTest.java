package com.julianas.stockflow.category.api;

import com.julianas.stockflow.category.Category;
import com.julianas.stockflow.common.api.PageResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CategoryMapperTest {

    private final CategoryMapper categoryMapper = new CategoryMapper();

    @Test
    void mapsAllFieldsToCategoryResponse() {
        Instant createdAt = Instant.parse("2026-01-10T12:00:00Z");
        Instant updatedAt = Instant.parse("2026-02-15T09:30:00Z");
        Category category = mock(Category.class);
        when(category.getId()).thenReturn(7L);
        when(category.getName()).thenReturn("Electronics");
        when(category.getDescription()).thenReturn("Electronic products");
        when(category.getCreatedAt()).thenReturn(createdAt);
        when(category.getUpdatedAt()).thenReturn(updatedAt);

        CategoryResponse response = categoryMapper.toResponse(category);

        assertEquals(7L, response.id());
        assertEquals("Electronics", response.name());
        assertEquals("Electronic products", response.description());
        assertEquals(createdAt, response.createdAt());
        assertEquals(updatedAt, response.updatedAt());
    }

    @Test
    void preservesNullDescription() {
        Category category = mock(Category.class);

        CategoryResponse response = categoryMapper.toResponse(category);

        assertNull(response.description());
    }

    @Test
    void rejectsNullCategory() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> categoryMapper.toResponse(null)
        );

        assertEquals("category must not be null", exception.getMessage());
    }

    @Test
    void mapsPagePreservingContentOrderAndMetadata() {
        Category first = categoryWithIdAndName(11L, "First");
        Category second = categoryWithIdAndName(12L, "Second");
        Page<Category> page = new PageImpl<>(
                List.of(first, second),
                PageRequest.of(1, 2),
                5
        );

        PageResponse<CategoryResponse> response = categoryMapper.toPageResponse(page);

        assertEquals(List.of(11L, 12L), response.content().stream().map(CategoryResponse::id).toList());
        assertEquals(List.of("First", "Second"), response.content().stream().map(CategoryResponse::name).toList());
        assertEquals(1, response.page());
        assertEquals(2, response.size());
        assertEquals(5, response.totalElements());
        assertEquals(3, response.totalPages());
        assertEquals(false, response.first());
        assertEquals(false, response.last());
    }

    @Test
    void rejectsNullPage() {
        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> categoryMapper.toPageResponse(null)
        );

        assertEquals("page must not be null", exception.getMessage());
    }

    private Category categoryWithIdAndName(Long id, String name) {
        Category category = mock(Category.class);
        when(category.getId()).thenReturn(id);
        when(category.getName()).thenReturn(name);
        return category;
    }
}
