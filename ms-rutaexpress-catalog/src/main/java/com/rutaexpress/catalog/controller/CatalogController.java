package com.rutaexpress.catalog.controller;

import com.rutaexpress.catalog.dto.CapacityChangeResponse;
import com.rutaexpress.catalog.dto.ServiceOfferingRequest;
import com.rutaexpress.catalog.dto.ServiceOfferingResponse;
import com.rutaexpress.catalog.dto.UpdateTariffCapacityRequest;
import com.rutaexpress.catalog.service.CatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/catalog")
@RequiredArgsConstructor
@Tag(name = "Catalogo y Capacidad", description = "Endpoints para la gestion de servicios de envio, tarifas y capacidad de flota")
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping("/services")
    @Operation(summary = "Listar servicios de envio", description = "Obtiene todos los servicios de envio disponibles en el catalogo")
    public ResponseEntity<List<ServiceOfferingResponse>> listServices(
            @RequestParam(name = "activeOnly", required = false) Boolean activeOnly) {
        return ResponseEntity.ok(catalogService.getAllServices(activeOnly));
    }

    @GetMapping("/services/{id}")
    @Operation(summary = "Obtener servicio por ID")
    public ResponseEntity<ServiceOfferingResponse> getServiceById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(catalogService.getServiceById(id));
    }

    @GetMapping("/services/code/{code}")
    @Operation(summary = "Obtener servicio por codigo unico")
    public ResponseEntity<ServiceOfferingResponse> getServiceByCode(@PathVariable("code") String code) {
        return ResponseEntity.ok(catalogService.getServiceByCode(code));
    }

    @PostMapping("/services")
    @Operation(summary = "Crear nuevo servicio de catalogo", description = "Registra un nuevo tipo de servicio con su tarifa y capacidad inicial")
    public ResponseEntity<ServiceOfferingResponse> createService(
            @Valid @RequestBody ServiceOfferingRequest request) {
        ServiceOfferingResponse created = catalogService.createService(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/services/{id}")
    @Operation(summary = "Actualizar tarifas y capacidad del servicio")
    public ResponseEntity<ServiceOfferingResponse> updateService(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateTariffCapacityRequest request) {
        return ResponseEntity.ok(catalogService.updateService(id, request));
    }

    @PostMapping("/services/{id}/reserve-capacity")
    @Operation(summary = "Disminuir / reservar capacidad de flota", description = "Invocado cuando un envio es aceptado para descontar capacidad")
    public ResponseEntity<CapacityChangeResponse> reserveCapacity(
            @PathVariable("id") Long id,
            @RequestParam(name = "amount", defaultValue = "1") int amount) {
        return ResponseEntity.ok(catalogService.reserveCapacity(id, amount));
    }

    @PostMapping("/services/{id}/release-capacity")
    @Operation(summary = "Restituir capacidad de flota", description = "Invocado si un envio aceptado se cancela")
    public ResponseEntity<CapacityChangeResponse> releaseCapacity(
            @PathVariable("id") Long id,
            @RequestParam(name = "amount", defaultValue = "1") int amount) {
        return ResponseEntity.ok(catalogService.releaseCapacity(id, amount));
    }
}