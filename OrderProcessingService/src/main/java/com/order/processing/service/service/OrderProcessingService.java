package com.order.processing.service.service;

import com.order.processing.service.dto.OrderData;
import com.order.processing.service.dto.StatusEvent;
import com.order.processing.service.entity.Inventory;
import com.order.processing.service.entity.Order;
import com.order.processing.service.repository.InventoryRepository;
import com.order.processing.service.repository.OrderRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class OrderProcessingService {

    private final OrderRepository orderRepository;
    private final InventoryRepository inventoryRepository;
    private final RabbitTemplate rabbitTemplate;

    @Value("${spring.rabbitmq.exchange.order.status}")
    private String orderStatusExchange;

    public OrderProcessingService(OrderRepository orderRepository, InventoryRepository inventoryRepository, RabbitTemplate rabbitTemplate) {
        this.orderRepository = orderRepository;
        this.inventoryRepository = inventoryRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = "${spring.rabbitmq.queue.order.requests}")
    @Transactional
    public void processOrder(OrderData orderData) {
        String orderId = orderData.getOrderId();
        String productId = orderData.getProductId();
        Integer quantity = orderData.getQuantity();

        System.out.println("Processing order: " + orderId + " for product: " + productId);

        // Phase 1: Idempotency Check - Try to insert the order
        try {
            Order initialOrder = Order.builder()
                    .orderId(orderId)
                    .userId(orderData.getUserId())
                    .productId(productId)
                    .status("INITIATED")
                    .updatedAt(LocalDateTime.now())
                    .build();
            orderRepository.save(initialOrder);
        } catch (Exception e) {
            // Duplicate key exception - message is a redelivery, treat as no-op
            System.out.println("Order " + orderId + " already processed (idempotency check)");
            return;
        }

        // Phase 2: Stock Check & Reservation
        Boolean isHighDemand = checkIfHighDemand(productId);
        boolean stockReserved = false;

        if (isHighDemand != null && isHighDemand) {
            // Redis Path (Hot Product) - using Lua script logic
            stockReserved = reserveStockViaRedis(productId, quantity);
        } else {
            // DB Path (Default) - atomic conditional update
            stockReserved = reserveStockViaDb(productId, quantity);
        }

        if (!stockReserved) {
            // Stock reservation failed - publish OUT_OF_STOCK
            publishStatusEvent(orderId, "OUT_OF_STOCK");
            updateOrderStatus(orderId, "OUT_OF_STOCK");
            System.out.println("Order " + orderId + " failed: Out of stock");
            return;
        }

        // Stock confirmed - publish STOCK_CONFIRMED
        publishStatusEvent(orderId, "STOCK_CONFIRMED");
        updateOrderStatus(orderId, "STOCK_CONFIRMED");

        // Publish PROCESSING_PAYMENT
        publishStatusEvent(orderId, "PROCESSING_PAYMENT");
        updateOrderStatus(orderId, "PROCESSING_PAYMENT");

        // Phase 3: Payment Processing (External Service - simulated)
        boolean paymentSuccess = processPayment(orderData);

        if (paymentSuccess) {
            // Payment successful
            publishStatusEvent(orderId, "PAYMENT_CONFIRMED");
            updateOrderStatus(orderId, "PAYMENT_CONFIRMED");
            System.out.println("Order " + orderId + " completed successfully");
        } else {
            // Payment failed - revert stock
            revertStock(productId, quantity, isHighDemand != null && isHighDemand);
            publishStatusEvent(orderId, "PAYMENT_FAILED");
            updateOrderStatus(orderId, "PAYMENT_FAILED");
            System.out.println("Order " + orderId + " failed: Payment failed");
        }
    }

    private Boolean checkIfHighDemand(String productId) {
        // All products use DB path for now
        // Redis path can be enabled by adding is_high_demand column to products table
        return false;
    }

    private boolean reserveStockViaDb(String productId, int quantity) {
        // First check if inventory exists and current stock
        Inventory inventory = inventoryRepository.findById(productId).orElse(null);
        if (inventory == null) {
            System.out.println("No inventory found for product: " + productId);
            return false;
        }
        System.out.println("Current stock for product " + productId + ": " + inventory.getQty() + ", Requested: " + quantity);
        
        int rowsAffected = inventoryRepository.updateQuantityIfExists(productId, quantity);
        System.out.println("Rows affected: " + rowsAffected);
        
        // Check if stock was actually updated
        Inventory updatedInventory = inventoryRepository.findById(productId).orElse(null);
        if (updatedInventory != null) {
            System.out.println("Stock after update: " + updatedInventory.getQty());
        }
        
        return rowsAffected > 0;
    }

    private boolean reserveStockViaRedis(String productId, int quantity) {
        // Simulating Redis Lua script execution
        // In production, this would use RedisTemplate with EVAL command
        // For now, we'll use the same DB logic as a placeholder
        // The actual Redis implementation would use:
        // String script = "local current_stock = tonumber(redis.call('GET', KEYS[1]))\n" +
        //         "local req_qty = tonumber(ARGV[1])\n" +
        //         "if current_stock and current_stock >= req_qty then\n" +
        //         "    redis.call('DECRBY', KEYS[1], req_qty)\n" +
        //         "    return 1\n" +
        //         "else\n" +
        //         "    return 0\n" +
        //         "end";
        // return (Long) redisTemplate.execute(script, Collections.singletonList("stock:" + productId), String.valueOf(quantity)) == 1L;
        
        // Using DB as fallback for now
        return reserveStockViaDb(productId, quantity);
    }

    private void revertStock(String productId, int quantity, boolean isHighDemand) {
        if (isHighDemand) {
            // Redis Path: INCRBY stock:{product_id} 1
            // In production: redisTemplate.opsForValue().increment("stock:" + productId, quantity);
            System.out.println("Reverting stock via Redis for product: " + productId);
        } else {
            // DB Path: UPDATE inventory SET qty = qty + 1 WHERE product_id = {id}
            inventoryRepository.incrementQuantity(productId, quantity);
            System.out.println("Reverting stock via DB for product: " + productId);
        }
    }

    private boolean processPayment(OrderData orderData) {
        // Simulate external payment gateway call
        // In production, this would call the actual payment service
        // For now, we'll assume payment always succeeds
        // Add retry logic and error handling for production
        
        System.out.println("Processing payment for order: " + orderData.getOrderId());
        
        // Simulate payment processing delay
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // In production, this would return the actual payment result
        return true;
    }

    private void publishStatusEvent(String orderId, String status) {
        StatusEvent statusEvent = StatusEvent.builder()
                .eventType("STATUS_UPDATE")
                .orderId(orderId)
                .status(status)
                .timestamp(System.currentTimeMillis())
                .build();
        
        rabbitTemplate.convertAndSend(orderStatusExchange, "", statusEvent);
        System.out.println("Published status event: " + status + " for order: " + orderId);
    }

    private void updateOrderStatus(String orderId, String status) {
        orderRepository.findById(orderId).ifPresent(order -> {
            order.setStatus(status);
            order.setUpdatedAt(LocalDateTime.now());
            orderRepository.save(order);
        });
    }
}
