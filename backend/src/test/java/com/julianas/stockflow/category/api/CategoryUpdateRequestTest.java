package com.julianas.stockflow.category.api;

class CategoryUpdateRequestTest extends CategoryRequestValidationTest<CategoryUpdateRequest> {

    @Override
    CategoryUpdateRequest createRequest(String name, String description) {
        return new CategoryUpdateRequest(name, description);
    }
}
