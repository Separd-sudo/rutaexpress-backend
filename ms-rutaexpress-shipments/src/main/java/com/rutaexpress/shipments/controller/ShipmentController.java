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
@Tag(name = "Gestion de Envios", description = "Endpoints para la creacion, seguimiento y cambio de estado del ciclo logistico de envios")
public class ShipmentController {

    private final ShipmentService shipmentService;
    private final com.rutaexpress.shipments.client.AuditClient auditClient;

    @PostMapping
    @Operation(summary = "Crear nuevo envio", description = "Registra un envio en estado inicial CREADO, calcula tarifa y notifica auditoria")
    public ResponseEntity<ShipmentResponse> createShipment(@Valid @RequestBody CreateShipmentRequest request) {
        ShipmentResponse response = shipmentService.createShipment(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/demo/dlq")
    @Operation(summary = "Simular envío de mensaje con fallo hacia Dead Letter Queue (DLQ)")
    public ResponseEntity<java.util.Map<String, Object>> simulateDlqEvent(
            @RequestBody(required = false) java.util.Map<String, String> body) {
        String tracking = body != null ? body.get("trackingNumber") : "RTX-DLQ-DEMO";
        String details = body != null ? body.get("details") : "Prueba controlada de rechazo NACK y desvío a DLQ";
        var envelope = auditClient.sendDemoDlqEvent(tracking, details);
        return ResponseEntity.accepted().body(java.util.Map.of(
                "status", "SENT_TO_DLQ_FLOW",
                "targetDlq", "q.cmd.warehouse.dlq",
                "envelope", envelope
        ));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener detalle de envio por ID")
    public ResponseEntity<ShipmentResponse> getShipmentById(@PathVariable("id") Long id) {
        return ResponseEntity.ok(shipmentService.getShipmentById(id));
    }

    @GetMapping("/tracking/{trackingNumber}")
    @Operation(summary = "Consultar envio por numero de seguimiento")
    public ResponseEntity<ShipmentResponse> getShipmentByTracking(@PathVariable("trackingNumber") String trackingNumber) {
        return ResponseEntity.ok(shipmentService.getShipmentByTracking(trackingNumber));
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "Actualizar estado del envio", description = "Permite transiciones controladas. Regla: no se puede pasar a EN_RUTA sin antes pasar por ACEPTADO.")
    public ResponseEntity<ShipmentResponse> updateStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody UpdateStatusRequest request) {
        return ResponseEntity.ok(shipmentService.updateStatus(id, request));
    }

    @GetMapping
    @Operation(summary = "Filtrar y listar envios", description = "Busqueda por estado y rango de fechas de creacion")
    public ResponseEntity<List<ShipmentResponse>> getShipments(
            @RequestParam(name = "status", required = false) ShipmentStatus status,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(shipmentService.getShipments(status, from, to));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar o cancelar un envio", description = "Elimina envios en estado CREADO o CANCELADO")
    public ResponseEntity<Void> deleteShipment(@PathVariable("id") Long id) {
        shipmentService.deleteShipment(id);
        return ResponseEntity.noContent().build();
    }
}