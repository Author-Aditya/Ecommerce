package com.order.processing.service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Inventory {

    @Id
    @Column(name = "product_id", nullable = false, length = 100)
    private String productId;

    @Column(name = "quantity", nullable = false)
    private Integer qty;

    @Column(name = "category", length = 100)
    private String category;
}
