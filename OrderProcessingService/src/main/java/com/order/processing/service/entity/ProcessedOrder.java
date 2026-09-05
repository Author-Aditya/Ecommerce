package com.order.processing.service.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedOrder {

    @Id
    @Column(name = "order_id", nullable = false, length = 100)
    private String orderId;

    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;

    @Column(name = "product_id", nullable = false, length = 100)
    private String productId;

    @Column(name = "status", nullable = false, length = 50)
    private String status;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "payment_token", length = 200)
    private String paymentToken;
}
