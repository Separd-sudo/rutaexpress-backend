package com.rutaexpress.bff.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@RestController
@RequestMapping({"/api/bff/report", "/api/report"})
@Tag(name = "BFF - Reporteria", description = "KPIs de la red alimentados por Kafka (solo Admin)")
public class BffReportController {

    private final RestClient reportClient;

    public BffReportController(
            RestClient.Builder builder,
            @Value("${services.report.url:http://localhost:8085}") String reportUrl) {
        this.reportClient = builder.baseUrl(reportUrl).build();
    }

    @GetMapping("/kpis")
    @Operation(summary = "KPIs: envios por hora, lead time y estados activos (BFF)")
    public ResponseEntity<String> kpis(@RequestParam(name = "range", defaultValue = "last24h") String range) {
        return get("/api/report/kpis?range={range}", range);
    }

    @GetMapping("/top-services")
    @Operation(summary = "Servicios mas usados (BFF)")
    public ResponseEntity<String> topServices(@RequestParam(name = "range", defaultValue = "last7d") String range,
                                              @RequestParam(name = "limit", defaultValue = "5") int limit) {
        return get("/api/report/top-services?range={range}&limit={limit}", range, limit);
    }

    @GetMapping("/events/recent")
    @Operation(summary = "Ultimos eventos consumidos desde Kafka (BFF)")
    public ResponseEntity<String> recent(@RequestParam(name = "limit", defaultValue = "20") int limit) {
        return get("/api/report/events/recent?limit={limit}", limit);
    }

    @GetMapping("/activity")
    @Operation(summary = "Actividad de usuarios consumida desde el topic audit.timeline (BFF)")
    public ResponseEntity<String> activity(@RequestParam(name = "range", defaultValue = "last24h") String range,
                                           @RequestParam(name = "limit", defaultValue = "10") int limit) {
        return get("/api/report/activity?range={range}&limit={limit}", range, limit);
    }

    private ResponseEntity<String> get(String uri, Object... vars) {
        try {
            String body = reportClient.get().uri(uri, vars).accept(MediaType.APPLICATION_JSON)
                    .retrieve().body(String.class);
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(body);
        } catch (RestClientResponseException ex) {
            return ResponseEntity.status(ex.getStatusCode()).contentType(MediaType.APPLICATION_JSON)
                    .body(ex.getResponseBodyAsString());
        }
    }
}
