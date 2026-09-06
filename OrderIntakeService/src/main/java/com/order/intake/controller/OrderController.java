package com.order.intake.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.order.intake.dto.OrderRequest;
import com.order.intake.dto.OrderResponse;
import com.order.intake.service.OrderService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Order Intake Controller", description = "API for order placement")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    @Operation(summary = "Place a new order", description = "Creates a new order")
    @ApiResponses({
        @ApiResponse(responseCode = "202", description = "Order accepted for processing",
            content = @Content(schema = @Schema(implementation = OrderResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request")
    })
    public ResponseEntity<OrderResponse> placeOrder(@RequestBody OrderRequest request) {
        OrderResponse response = orderService.placeOrder(request);
        return ResponseEntity.accepted().body(response);
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order status", description = "Retrieves current status of an order")
    @ApiResponse(responseCode = "200", description = "Order found")
    public ResponseEntity<OrderResponse> getOrderStatus(@PathVariable String orderId) {
        var order = orderService.getOrderById(orderId);
        return ResponseEntity.ok(OrderResponse.builder()
                .orderId(order.getOrderId())
                .status(order.getStatus())
                .message("Order retrieved successfully")
                .build());
    }
}

