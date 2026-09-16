package com.rutaexpress.audit.controller;

import com.rutaexpress.audit.dto.AuditEventResponse;
import com.rutaexpress.audit.dto.CreateAuditEventRequest;
import com.rutaexpress.audit.service.AuditService;
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
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@Tag(name = "Auditoría y Trazabilidad", description = "Endpoints para consulta de timeline y registro inmutable de eventos logísticos")
public class AuditController {

    private final AuditService auditService;

    @PostMapping("/events")
    @Operation(summary = "Registrar evento de auditoría", description = "Ingesta de eventos logísticos generados por los servicios")
    public ResponseEntity<AuditEventResponse> recordEvent(@Valid @RequestBody CreateAuditEventRequest request) {
        AuditEventResponse response = auditService.recordEvent(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/shipments/{shipmentId}")
    @Operation(summary = "Obtener timeline de un envío", description = "Consulta la trazabilidad cronológica de un envío (quién creó, aceptó, etc.)")
    public ResponseEntity<List<AuditEventResponse>> getShipmentTimeline(@PathVariable Long shipmentId) {
        return ResponseEntity.ok(auditService.getTimelineForShipment(shipmentId));
    }

    @GetMapping
    @Operation(summary = "Consultar eventos de auditoría con filtros", description = "Permite a auditores y administradores filtrar por usuario, tipo de evento y rango de fechas")
    public ResponseEntity<List<AuditEventResponse>> searchEvents(
            @RequestParam(required = false) String user,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(auditService.searchEvents(user, eventType, from, to));
    }
}