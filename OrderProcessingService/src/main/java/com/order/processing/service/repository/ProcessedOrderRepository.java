package com.order.processing.service.repository;

import com.order.processing.service.entity.ProcessedOrder;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProcessedOrderRepository extends JpaRepository<ProcessedOrder, String> {
    Optional<ProcessedOrder> findByOrderId(String orderId);
}
