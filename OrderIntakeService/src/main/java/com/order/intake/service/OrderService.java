package com.order.intake.service;

import com.order.intake.dto.OrderRequest;
import com.order.intake.dto.OrderResponse;
import com.order.intake.entity.OrderEntity;
import com.order.intake.repository.OrderRepository;
import com.order.intake.dto.product.ProductResponse;
import com.order.intake.dto.order.OrderData;
import com.order.intake.client.ProductManagerClient;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final ProductManagerClient productManagerClient;
    private final RabbitTemplate rabbitTemplate;

    @Value("${spring.rabbitmq.exchange.order.requests}")
    private String orderRequestsExchange;

    public OrderService(OrderRepository orderRepository, ProductManagerClient productManagerClient, RabbitTemplate rabbitTemplate) {
        this.orderRepository = orderRepository;
        this.productManagerClient = productManagerClient;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    public OrderResponse placeOrder(OrderRequest request) {
        String orderId = "ord_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);

        // Check product stock via ProductManager service
        ProductResponse productResponse = productManagerClient.getProductById(request.getProductId());

        if (productResponse == null) {
            return OrderResponse.builder()
                    .orderId(orderId)
                    .status("PRODUCT_NOT_FOUND")
                    .message("Product not found")
                    .build();
        }

        // Check stock availability
        if (productResponse.getQuantity() < request.getQuantity()) {
            return OrderResponse.builder()
                    .orderId(orderId)
                    .status("OUT_OF_STOCK")
                    .message("Product is out of stock")
                    .build();
        }

        // Valid order - push to RabbitMQ for further processing
        OrderData orderData = OrderData.builder()
                .orderId(orderId)
                .userId(request.getUserId())
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .build();
        rabbitTemplate.convertAndSend(orderRequestsExchange, "order.requests", orderData);

        // Save order with PENDING status
        OrderEntity order = OrderEntity.builder()
                .orderId(orderId)
                .userId(request.getUserId())
                .productId(request.getProductId())
                .status("PENDING")
                .quantity(request.getQuantity())
                .updatedAt(java.time.LocalDateTime.now())
                .build();

        orderRepository.save(order);

        return OrderResponse.builder()
                .orderId(orderId)
                .status("PENDING")
                .message("Order placed successfully")
                .build();
    }

    @Transactional(readOnly = true)
    public OrderEntity getOrderById(String orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));
    }
}
