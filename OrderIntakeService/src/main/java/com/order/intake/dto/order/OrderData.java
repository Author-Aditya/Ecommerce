package com.order.intake.dto.order;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderData {
    private String orderId;
    private String userId;
    private String productId;
    private Integer quantity;
}
