package com.julianas.stockflow.category.api;

class CategoryCreateRequestTest extends CategoryRequestValidationTest<CategoryCreateRequest> {

    @Override
    CategoryCreateRequest createRequest(String name, String description) {
        return new CategoryCreateRequest(name, description);
    }
}
