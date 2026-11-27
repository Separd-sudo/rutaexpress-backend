package com.rutaexpress.bff.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * Puerta de enlace hacia ms-rutaexpress-kafka (administracion del cluster, lag, DLT y laboratorio).
 * Reenvia cualquier subruta: /api/bff/kafka/** -> /api/kafka/**
 */
@RestController
@RequestMapping({"/api/bff/kafka", "/api/kafka"})
@Tag(name = "BFF - Kafka Admin & Streaming", description = "Puerta de enlace hacia el microservicio administrador de Kafka")
public class BffKafkaController {

    private final RestClient kafkaClient;

    public BffKafkaController(
            RestClient.Builder builder,
            @Value("${services.kafka.url:http://localhost:8086}") String kafkaUrl) {
        this.kafkaClient = builder.baseUrl(kafkaUrl).build();
    }

    @GetMapping("/**")
    @Operation(summary = "Consultas al admin de Kafka: cluster, topics, consumer-groups/{id}/lag, dlt/{topic}/messages (BFF)")
    public ResponseEntity<String> get(HttpServletRequest request) {
        return forward(HttpMethod.GET, request, null);
    }

    @PostMapping("/**")
    @Operation(summary = "Acciones del admin de Kafka: crear topic, reprocesar DLT, laboratorio (BFF)")
    public ResponseEntity<String> post(HttpServletRequest request, @RequestBody(required = false) String body) {
        return forward(HttpMethod.POST, request, body);
    }

    @PutMapping("/**")
    @Operation(summary = "Modificar configuracion de un topic: topics/{topic}/configs (BFF)")
    public ResponseEntity<String> put(HttpServletRequest request, @RequestBody(required = false) String body) {
        return forward(HttpMethod.PUT, request, body);
    }

    @DeleteMapping("/**")
    @Operation(summary = "Eliminar topics o consumer groups: topics/{topic}, consumer-groups/{groupId} (BFF)")
    public ResponseEntity<String> delete(HttpServletRequest request) {
        return forward(HttpMethod.DELETE, request, null);
    }

    private ResponseEntity<String> forward(HttpMethod method, HttpServletRequest request, String body) {
        String subPath = request.getRequestURI()
                .replaceFirst("^/api/bff/kafka", "")
                .replaceFirst("^/api/kafka", "");
        String query = request.getQueryString();
        String target = "/api/kafka" + subPath + (query != null ? "?" + query : "");
        try {
            RestClient.RequestBodySpec spec = kafkaClient.method(method)
                    .uri(java.net.URI.create(kafkaBase() + target))
                    .accept(MediaType.APPLICATION_JSON);
            if (body != null) {
                spec.contentType(MediaType.APPLICATION_JSON).body(body);
            }
            String response = spec.retrieve().body(String.class);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response);
        } catch (RestClientResponseException ex) {
            return ResponseEntity.status(ex.getStatusCode()).contentType(MediaType.APPLICATION_JSON)
                    .body(ex.getResponseBodyAsString());
        }
    }

    @Value("${services.kafka.url:http://localhost:8086}")
    private String kafkaUrl;

    private String kafkaBase() {
        return kafkaUrl.endsWith("/") ? kafkaUrl.substring(0, kafkaUrl.length() - 1) : kafkaUrl;
    }
}
