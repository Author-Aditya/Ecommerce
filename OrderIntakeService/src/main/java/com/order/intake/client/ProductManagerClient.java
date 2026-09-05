package com.order.intake.client;

import com.order.intake.dto.product.ProductResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

@FeignClient(name = "ProductManager", url = "${product.manager.url:http://localhost:8081}")
public interface ProductManagerClient {

    @GetMapping("/api/v1/products/{productId}")
    ProductResponse getProductById(@PathVariable String productId);
}
