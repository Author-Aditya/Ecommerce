package com.order.intake.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.intake.dto.order.OrderData;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
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

    @Value("${spring.rabbitmq.queue.order.deadletter}")
    private String orderDeadLetterQueue;

    @Value("${spring.rabbitmq.exchange.order.deadletter}")
    private String orderDeadLetterExchange;

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
    public Queue orderDeadLetterQueue() {
        return QueueBuilder.durable(orderDeadLetterQueue).build();
    }

    @Bean
    public DirectExchange orderDeadLetterExchange() {
        return new DirectExchange(orderDeadLetterExchange);
    }

    @Bean
    public Binding orderRequestsBinding() {
        return BindingBuilder.bind(orderRequestsQueue()).to(orderRequestsExchange()).with("order.requests");
    }

    @Bean
    public MessageConverter messageConverter(ObjectMapper objectMapper) {
        return new Jackson2JsonMessageConverter(objectMapper);
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter messageConverter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter);
        return rabbitTemplate;
    }
}
