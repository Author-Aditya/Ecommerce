package com.order.processing.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderData {
    private String eventType;
    private String orderId;
    private String userId;
    private String productId;
    private Integer quantity;
    private String paymentToken;
    private Long timestamp;
}
