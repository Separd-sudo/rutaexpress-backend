package com.rutaexpress.rabbitmq.service;

import com.rutaexpress.rabbitmq.dto.CreateBindingRequest;
import com.rutaexpress.rabbitmq.dto.CreateExchangeRequest;
import com.rutaexpress.rabbitmq.dto.CreateQueueRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.CustomExchange;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Servicio dedicado a la administración programática de colas, exchanges y bindings
 * en RabbitMQ mediante RabbitAdmin (Indicador 7 Pauta EP3).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RabbitAdminService {

    private final RabbitAdmin rabbitAdmin;

    public Map<String, Object> createQueue(CreateQueueRequest request) {
        validateNotBlank(request.getQueueName(), "El nombre de la cola no puede estar vacío");

        QueueBuilder builder = request.isDurable()
                ? QueueBuilder.durable(request.getQueueName())
                : QueueBuilder.nonDurable(request.getQueueName());

        if (request.getMessageTtlMs() != null && request.getMessageTtlMs() > 0) {
            builder.withArgument("x-message-ttl", request.getMessageTtlMs());
        }
        if (request.getDeadLetterExchange() != null && !request.getDeadLetterExchange().isBlank()) {
            builder.withArgument("x-dead-letter-exchange", request.getDeadLetterExchange());
        }
        if (request.getDeadLetterRoutingKey() != null && !request.getDeadLetterRoutingKey().isBlank()) {
            builder.withArgument("x-dead-letter-routing-key", request.getDeadLetterRoutingKey());
        }

        Queue queue = builder.build();
        rabbitAdmin.declareQueue(queue);
        log.info("[RabbitAdminService] Cola creada exitosamente: {}", request.getQueueName());

        return Map.of(
                "status", "CREATED",
                "queueName", request.getQueueName(),
                "durable", request.isDurable()
        );
    }

    public QueueInformation getQueueInfo(String queueName) {
        validateNotBlank(queueName, "El nombre de la cola no puede estar vacío");
        return rabbitAdmin.getQueueInfo(queueName);
    }

    public Map<String, Object> deleteQueue(String queueName) {
        validateNotBlank(queueName, "El nombre de la cola no puede estar vacío");
        boolean deleted = rabbitAdmin.deleteQueue(queueName);
        log.info("[RabbitAdminService] Cola eliminada: {} (resultado={})", queueName, deleted);
        return Map.of(
                "status", "DELETED",
                "queueName", queueName,
                "deleted", deleted
        );
    }

    public Map<String, Object> purgeQueue(String queueName) {
        validateNotBlank(queueName, "El nombre de la cola no puede estar vacío");
        int purgedCount = rabbitAdmin.purgeQueue(queueName);
        log.info("[RabbitAdminService] Cola purgada: {}, mensajes eliminados: {}", queueName, purgedCount);
        return Map.of(
                "status", "PURGED",
                "queueName", queueName,
                "messagesPurged", purgedCount
        );
    }

    public Map<String, Object> createExchange(CreateExchangeRequest request) {
        validateNotBlank(request.getExchangeName(), "El nombre del exchange no puede estar vacío");
        String type = request.getType() != null ? request.getType().toLowerCase() : "direct";

        Exchange exchange = switch (type) {
            case "direct" -> new DirectExchange(request.getExchangeName(), request.isDurable(), false);
            case "topic" -> new TopicExchange(request.getExchangeName(), request.isDurable(), false);
            case "fanout" -> new FanoutExchange(request.getExchangeName(), request.isDurable(), false);
            default -> new CustomExchange(request.getExchangeName(), type, request.isDurable(), false);
        };

        rabbitAdmin.declareExchange(exchange);
        log.info("[RabbitAdminService] Exchange creado: {} (tipo={})", request.getExchangeName(), type);

        return Map.of(
                "status", "CREATED",
                "exchangeName", request.getExchangeName(),
                "type", type,
                "durable", request.isDurable()
        );
    }

    public Map<String, Object> deleteExchange(String exchangeName) {
        validateNotBlank(exchangeName, "El nombre del exchange no puede estar vacío");
        boolean deleted = rabbitAdmin.deleteExchange(exchangeName);
        log.info("[RabbitAdminService] Exchange eliminado: {} (resultado={})", exchangeName, deleted);
        return Map.of(
                "status", "DELETED",
                "exchangeName", exchangeName,
                "deleted", deleted
        );
    }

    public Map<String, Object> createBinding(CreateBindingRequest request) {
        validateNotBlank(request.getQueueName(), "El nombre de la cola no puede estar vacío");
        validateNotBlank(request.getExchangeName(), "El nombre del exchange no puede estar vacío");
        validateNotBlank(request.getRoutingKey(), "La routingKey no puede estar vacía");

        Binding binding = new Binding(
                request.getQueueName(),
                Binding.DestinationType.QUEUE,
                request.getExchangeName(),
                request.getRoutingKey(),
                null
        );
        rabbitAdmin.declareBinding(binding);
        log.info("[RabbitAdminService] Binding creado: {} -> {} ({})",
                request.getExchangeName(), request.getQueueName(), request.getRoutingKey());

        return Map.of(
                "status", "CREATED",
                "queueName", request.getQueueName(),
                "exchangeName", request.getExchangeName(),
                "routingKey", request.getRoutingKey()
        );
    }

    private void validateNotBlank(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
    }
}
