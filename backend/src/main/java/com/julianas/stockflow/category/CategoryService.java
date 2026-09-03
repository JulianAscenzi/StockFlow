package com.julianas.stockflow.category;

import com.julianas.stockflow.product.ProductRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    public CategoryService(
            CategoryRepository categoryRepository,
            ProductRepository productRepository
    ) {
        this.categoryRepository = Objects.requireNonNull(categoryRepository, "categoryRepository");
        this.productRepository = Objects.requireNonNull(productRepository, "productRepository");
    }

    @Transactional
    public Category create(String name, String description) {
        String normalizedName = normalizeName(name);
        rejectDuplicateName(normalizedName);

        return categoryRepository.save(new Category(normalizedName, description));
    }

    @Transactional(readOnly = true)
    public Category getById(Long id) {
        Objects.requireNonNull(id, "id");
        return findById(id);
    }

    @Transactional(readOnly = true)
    public Page<Category> findAll(Pageable pageable) {
        return categoryRepository.findAll(Objects.requireNonNull(pageable, "pageable"));
    }

    @Transactional
    public Category update(Long id, String name, String description) {
        Objects.requireNonNull(id, "id");
        String normalizedName = normalizeName(name);
        Category category = findById(id);

        if (!normalizedName.equalsIgnoreCase(category.getName())) {
            rejectDuplicateName(normalizedName);
        }

        category.update(normalizedName, description);
        return categoryRepository.save(category);
    }

    @Transactional
    public void delete(Long id) {
        Objects.requireNonNull(id, "id");
        Category category = findById(id);

        if (productRepository.existsByCategoryId(id)) {
            throw new CategoryInUseException(id);
        }

        try {
            categoryRepository.delete(category);
            categoryRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new CategoryInUseException(id, exception);
        }
    }

    private Category findById(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new CategoryNotFoundException(id));
    }

    private void rejectDuplicateName(String name) {
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateCategoryNameException(name);
        }
    }

    private static String normalizeName(String name) {
        String normalizedName = Objects.requireNonNull(name, "name").trim();
        if (normalizedName.isEmpty()) {
            throw new IllegalArgumentException("Category name must not be blank");
        }
        return normalizedName;
    }
}
