package com.rutaexpress.bff.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bff/audit")
@Tag(name = "BFF - Auditoria", description = "Consulta de eventos y trazabilidad para el frontend")
public class BffAuditController {

    private final RestClient auditClient;

    public BffAuditController(
            RestClient.Builder builder,
            @Value("${services.audit.url:http://localhost:8083}") String auditUrl) {
        this.auditClient = builder.baseUrl(auditUrl).build();
    }

    @GetMapping("/shipments/{shipmentId}")
    @Operation(summary = "Obtener timeline de un envio (BFF)")
    public ResponseEntity<List<Map<String, Object>>> getShipmentTimeline(@PathVariable("shipmentId") Long shipmentId) {
        return auditClient.get()
                .uri("/api/audit/shipments/{id}", shipmentId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    }

    @GetMapping
    @Operation(summary = "Consultar eventos de auditoria con filtros (BFF)")
    public ResponseEntity<List<Map<String, Object>>> searchEvents(
            @RequestParam(name = "user", required = false) String user,
            @RequestParam(name = "eventType", required = false) String eventType,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to) {
        return auditClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/audit")
                        .queryParamIfPresent("user", java.util.Optional.ofNullable(user))
                        .queryParamIfPresent("eventType", java.util.Optional.ofNullable(eventType))
                        .queryParamIfPresent("from", java.util.Optional.ofNullable(from))
                        .queryParamIfPresent("to", java.util.Optional.ofNullable(to))
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    }
}