# E-Commerce Order Processing Platform

An event-driven, microservices-based e-commerce order processing platform built with **Java 17**, **Spring Boot 3.5.5**, **RabbitMQ**, and **PostgreSQL**[cite: 1]. The system is engineered to solve critical e-commerce operational bottlenecks including stock race conditions, service coupling, non-idempotent operations, and handling traffic spikes[cite: 1].

---

## 📌 Architecture & Services Overview

The platform consists of three independent Spring Boot applications communicating asynchronously via RabbitMQ[cite: 1]:

| Service | Port | Database | Primary Role |
| :--- | :--- | :--- | :--- |
| **`OrderIntakeService`** | `8080` | `orderdb` | Accepts REST orders, validates input, publishes to RabbitMQ, and streams real-time SSE status updates to clients[cite: 1]. |
| **`ProductManager`** | `8081` | `productdb` | Handles product catalog and inventory management CRUD operations[cite: 1]. |
| **`OrderProcessingService`** | `8082` *(No HTTP)* | `productdb` | Background worker that consumes order events, atomically reserves stock, simulates payments, and publishes status updates[cite: 1]. |

### System Architecture Diagram
```text
  Customer
     |  POST /api/v1/orders
     v
+-----------------------+   REST (validate)    +----------------------+
| OrderIntakeService    | -------------------> | ProductManager       |
| (Port 8080)           |                      | (Port 8081)          |
| DB: orderdb           |                      | DB: productdb        |
+-----------+-----------+                      +----------------------+
            |
            | publishes OrderData (Jackson JSON)
            v
+--------------------------------------------------------------+
| RabbitMQ  (vhost: order)                                     |
|   order.requests.exchange (DIRECT) -> order.requests queue   |
|   order.status.exchange   (FANOUT) -> order.status.queue     |
|   order.deadletter.exchange (DIRECT) -> order.deadletter     |
+--------------------------------------------------------------+
            | consumes (prefetch = 10, concurrency = 10)
            v
+-----------------------+   atomic DB row lock   +--------------------------+
| OrderProcessingService| ---------------------> | PostgreSQL: productdb    |
| (Port 8082, no HTTP)  |   UPDATE products ...  |  orders + products       |
+-----------+-----------+   WHERE qty >= ?       +--------------------------+
            |
            | publishes StatusEvent (fanout) --> Intake --> SSE --> Customer
```[cite: 1]

---

## 🛠️ Problems Solved & Technical Solutions

### 1. Stock Race Conditions & Overselling
* **Problem**: Naive read-then-write logic allows concurrent orders to read identical stock counts, leading to negative inventory and overselling[cite: 1].
* **Solution**: Stock reservation uses a single, atomic SQL statement executed inside `@Transactional` consumer methods[cite: 1]:
  ```sql
  UPDATE products
     SET quantity = quantity - ?2
   WHERE product_id = ?1
     AND quantity >= ?2;
  ```[cite: 1]
  PostgreSQL enforces a **row-level lock** during concurrent `UPDATE` calls[cite: 1]. If stock is sufficient, `rowsAffected = 1`[cite: 1]. If insufficient, `rowsAffected = 0` and the order transitions to `OUT_OF_STOCK` without acquiring locks across steps[cite: 1].

### 2. Duplicate Deliveries (At-Least-Once Delivery Idempotency)
* **Problem**: Network retry blips or consumer restarts can cause RabbitMQ to re-deliver the same message, leading to duplicate stock deductions or charges[cite: 1].
* **Solution**: The `orders` database table utilizes `order_id` as its `PRIMARY KEY`[cite: 1]. The initial step in message processing inserts an `INITIATED` record[cite: 1]. Redelivered messages trigger a unique constraint violation, which is caught and treated safely as a no-op[cite: 1].

### 3. Synchronous Thread Exhaustion & Traffic Spikes
* **Problem**: Blocking HTTP calls during peak sales exhaust web server thread pools[cite: 1].
* **Solution**: `OrderIntakeService` immediately returns an `HTTP 202 Accepted` response upon placing the message onto a durable RabbitMQ queue (`order.requests`)[cite: 1]. Background processing workers pull messages at their own rate (`prefetch=10`, `concurrency=10`)[cite: 1].

### 4. Live Progress Tracking via Server-Sent Events (SSE)
* **Problem**: Customers lack visibility into background order verification and processing phases[cite: 1].
* **Solution**: Worker events published to `order.status.exchange` (FANOUT) are picked up by intake services and pushed continuously to the client browser over an open SSE connection[cite: 1].
  * Lifecycle: `INITIATED` ➔ `STOCK_CONFIRMED` ➔ `PROCESSING_PAYMENT` ➔ `PAYMENT_CONFIRMED` (or `OUT_OF_STOCK` / `PAYMENT_FAILED`)[cite: 1].

