package com.rutaexpress.rabbitmq.controller;

import com.rutaexpress.rabbitmq.dto.CreateBindingRequest;
import com.rutaexpress.rabbitmq.dto.CreateExchangeRequest;
import com.rutaexpress.rabbitmq.dto.CreateQueueRequest;
import com.rutaexpress.rabbitmq.dto.EventEnvelope;
import com.rutaexpress.rabbitmq.dto.ShipmentEventMessage;
import com.rutaexpress.rabbitmq.service.RabbitAdminService;
import com.rutaexpress.rabbitmq.service.RabbitMQEventConsumerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.QueueInformation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rabbitmq")
@RequiredArgsConstructor
@Tag(name = "RabbitMQ Admin & Event Broker API", description = "Administración de colas, exchanges, bindings y monitoreo de DLQ (EP3)")
public class RabbitMQController {

    private final RabbitAdminService rabbitAdminService;
    private final RabbitMQEventConsumerService consumerService;

    @Value("${rabbitmq.exchanges.direct:cmd.direct}")
    private String directExchangeName;

    @Value("${rabbitmq.exchanges.topic:cmd.topic}")
    private String topicExchangeName;

    @Value("${rabbitmq.exchanges.dlx:cmd.dead.dlx}")
    private String dlxExchangeName;

    // =========================================================================
    // 1. ENDPOINTS DE ADMINISTRACIÓN DE COLAS, EXCHANGES Y BINDINGS (EP3)
    // =========================================================================

    @PostMapping("/queues")
    @Operation(summary = "Crear una nueva cola en RabbitMQ (valida parámetros con @Valid)")
    public ResponseEntity<Map<String, Object>> createQueue(@Valid @RequestBody CreateQueueRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rabbitAdminService.createQueue(request));
    }

    @GetMapping("/queues/{name}")
    @Operation(summary = "Consultar información y estado de una cola en RabbitMQ")
    public ResponseEntity<Map<String, Object>> getQueueInfo(@PathVariable("name") String name) {
        QueueInformation info = rabbitAdminService.getQueueInfo(name);
        if (info == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of(
                "queueName", info.getName(),
                "messageCount", info.getMessageCount(),
                "consumerCount", info.getConsumerCount()
        ));
    }

    @DeleteMapping("/queues/{name}")
    @Operation(summary = "Eliminar una cola en RabbitMQ por nombre")
    public ResponseEntity<Map<String, Object>> deleteQueue(@PathVariable("name") String name) {
        return ResponseEntity.ok(rabbitAdminService.deleteQueue(name));
    }

    @PostMapping("/queues/{name}/purge")
    @Operation(summary = "Purgar todos los mensajes de una cola en RabbitMQ")
    public ResponseEntity<Map<String, Object>> purgeQueue(@PathVariable("name") String name) {
        return ResponseEntity.ok(rabbitAdminService.purgeQueue(name));
    }

    @PostMapping("/exchanges")
    @Operation(summary = "Crear un nuevo Exchange en RabbitMQ (direct, topic o fanout)")
    public ResponseEntity<Map<String, Object>> createExchange(@Valid @RequestBody CreateExchangeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rabbitAdminService.createExchange(request));
    }

    @DeleteMapping("/exchanges/{name}")
    @Operation(summary = "Eliminar un Exchange en RabbitMQ por nombre")
    public ResponseEntity<Map<String, Object>> deleteExchange(@PathVariable("name") String name) {
        return ResponseEntity.ok(rabbitAdminService.deleteExchange(name));
    }

    @PostMapping("/bindings")
    @Operation(summary = "Crear un Binding entre una Cola y un Exchange con Routing Key")
    public ResponseEntity<Map<String, Object>> createBinding(@Valid @RequestBody CreateBindingRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(rabbitAdminService.createBinding(request));
    }

    // =========================================================================
    // 2. ENDPOINTS DE MONITOREO DE EVENTOS Y DEMOSTRACIÓN DLQ
    // =========================================================================

    @GetMapping("/events")
    @Operation(summary = "Consultar últimos eventos procesados exitosamente desde las colas RabbitMQ")
    public ResponseEntity<List<ShipmentEventMessage>> getProcessedEvents() {
        return ResponseEntity.ok(consumerService.getRecentProcessedEvents());
    }

    @GetMapping("/dlq")
    @Operation(summary = "Consultar mensajes en cuarentena recibidos en las Dead Letter Queues (DLQ)")
    public ResponseEntity<List<Map<String, Object>>> getDlqEvents() {
        return ResponseEntity.ok(consumerService.getRecentDlqEvents());
    }

    @GetMapping("/metrics")
    @Operation(summary = "Consultar métricas de procesamiento, idempotencia y tasa de DLQ")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        return ResponseEntity.ok(consumerService.getMetrics());
    }

    @PostMapping("/publish")
    @Operation(summary = "Publicar un evento con Envelope común en RabbitMQ")
    public ResponseEntity<Map<String, Object>> publishEvent(
            @RequestBody ShipmentEventMessage event,
            @RequestParam(name = "simulateFailure", defaultValue = "false") boolean simulateFailure) {
        EventEnvelope envelope = consumerService.publishEvent(event, simulateFailure);
        return ResponseEntity.accepted().body(Map.of(
                "status", "PUBLISHED",
                "exchange", directExchangeName,
                "envelope", envelope
        ));
    }

    @GetMapping("/health")
    @Operation(summary = "Estado operativo del microservicio administrador y consumidor RabbitMQ")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "ms-rutaexpress-rabbitmq",
                "exchanges", List.of(directExchangeName, topicExchangeName, dlxExchangeName),
                "metrics", consumerService.getMetrics()
        ));
    }
}
