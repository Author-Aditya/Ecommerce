package com.order.processing.service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.order.processing.service.dto.OrderData;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;

import java.nio.charset.StandardCharsets;

/**
 * Tolerant message converter used by the order.requests listener and the
 * RabbitTemplate.
 *
 * <p>The Order Intake Service always publishes well-formed JSON OrderData with
 * {@code contentType=application/json}; that path is handled exactly like the
 * standard {@link Jackson2JsonMessageConverter}.
 *
 * <p>This converter additionally copes with the two most common mistakes when a
 * message is published manually (for example via the RabbitMQ Management UI,
 * which defaults to {@code contentType=text/plain}):
 *
 * <ol>
 *   <li>Body is valid JSON OrderData but {@code contentType} was left as
 *       {@code text/plain} - the JSON is still parsed into {@link OrderData}.</li>
 *   <li>Body is NOT valid OrderData JSON (e.g. a bare order-id string such as
 *       {@code ord_abc123def456}) - a clear {@link MessageConversionException}
 *       is thrown so the message is rejected and routed to the dead-letter
 *       queue, instead of failing with the confusing boilerplate of the plain
 *       Jackson converter.</li>
 * </ol>
 */
public class OrderMessageConverter extends Jackson2JsonMessageConverter {

    private final ObjectMapper objectMapper;

    public OrderMessageConverter(ObjectMapper objectMapper) {
        // "*" trusts all packages so the __TypeId__ header set by the
        // Intake service is honored when it is present.
        super(objectMapper, "*");
        this.objectMapper = objectMapper;
    }

    @Override
    public Object fromMessage(Message message) throws MessageConversionException {
        MessageProperties properties = message.getMessageProperties();
        String contentType = properties.getContentType();

        // Standard Jackson path: application/json, octet-stream or null.
        if (contentType == null
                || contentType.equalsIgnoreCase(MessageProperties.DEFAULT_CONTENT_TYPE)
                || contentType.toLowerCase().contains("json")) {
            return super.fromMessage(message);
        }

        // Fallback: the message was published without a JSON content-type but
        // the body may still be valid OrderData JSON. Try to parse it.
        byte[] bodyBytes = message.getBody();
        String body = bodyBytes == null ? "" : new String(bodyBytes, StandardCharsets.UTF_8).trim();

        if (body.isEmpty()) {
            throw new MessageConversionException(
                    "Empty order message body. Expected a JSON OrderData payload with "
                            + "{orderId, userId, productId, quantity}.");
        }

        try {
            return objectMapper.readValue(body, OrderData.class);
        } catch (Exception e) {
            throw new MessageConversionException(
                    "Invalid order message. Expected JSON OrderData {orderId, userId, productId, quantity} "
                            + "but received contentType=" + contentType + " and body='" + body + "'. "
                            + "Publish a valid JSON payload or use the Order Intake Service API.", e);
        }
    }
}