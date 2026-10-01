package com.julianas.stockflow.sale.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import com.julianas.stockflow.common.api.PageResponse;
import com.julianas.stockflow.sale.Sale;
import com.julianas.stockflow.sale.IdempotentSaleService;
import org.springframework.web.bind.annotation.RequestHeader;
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
    private final IdempotentSaleService idempotentSales;

    public SaleController(SaleService saleService, SaleMapper saleMapper, IdempotentSaleService idempotentSales) {
        this.saleService = saleService;
        this.saleMapper = saleMapper;
        this.idempotentSales = idempotentSales;
    }

    @GetMapping
    public PageResponse<SaleSummaryResponse> history(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return PageResponse.from(
                saleService.history(page, size).map(saleMapper::toSummary));
    }

    @GetMapping("/{id}")
    public SaleResponse detail(@PathVariable Long id) {
        return saleMapper.toResponse(saleService.detail(id));
    }

    @PostMapping
    public ResponseEntity<SaleResponse> confirm(@Valid @RequestBody SaleCreateRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        var lines = request.items().stream()
                .map(item -> new SaleService.SaleLine(item.productId(), item.quantity())).toList();
        Sale sale = key == null ? saleService.confirm(request.notes(), lines)
                : idempotentSales.confirm(key, request.notes(), lines);
        SaleResponse response = saleMapper.toResponse(sale);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }
}
