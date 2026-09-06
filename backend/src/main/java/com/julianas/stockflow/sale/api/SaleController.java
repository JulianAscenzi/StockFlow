package com.julianas.stockflow.sale.api;

import com.julianas.stockflow.sale.Sale;
import com.julianas.stockflow.sale.SaleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;

@RestController
@RequestMapping("/api/sales")
public class SaleController {

    private final SaleService saleService;
    private final SaleMapper saleMapper;

    public SaleController(SaleService saleService, SaleMapper saleMapper) {
        this.saleService = saleService;
        this.saleMapper = saleMapper;
    }

    @PostMapping
    public ResponseEntity<SaleResponse> confirm(@Valid @RequestBody SaleCreateRequest request) {
        Sale sale = saleService.confirm(
                request.notes(),
                request.items().stream().map(item -> new SaleService.SaleLine(item.productId(), item.quantity())).toList()
        );
        SaleResponse response = saleMapper.toResponse(sale);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }
}
