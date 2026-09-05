package com.product.manager.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductRequest {
    private String productId;
    private String productName;
    private String description;
    private Double price;
    private Integer quantity;
    private String category;
}
