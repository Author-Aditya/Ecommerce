package com.order.processing.service.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.processing.service.dto.OrderData;
import com.order.processing.service.dto.StatusEvent;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Value("${spring.rabbitmq.queue.order.requests}")
    private String orderRequestsQueue;

    @Value("${spring.rabbitmq.exchange.order.requests}")
    private String orderRequestsExchange;

    @Value("${spring.rabbitmq.queue.order.status}")
    private String orderStatusQueue;

    @Value("${spring.rabbitmq.exchange.order.status}")
    private String orderStatusExchange;

    @Value("${spring.rabbitmq.queue.order.deadletter}")
    private String orderDeadLetterQueue;

    @Value("${spring.rabbitmq.exchange.order.deadletter}")
    private String orderDeadLetterExchange;

    @Value("${spring.rabbitmq.listener.concurrency}")
    private int concurrency;

    @Bean
    public DirectExchange orderRequestsExchange() {
        return new DirectExchange(orderRequestsExchange);
    }

    @Bean
    public Queue orderRequestsQueue() {
        return QueueBuilder.durable(orderRequestsQueue)
                .withArgument("x-dead-letter-exchange", orderDeadLetterExchange)
                .build();
    }

    @Bean
    public FanoutExchange orderStatusExchange() {
        return new FanoutExchange(orderStatusExchange);
    }

    @Bean
    public Queue orderStatusQueue() {
        return QueueBuilder.durable(orderStatusQueue).build();
    }

    @Bean
    public Binding orderStatusBinding() {
        return BindingBuilder.bind(orderStatusQueue()).to(orderStatusExchange());
    }

    @Bean
    public Queue orderDeadLetterQueue() {
        return QueueBuilder.durable(orderDeadLetterQueue).build();
    }

    @Bean
    public DirectExchange orderDeadLetterExchange() {
        return new DirectExchange(orderDeadLetterExchange);
    }

    @Bean
    public Binding orderDeadLetterBinding() {
        return BindingBuilder.bind(orderDeadLetterQueue()).to(orderDeadLetterExchange()).with("order.deadletter");
    }

    @Bean
    public ObjectMapper objectMapper() {
        // OrderProcessingService does not include spring-boot-starter-web, so the
        // auto-configured Jackson ObjectMapper bean is NOT present by default.
        // Provide one that mirrors Spring Boot's standard configuration:
        // register any JSR-310 (java.time) and other modules found on the
        // classpath, and tolerate unknown JSON properties.
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return objectMapper;
    }

    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        return new OrderMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter);
        factory.setConcurrentConsumers(concurrency);
        factory.setMaxConcurrentConsumers(concurrency);
        factory.setPrefetchCount(10);
        return factory;
    }
}
