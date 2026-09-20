package com.rutaexpress.bff.controller;

import com.rutaexpress.bff.dto.FullShipmentTraceResponse;
import com.rutaexpress.bff.service.BffAggregationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bff/shipments")
@Tag(name = "BFF - Envios", description = "Acceso unificado y agregacion para la gestion de envios desde React")
@Slf4j
public class BffShipmentController {

    private final RestClient shipmentsClient;
    private final BffAggregationService aggregationService;

    public BffShipmentController(
            RestClient.Builder builder,
            @Value("${services.shipments.url:http://localhost:8081}") String shipmentsUrl,
            BffAggregationService aggregationService) {
        this.shipmentsClient = builder.baseUrl(shipmentsUrl).build();
        this.aggregationService = aggregationService;
    }

    @PostMapping
    @Operation(summary = "Crear nuevo envio (BFF)", description = "Redirige la creacion del envio al microservicio de shipments")
    public ResponseEntity<Map<String, Object>> createShipment(@RequestBody Map<String, Object> body) {
        return shipmentsClient.post()
                .uri("/api/shipments")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener envio por ID (BFF)")
    public ResponseEntity<Map<String, Object>> getShipmentById(@PathVariable("id") Long id) {
        return shipmentsClient.get()
                .uri("/api/shipments/{id}", id)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    @GetMapping("/tracking/{trackingNumber}")
    @Operation(summary = "Consultar envio por tracking (BFF)")
    public ResponseEntity<Map<String, Object>> getShipmentByTracking(@PathVariable("trackingNumber") String trackingNumber) {
        return shipmentsClient.get()
                .uri("/api/shipments/tracking/{trackingNumber}", trackingNumber)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Actualizar estado de envio (BFF)")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable("id") Long id,
            @RequestBody Map<String, Object> body) {
        return shipmentsClient.put()
                .uri("/api/shipments/{id}/status", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    @GetMapping
    @Operation(summary = "Listar envios filtrados (BFF)")
    public ResponseEntity<List<Map<String, Object>>> getShipments(
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to) {
        return shipmentsClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/shipments")
                        .queryParamIfPresent("status", java.util.Optional.ofNullable(status))
                        .queryParamIfPresent("from", java.util.Optional.ofNullable(from))
                        .queryParamIfPresent("to", java.util.Optional.ofNullable(to))
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    }

    @GetMapping("/{id}/full-trace")
    @Operation(summary = "Agregacion completa del envio: datos + catalogo + timeline",
               description = "Endpoint optimizado para React que unifica los 3 microservicios en una sola respuesta")
    public ResponseEntity<FullShipmentTraceResponse> getFullTrace(@PathVariable("id") Long id) {
        return ResponseEntity.ok(aggregationService.getFullTrace(id));
    }
}