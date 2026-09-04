package com.julianas.stockflow.product.api;

import com.julianas.stockflow.common.api.PageResponse;
import com.julianas.stockflow.product.Product;
import com.julianas.stockflow.product.ProductService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;
    private final ProductMapper productMapper;

    public ProductController(ProductService productService, ProductMapper productMapper) {
        this.productService = productService;
        this.productMapper = productMapper;
    }

    @PostMapping
    public ResponseEntity<ProductResponse> create(
            @Valid @RequestBody ProductCreateRequest request
    ) {
        Product product = productService.create(
                request.name(),
                request.sku(),
                request.description(),
                request.price(),
                request.cost(),
                request.minimumStock(),
                request.categoryId()
        );
        ProductResponse response = productMapper.toResponse(product);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public ProductResponse getById(@PathVariable Long id) {
        return productMapper.toResponse(productService.getById(id));
    }

    @GetMapping
    public PageResponse<ProductResponse> findAll(
            @PageableDefault(size = 20, sort = "name") Pageable pageable
    ) {
        return productMapper.toPageResponse(productService.findAll(pageable));
    }

    @GetMapping("/search")
    public PageResponse<ProductResponse> searchByName(
            @RequestParam(defaultValue = "") String name,
            @PageableDefault(size = 20, sort = "name") Pageable pageable
    ) {
        return productMapper.toPageResponse(productService.searchByName(name, pageable));
    }

    @GetMapping("/category/{categoryId}")
    public PageResponse<ProductResponse> findByCategory(
            @PathVariable Long categoryId,
            @PageableDefault(size = 20, sort = "name") Pageable pageable
    ) {
        return productMapper.toPageResponse(productService.findByCategory(categoryId, pageable));
    }

    @GetMapping("/active")
    public PageResponse<ProductResponse> findActive(
            @PageableDefault(size = 20, sort = "name") Pageable pageable
    ) {
        return productMapper.toPageResponse(productService.findActive(pageable));
    }

    @PutMapping("/{id}")
    public ProductResponse update(
            @PathVariable Long id,
            @Valid @RequestBody ProductUpdateRequest request
    ) {
        Product product = productService.update(
                id,
                request.name(),
                request.sku(),
                request.description(),
                request.price(),
                request.cost(),
                request.minimumStock(),
                request.categoryId()
        );
        return productMapper.toResponse(product);
    }

    @PatchMapping("/{id}/activate")
    public ProductResponse activate(@PathVariable Long id) {
        return productMapper.toResponse(productService.activate(id));
    }

    @PatchMapping("/{id}/deactivate")
    public ProductResponse deactivate(@PathVariable Long id) {
        return productMapper.toResponse(productService.deactivate(id));
    }
}
