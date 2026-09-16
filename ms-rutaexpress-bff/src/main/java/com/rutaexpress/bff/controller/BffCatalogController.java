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
@RequestMapping("/api/bff/catalog")
@Tag(name = "BFF - Catálogo", description = "Gestión de catálogo de servicios y capacidades para el frontend")
public class BffCatalogController {

    private final RestClient catalogClient;

    public BffCatalogController(
            RestClient.Builder builder,
            @Value("${services.catalog.url:http://localhost:8082}") String catalogUrl) {
        this.catalogClient = builder.baseUrl(catalogUrl).build();
    }

    @GetMapping("/services")
    @Operation(summary = "Listar catálogo de servicios (BFF)")
    public ResponseEntity<List<Map<String, Object>>> listServices(@RequestParam(required = false) Boolean activeOnly) {
        return catalogClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/api/catalog/services")
                        .queryParamIfPresent("activeOnly", java.util.Optional.ofNullable(activeOnly))
                        .build())
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
    }

    @GetMapping("/services/{id}")
    @Operation(summary = "Obtener servicio por ID (BFF)")
    public ResponseEntity<Map<String, Object>> getServiceById(@PathVariable Long id) {
        return catalogClient.get()
                .uri("/api/catalog/services/{id}", id)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    @PostMapping("/services")
    @Operation(summary = "Crear nuevo servicio de catálogo (Admin)")
    public ResponseEntity<Map<String, Object>> createService(@RequestBody Map<String, Object> body) {
        return catalogClient.post()
                .uri("/api/catalog/services")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
    }

    @PutMapping("/services/{id}")
    @Operation(summary = "Modificar tarifa o capacidad (Admin)")
    public ResponseEntity<Map<String, Object>> updateService(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        return catalogClient.put()
                .uri("/api/catalog/services/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toEntity(new ParameterizedTypeReference<Map<String, Object>>() {});
    }
}