# 🛒 E-Commerce Order Processing Platform

> **A production-style Spring Boot microservices platform designed to handle high-concurrency orders, prevent overselling, process orders asynchronously, and stream real-time order status to customers.**

<p align="center">

![Java](https://img.shields.io/badge/Java-17-orange?style=for-the-badge&logo=openjdk)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.5-brightgreen?style=for-the-badge&logo=springboot)
![RabbitMQ](https://img.shields.io/badge/RabbitMQ-Async%20Messaging-ff6600?style=for-the-badge&logo=rabbitmq)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-Database-336791?style=for-the-badge&logo=postgresql)
![Maven](https://img.shields.io/badge/Maven-Build-C71A36?style=for-the-badge&logo=apachemaven)

</p>

---

## 🚀 What Is This Project?

This project is a **microservices-based e-commerce order processing platform** built with **Java 17, Spring Boot, RabbitMQ, PostgreSQL, JPA/Hibernate, and SSE**.

The architecture separates **order intake**, **product/inventory management**, and **order processing** into independent services.

The key goal is to solve real-world distributed-system problems such as:

- 🔥 Overselling during concurrent purchases
- ⚡ Slow synchronous order processing
- 🔁 Duplicate message processing
- 📈 Traffic spikes and independent scaling
- 📡 Real-time order status updates
- 💀 Failed/poison messages
- 🔗 Tight coupling between services

---

## 🏗️ Architecture

```text
                         ┌─────────────────┐
                         │    Customer     │
                         └────────┬────────┘
                                  │
                         POST /api/v1/orders
                                  │
                                  ▼
                    ┌──────────────────────────┐
                    │   OrderIntakeService     │
                    │          :8080           │
                    │                          │
                    │ REST API                 │
                    │ Validation               │
                    │ RabbitMQ Producer        │
                    │ SSE Status Streaming     │
                    └───────────┬──────────────┘
                                │
                       OrderData (JSON)
                                │
                                ▼
              ╔════════════════════════════════════╗
              ║              RabbitMQ              ║
              ║                                    ║
              ║ order.requests.exchange            ║
              ║          ↓                         ║
              ║ order.requests                     ║
              ║                                    ║
              ║ order.status.exchange              ║
              ║          ↓                         ║
              ║ order.status.queue                 ║
              ║                                    ║
              ║ order.deadletter.exchange          ║
              ║          ↓                         ║
              ║ order.deadletter                   ║
              ╚══════════════════╤═════════════════╝
                                 │
                         Consume orders
                     concurrency = 10
                        prefetch = 10
                                 │
                                 ▼
                 ┌────────────────────────────┐
                 │ OrderProcessingService     │
                 │          :8082             │
                 │        No HTTP              │
                 │                            │
                 │ Order Consumer              │
                 │ Stock Reservation           │
                 │ Payment Processing          │
                 │ Status Publisher             │
                 └─────────────┬──────────────┘
                               │
                         PostgreSQL
                               │
                    ┌──────────┴──────────┐
                    │                     │
                 orderdb              productdb
                    │                     │
                    ▼                     ▼
              Order Records        Products/Inventory

                 StatusEvent
                      │
                      ▼
              RabbitMQ FANOUT
                      │
                      ▼
              OrderIntakeService
                      │
                     SSE
                      │
                      ▼
                  Customer
```

---

## 🧩 Microservices

| Service | Port | Database | Responsibility |
|---|---:|---|---|
| 📥 **OrderIntakeService** | `8080` | `orderdb` | Accepts orders, validates them, publishes messages and streams status |
| 📦 **ProductManager** | `8081` | `productdb` | Product catalog and inventory CRUD |
| ⚙️ **OrderProcessingService** | `8082` | `productdb` | Consumes orders, reserves stock, processes payment and publishes status |

### Supporting Infrastructure

| Component | Purpose |
|---|---|
| 🐇 RabbitMQ | Asynchronous communication and buffering |
| 🐘 PostgreSQL | Persistent data storage |
| 🔄 Spring Data JPA | Database access |
| 🧱 Hibernate | ORM |
| 📡 SSE | Real-time customer status updates |
| 🔢 Jackson | JSON message serialization |
| 📚 OpenAPI / Swagger | API documentation |

---

# 🎯 Problems & Solutions

## 1. 🔥 Preventing Overselling

A naive implementation can create a race condition:

```text
Stock = 3

Thread A → reads 3
Thread B → reads 3

Thread A → writes 2
Thread B → writes 2

Expected: 1
Actual:   2 customers successfully bought
```

This project avoids the problem with an **atomic conditional UPDATE**:

```sql
UPDATE products
SET quantity = quantity - ?
WHERE product_id = ?
  AND quantity >= ?;
```

The result tells us whether the reservation succeeded:

```text
rowsAffected = 1 → Stock reserved
rowsAffected = 0 → OUT_OF_STOCK
```

### Why is this safe?

PostgreSQL serializes concurrent updates to the same row.

```text
Product A
Quantity = 1

Order 1 ───────┐
               │
               ▼
        Atomic UPDATE
        quantity >= 1
               │
               ▼
        Quantity = 0

Order 2 ───────┐
               │
               ▼
        Atomic UPDATE
        quantity >= 1
               │
               ▼
        rowsAffected = 0
        → OUT_OF_STOCK
```

No distributed lock, application mutex, or `SELECT ... FOR UPDATE` is required for this stock reservation approach.

---

## 2. 🔁 Duplicate Message Protection

RabbitMQ provides **at-least-once delivery**, which means a message can potentially be delivered again.

Without idempotency:

```text
Message
  │
  ▼
Process Order
  │
  ├── Deduct stock
  ├── Charge customer
  │
  ▼
Consumer crashes
  │
  ▼
RabbitMQ redelivers message
  │
  ▼
Stock deducted AGAIN ❌
Payment processed AGAIN ❌
```

This project uses the `order_id` as the **primary key**.

```text
First delivery
     ↓
INSERT order_id
     ↓
SUCCESS

Duplicate delivery
     ↓
INSERT same order_id
     ↓
PRIMARY KEY violation
     ↓
Treat as already processed
     ↓
No duplicate processing
```

---

# ⚡ Asynchronous Order Processing

The customer does **not** wait for stock reservation and payment processing.

Instead:

```text
Customer
   │
   │ POST /api/v1/orders
   ▼
OrderIntakeService
   │
   │ publish message
   ▼
RabbitMQ
   │
   │
   └──────────────► returns HTTP 202
                         │
                         ▼
                    Customer gets
                     quick response

RabbitMQ
   │
   ▼
OrderProcessingService
   │
   ├── Reserve stock
   ├── Process payment
   └── Publish status
```

### Why HTTP `202 Accepted`?

The order has been **accepted for processing**, but the complete business operation is handled asynchronously.

This prevents slow processing from blocking the customer's HTTP request.

---

# 📈 Scaling & Traffic Spikes

Imagine a flash sale generates **10× normal traffic**.

Instead of overwhelming the processing service:

```text
                  FLASH SALE 🔥
                       │
                       ▼
              ┌─────────────────┐
              │ Order Intake    │
              └────────┬────────┘
                       │
                       ▼
                 ╔═══════════╗
                 ║ RabbitMQ  ║
                 ║   Queue   ║
                 ╚═════╤═════╝
                       │
              ┌────────┴────────┐
              │                 │
              ▼                 ▼
        Processing #1     Processing #2
```

The queue acts as a **buffer**.

You can independently scale:

```text
More traffic
    │
    ├──► Add Intake replicas
    │
    └──► Add Processing replicas
```

No code changes are required to scale the services horizontally.

---

# 🐇 Why RabbitMQ?

RabbitMQ is the **decoupling backbone** of this architecture.

### 🔗 1. Loose Coupling

The Intake service does not need to know the implementation details of the Processing service.

It only needs:

```text
Exchange + Routing Key + Message Contract
```

---

### 📦 2. Traffic Buffering

During traffic spikes:

```text
Incoming Orders
      │
      ▼
  RabbitMQ Queue
      │
      │ processed at worker capacity
      ▼
 Processing Service
```

The queue absorbs temporary bursts instead of overwhelming downstream services.

---

### 💾 3. Durable Messaging

The `order.requests` queue is durable.

Unacknowledged messages can be redelivered when a consumer fails.

Combined with idempotent processing:

```text
At-least-once delivery
        +
Idempotent consumer
        =
Reliable processing
```

---

### 📈 4. Independent Scalability

```text
Intake × N
    │
    ▼
RabbitMQ
    │
    ▼
Processing × N
```

The two sides can scale independently.

---

### 💀 5. Dead-Letter Handling

Failed or poison messages can be routed to a Dead Letter Exchange:

```text
Processing Failure
       │
       ▼
Dead Letter Exchange
       │
       ▼
order.deadletter
```

This separates **retryable failures** from messages that require investigation.

---

### 📡 6. Real-Time Customer Updates

Processing publishes status events through a **FANOUT exchange**.

```text
OrderProcessingService
        │
        │ StatusEvent
        ▼
order.status.exchange
        │
        ▼
OrderIntakeService
        │
        │ SSE
        ▼
Customer Browser
```

---

# 📡 Real-Time Order Status

The customer can receive status updates such as:

```text
INITIATED
    │
    ▼
STOCK_CONFIRMED
    │
    ▼
PROCESSING_PAYMENT
    │
    ▼
PAYMENT_CONFIRMED
```

Failure paths:

```text
INITIATED
    │
    ├──────────────► OUT_OF_STOCK
    │
    └──► PROCESSING_PAYMENT
              │
              └────────► PAYMENT_FAILED
```

This avoids forcing the customer to continuously poll the backend.

---

# 🧵 RabbitMQ Concurrency

The processing service uses:

```properties
spring.rabbitmq.listener.concurrency=10
```

and:

```text
prefetch = 10
```

Conceptually:

```text
             RabbitMQ
                │
      ┌─────────┼─────────┐
      ▼         ▼         ▼
   Worker 1  Worker 2  Worker 3 ... Worker 10
```

This allows multiple independent orders to be processed concurrently.

For the same hot product, the database row-level locking ensures that stock updates remain safe.

---

# 🔄 End-to-End Order Flow

Example: **5 customers place orders concurrently**

```text
1. 5 customers
       │
       ▼
2. OrderIntakeService
       │
       ├── Validate products
       │
       └── Return HTTP 202

3. Publish 5 OrderData messages
       │
       ▼
4. RabbitMQ
       │
       ▼
5. OrderProcessingService
       │
       ├── INSERT order → INITIATED
       │
       ├── Atomic stock reservation
       │
       ├── STOCK_CONFIRMED
       │
       ├── PROCESSING_PAYMENT
       │
       └── PAYMENT_CONFIRMED

6. StatusEvent
       │
       ▼
7. RabbitMQ FANOUT
       │
       ▼
8. OrderIntakeService
       │
       ▼
9. SSE
       │
       ▼
10. Customer sees live status
```

---

# 🗂️ Project Structure

```text
D:\Ecommerce
│
├── OrderIntakeService/
│   └── REST API + RabbitMQ Producer + SSE
│
├── ProductManager/
│   └── Product Catalog + Inventory REST API
│
├── OrderProcessingService/
│   └── RabbitMQ Consumer / Worker
│
├── OrderProcessingService_HLD_LLD.md
│   └── Detailed HLD / LLD
│
└── RabbitMQ_Explained.txt
    └── RabbitMQ architecture notes
```

---

# 🧰 Technology Stack

### Backend

- ☕ **Java 17**
- 🌱 **Spring Boot 3.5.5**
- 📦 **Maven**
- 🗃️ **Spring Data JPA**
- 🛠️ **Hibernate**
- 🌐 **Spring MVC / REST**
- 📡 **Server-Sent Events**

### Messaging

- 🐇 **RabbitMQ**
- 🔄 **Spring AMQP**
- 📨 **Jackson JSON**

### Database

- 🐘 **PostgreSQL**
- 🏊 **HikariCP**

### API Documentation

- 📚 **OpenAPI / Swagger**

### Developer Productivity

- ✨ **Lombok**

---

# 🚦 Getting Started

## Prerequisites

Make sure you have:

- Java 17+
- Maven
- PostgreSQL
- RabbitMQ
- RabbitMQ Management Plugin

RabbitMQ Management UI:

```text
http://localhost:15672
```

Create/use the RabbitMQ virtual host:

```text
order
```

---

## 1️⃣ Start ProductManager

```bash
cd ProductManager
mvnw spring-boot:run
```

Runs on:

```text
http://localhost:8081
```

---

## 2️⃣ Start OrderIntakeService

```bash
cd OrderIntakeService
mvnw spring-boot:run
```

Runs on:

```text
http://localhost:8080
```

---

## 3️⃣ Start OrderProcessingService

```bash
cd OrderProcessingService
mvnw spring-boot:run
```

Runs on:

```text
http://localhost:8082
```

> `OrderProcessingService` is a worker service and does not expose an HTTP API.

---

# 🧪 Try It

Create an order:

```bash
curl -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"userId":"usr_1","productId":"PROD-A","quantity":2}'
```

Expected behavior:

```text
HTTP 202 Accepted
        │
        ▼
Order published to RabbitMQ
        │
        ▼
OrderProcessingService
        │
        ├── Stock validation
        ├── Stock reservation
        ├── Payment processing
        └── Status events
```

---

# 🐇 RabbitMQ Topology

```text
                         RabbitMQ
                            │
        ┌───────────────────┼───────────────────┐
        │                   │                   │
        ▼                   ▼                   ▼
 order.requests       order.status       order.deadletter
    exchange             exchange             exchange
     DIRECT               FANOUT                DIRECT
        │                   │                   │
        ▼                   ▼                   ▼
order.requests        order.status        order.deadletter
     queue                 queue                 queue
```

### Main routing patterns

| Exchange | Type | Purpose |
|---|---|---|
| `order.requests.exchange` | DIRECT | Send orders to the processing queue |
| `order.status.exchange` | FANOUT | Broadcast order status events |
| `order.deadletter.exchange` | DIRECT | Handle failed/poison messages |

---

# 📊 Design Decisions

| Problem | Design Solution |
|---|---|
| 🔥 Overselling | Atomic conditional `UPDATE` + PostgreSQL row locking |
| 🔁 Duplicate delivery | `order_id` primary-key idempotency |
| 🐌 Slow processing | RabbitMQ asynchronous hand-off |
| 📈 Traffic spikes | Durable queue buffering |
| 📡 Real-time updates | RabbitMQ FANOUT + SSE |
| 💀 Failed messages | Dead Letter Exchange / Queue |
| 🔗 Tight coupling | Message-based service communication |
| 📈 Independent scaling | Multiple intake/processing instances |
| 🧠 Consumer overload | RabbitMQ prefetch limit |

---

# 🔍 Code Locations

The major mechanisms are implemented in:

```text
OrderProcessingService/
└── InventoryRepository.java
    └── Atomic stock UPDATE

OrderProcessingService/
└── OrderProcessingService.java
    ├── @RabbitListener
    ├── @Transactional
    ├── Idempotency handling
    ├── Stock processing
    └── Status publishing

OrderProcessingService/
└── RabbitMQConfig.java
    ├── Exchanges
    ├── Queues
    ├── Bindings
    ├── Concurrency
    └── Prefetch

OrderIntakeService/
└── OrderService.java
    └── RabbitMQ producer

OrderIntakeService/
└── OrderController.java
    └── HTTP 202 response
```

---

# 💡 What Makes This Project Interesting?

This is not just a CRUD microservices project.

It demonstrates several **real distributed-system concepts working together**:

```text
                 ┌─────────────────────┐
                 │  Customer Request   │
                 └──────────┬──────────┘
                            │
                            ▼
                     Async Messaging
                            │
                            ▼
                    Concurrent Workers
                            │
                            ▼
                  Atomic DB Operations
                            │
                            ▼
                     Idempotency
                            │
                            ▼
                   Event Broadcasting
                            │
                            ▼
                      SSE Streaming
```

The architecture combines:

**Concurrency + Transactions + Messaging + Idempotency + Scalability + Real-Time Events**

into one end-to-end system.

---

# 📌 Key Takeaways

### 🛡️ Correctness

Atomic database updates prevent stock races and overselling.

### ⚡ Performance

Customers receive `202 Accepted` without waiting for long-running processing.

### 🔄 Reliability

At-least-once delivery is handled using idempotent order processing.

### 📈 Scalability

RabbitMQ allows intake and processing services to scale independently.

### 📡 User Experience

SSE provides real-time order progress without polling.

### 💀 Resilience

Dead-letter queues isolate problematic messages for investigation.

---

## ⭐ Project Highlights

```text
✅ Microservices Architecture
✅ Asynchronous RabbitMQ Communication
✅ Atomic Inventory Reservation
✅ PostgreSQL Row-Level Concurrency Control
✅ Idempotent Order Processing
✅ RabbitMQ Prefetch & Consumer Concurrency
✅ Dead-Letter Queue
✅ FANOUT Status Events
✅ Server-Sent Events
✅ Independent Service Scaling
✅ REST APIs
✅ OpenAPI / Swagger
```

---

## 👨‍💻 Author

Built as a practical demonstration of **Java + Spring Boot + Microservices + RabbitMQ + PostgreSQL** and real-world distributed-system design patterns.

---

<p align="center">

### ⭐ If you find this project useful, consider giving it a star!

**Built with ☕ Java • 🌱 Spring Boot • 🐇 RabbitMQ • 🐘 PostgreSQL**

</p>
