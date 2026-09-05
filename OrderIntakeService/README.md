# Order Intake Service

Order Intake Microservice

## Summary
This microservice handles order placement and stock checking. It validates orders against the ProductManager service and pushes valid orders to RabbitMQ for further processing by the OrderProcessingService.

## Features
- Order placement with stock validation
- Product stock checking via ProductManager service
- RabbitMQ integration for order processing
- REST API for order management

## API Endpoints
- `POST /api/v1/orders` - Place a new order
- `GET /api/v1/orders/{orderId}` - Get order status

## Prerequisites
- Java 17
- Maven 3.9+
- PostgreSQL
- RabbitMQ (with `order` virtual host)
- ProductManager service running on port 8081

## Configuration
- Database: PostgreSQL (orderdb)
- RabbitMQ: localhost:5672, virtual host: order
- ProductManager: http://localhost:8081

## Build
```bash
./mvnw clean install
```

## Run
```bash
./mvnw spring-boot:run
```