---

## 🚀 RabbitMQ Topology Details

* **VHost**: `order`[cite: 1]
* **`order.requests.exchange` (DIRECT)**: Routes incoming orders into `order.requests` queue[cite: 1].
* **`order.status.exchange` (FANOUT)**: Broadcasts lifecycle status events to `order.status.queue` for intake instances to forward over SSE[cite: 1].
* **`order.deadletter.exchange` (DIRECT)**: Captures unhandled or poison messages into `order.deadletter` for inspection[cite: 1].

---

## 🧰 Tech Stack

* **Language & Framework**: Java 17, Spring Boot 3.5.5, Maven[cite: 1]
* **Database & Persistence**: PostgreSQL (`orderdb`, `productdb`), Spring Data JPA, Hibernate, HikariCP[cite: 1]
* **Messaging**: RabbitMQ, Spring AMQP, Jackson JSON Serialization[cite: 1]
* **API & Real-time Communication**: REST APIs, Server-Sent Events (SSE), OpenAPI/Swagger[cite: 1]
* **Utilities**: Lombok, Embedded Tomcat[cite: 1]

---

## 📁 Repository Directory Structure

```text
D:\Ecommerce\
├── OrderIntakeService\          # REST API + RabbitMQ producer + SSE streaming
├── ProductManager\              # Product catalog & inventory CRUD REST API
├── OrderProcessingService\      # RabbitMQ worker/consumer (No HTTP)
├── OrderProcessingService_HLD_LLD.md # High/Low Level Design Document
└── RabbitMQ_Explained.txt       # Architecture explainer documentation
```[cite: 1]

---

## ⚙️ Running Locally

### Prerequisites
* Java 17+[cite: 1]
* Maven[cite: 1]
* PostgreSQL running locally[cite: 1]
* RabbitMQ running with Management plugin (`localhost:15672`)[cite: 1]

### Execution Steps
1. **Configure RabbitMQ**: Start RabbitMQ and create a virtual host named `order`[cite: 1].
2. **Start ProductManager**:
   ```bash
   cd ProductManager
   ./mvnw spring-boot:run
   ```[cite: 1]
3. **Start OrderIntakeService**:
   ```bash
   cd OrderIntakeService
   ./mvnw spring-boot:run
   ```[cite: 1]
4. **Start OrderProcessingService**:
   ```bash
   cd OrderProcessingService
   ./mvnw spring-boot:run
   ```[cite: 1]
5. **Place an Order**:
   ```bash
   curl -X POST http://localhost:8080/api/v1/orders \
        -H "Content-Type: application/json" \
        -d '{"userId":"usr_1","productId":"PROD-A","quantity":2}'
   ```[cite: 1]

---

## 🔍 Future Scope for Improvement

* **Redis Fast-Path Integration**: Introduce atomic Lua scripting in Redis for flash-sale stock management to offload hot row contention from PostgreSQL[cite: 1].
* **Transactional Outbox Pattern**: Decouple database updates from RabbitMQ publishing to ensure zero message loss even during crashes[cite: 1].
* **Decoupled Payment Sagas**: Move external payment processing calls out of the stock reservation transaction to shorten DB lock durations[cite: 1].
* **Partitioned Queues**: Implement `x-consistent-hash` exchange routing by `product_id` to shard worker execution[cite: 1].

---

## 📊 Summary Matrix

| Issue | Technical Root Cause | Project Solution |
| :--- | :--- | :--- |
| **Overselling** | Concurrent read-check-write race conditions[cite: 1] | Atomic `UPDATE ... WHERE qty >= ?` with PostgreSQL row locks[cite: 1] |
| **Double Deductions** | Message broker at-least-once re-deliveries[cite: 1] | `order_id` Primary Key enforcement treating duplicates as no-ops[cite: 1] |
| **Blocked Web Threads** | Synchronous waits on payments/inventory[cite: 1] | Asynchronous hand-off via RabbitMQ returning `HTTP 202`[cite: 1] |
| **Traffic Spikes** | Unbuffered request flooding[cite: 1] | Durable queue buffering with regulated worker concurrency[cite: 1] |
| **Lack of Feedback** | Asynchronous execution blindness[cite: 1] | Fanout status exchanges streaming continuous updates via SSE[cite: 1] |
