package com.rutaexpress.bff.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/bff/rabbitmq", "/api/rabbitmq"})
@Tag(name = "BFF - RabbitMQ Admin & Mensajeria", description = "Puerta de enlace hacia el microservicio administrador y consumidor RabbitMQ")
public class BffRabbitMQController {

    private final RestClient rabbitClient;

    public BffRabbitMQController(
            RestClient.Builder builder,
            @Value("${services.rabbitmq.url:http://localhost:8084}") String rabbitUrl) {
        this.rabbitClient = builder.baseUrl(rabbitUrl).build();
    }

    @PostMapping("/queues")
    @Operation(summary = "Crear cola en RabbitMQ (BFF)")
    public ResponseEntity<Map<String, Object>> createQueue(@RequestBody Map<String, Object> body) {
        return rabbitClient.post()
                .uri("/api/rabbitmq/queues")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    @GetMapping("/queues/{name}")
    @Operation(summary = "Consultar información de cola en RabbitMQ (BFF)")
    public ResponseEntity<Map<String, Object>> getQueueInfo(@PathVariable("name") String name) {
        return rabbitClient.get()
                .uri("/api/rabbitmq/queues/{name}", name)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    @DeleteMapping("/queues/{name}")
    @Operation(summary = "Eliminar cola en RabbitMQ (BFF)")
    public ResponseEntity<Map<String, Object>> deleteQueue(@PathVariable("name") String name) {
        return rabbitClient.delete()
                .uri("/api/rabbitmq/queues/{name}", name)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    @PostMapping("/queues/{name}/purge")
    @Operation(summary = "Purgar mensajes de una cola en RabbitMQ (BFF)")
    public ResponseEntity<Map<String, Object>> purgeQueue(@PathVariable("name") String name) {
        return rabbitClient.post()
                .uri("/api/rabbitmq/queues/{name}/purge", name)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    @PostMapping("/exchanges")
    @Operation(summary = "Crear exchange en RabbitMQ (BFF)")
    public ResponseEntity<Map<String, Object>> createExchange(@RequestBody Map<String, Object> body) {
        return rabbitClient.post()
                .uri("/api/rabbitmq/exchanges")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    @DeleteMapping("/exchanges/{name}")
    @Operation(summary = "Eliminar exchange en RabbitMQ (BFF)")
    public ResponseEntity<Map<String, Object>> deleteExchange(@PathVariable("name") String name) {
        return rabbitClient.delete()
                .uri("/api/rabbitmq/exchanges/{name}", name)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    @PostMapping("/bindings")
    @Operation(summary = "Crear binding en RabbitMQ (BFF)")
    public ResponseEntity<Map<String, Object>> createBinding(@RequestBody Map<String, Object> body) {
        return rabbitClient.post()
                .uri("/api/rabbitmq/bindings")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    @GetMapping("/events")
    @Operation(summary = "Obtener eventos procesados por RabbitMQ (BFF)")
    public ResponseEntity<List<Map<String, Object>>> getRabbitEvents() {
        return rabbitClient.get()
                .uri("/api/rabbitmq/events")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    }

    @GetMapping("/dlq")
    @Operation(summary = "Obtener mensajes en cuarentena en DLQ (BFF)")
    public ResponseEntity<List<Map<String, Object>>> getDlqEvents() {
        return rabbitClient.get()
                .uri("/api/rabbitmq/dlq")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    }

    @GetMapping("/metrics")
    @Operation(summary = "Obtener métricas y tasa de DLQ (BFF)")
    public ResponseEntity<Map<String, Object>> getMetrics() {
        return rabbitClient.get()
                .uri("/api/rabbitmq/metrics")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    @PostMapping("/publish")
    @Operation(summary = "Publicar un evento asíncrono en RabbitMQ desde el BFF")
    public ResponseEntity<Map<String, Object>> publishEvent(
            @RequestBody Map<String, Object> payload,
            @RequestParam(name = "simulateFailure", defaultValue = "false") boolean simulateFailure) {
        return rabbitClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/rabbitmq/publish")
                        .queryParam("simulateFailure", simulateFailure)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    @GetMapping("/health")
    @Operation(summary = "Verificar estado del microservicio RabbitMQ (BFF)")
    public ResponseEntity<Map<String, Object>> getRabbitHealth() {
        return rabbitClient.get()
                .uri("/api/rabbitmq/health")
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
    }
}
