package com.rutaexpress.shipments.controller;

import com.rutaexpress.shipments.dto.CreateShipmentRequest;
import com.rutaexpress.shipments.dto.ShipmentResponse;
import com.rutaexpress.shipments.dto.UpdateStatusRequest;
import com.rutaexpress.shipments.entity.ShipmentStatus;
import com.rutaexpress.shipments.service.ShipmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/shipments")
@RequiredArgsConstructor
@Tag(name = "Gestión de Envíos", description = "Endpoints para la creación, seguimiento y cambio de estado del ciclo logístico de envíos")
public class ShipmentController {

    private final ShipmentService shipmentService;

    @PostMapping
    @Operation(summary = "Crear nuevo envío", description = "Registra un envío en estado inicial CREADO, calcula tarifa y notifica auditoría")
    public ResponseEntity<ShipmentResponse> createShipment(@Valid @RequestBody CreateShipmentRequest request) {
        ShipmentResponse response = shipmentService.createShipment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener detalle de envío por ID")
    public ResponseEntity<ShipmentResponse> getShipmentById(@PathVariable Long id) {
        return ResponseEntity.ok(shipmentService.getShipmentById(id));
    }

    @GetMapping("/tracking/{trackingNumber}")
    @Operation(summary = "Consultar envío por número de seguimiento")
    public ResponseEntity<ShipmentResponse> getShipmentByTracking(@PathVariable String trackingNumber) {
        return ResponseEntity.ok(shipmentService.getShipmentByTracking(trackingNumber));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Actualizar estado del envío", description = "Permite transiciones controladas. Regla: no se puede pasar a EN_RUTA sin antes pasar por ACEPTADO.")
    public ResponseEntity<ShipmentResponse> updateStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(shipmentService.updateStatus(id, request));
    }

    @GetMapping
    @Operation(summary = "Filtrar y listar envíos", description = "Búsqueda por estado y rango de fechas de creación")
    public ResponseEntity<List<ShipmentResponse>> getShipments(
            @RequestParam(required = false) ShipmentStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(shipmentService.getShipments(status, from, to));
    }
}