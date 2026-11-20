package com.rutaexpress.report.controller;

import com.rutaexpress.report.service.ReportQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/report")
@RequiredArgsConstructor
@Tag(name = "Reporteria", description = "KPIs de la red calculados desde el streaming de Kafka (solo lectura)")
public class ReportController {

    private final ReportQueryService queryService;

    @GetMapping("/kpis")
    @Operation(summary = "KPIs: envios por hora, lead time y estados activos")
    public ResponseEntity<Map<String, Object>> kpis(@RequestParam(name = "range", defaultValue = "last24h") String range) {
        return ResponseEntity.ok(queryService.kpis(range));
    }

    @GetMapping("/top-services")
    @Operation(summary = "Servicios de envio mas usados")
    public ResponseEntity<List<Map<String, Object>>> topServices(
            @RequestParam(name = "range", defaultValue = "last7d") String range,
            @RequestParam(name = "limit", defaultValue = "5") int limit) {
        return ResponseEntity.ok(queryService.topServices(range, limit));
    }

    @GetMapping("/events/recent")
    @Operation(summary = "Ultimos eventos consumidos desde Kafka (con particion y offset)")
    public ResponseEntity<List<Map<String, Object>>> recent(@RequestParam(name = "limit", defaultValue = "20") int limit) {
        return ResponseEntity.ok(queryService.recentEvents(limit));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", ex.getMessage()));
    }
}
