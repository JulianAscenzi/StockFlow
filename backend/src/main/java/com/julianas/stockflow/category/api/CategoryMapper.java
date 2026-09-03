package com.julianas.stockflow.category.api;

import com.julianas.stockflow.category.Category;
import com.julianas.stockflow.common.api.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public class CategoryMapper {

    public CategoryResponse toResponse(Category category) {
        Objects.requireNonNull(category, "category must not be null");

        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }

    public PageResponse<CategoryResponse> toPageResponse(Page<Category> page) {
        Objects.requireNonNull(page, "page must not be null");

        return PageResponse.from(page.map(this::toResponse));
    }
}
