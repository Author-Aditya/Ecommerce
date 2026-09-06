package com.order.processing.service.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatusEvent {
    private String eventType;
    private String orderId;
    private String status;
    private Long timestamp;
}
