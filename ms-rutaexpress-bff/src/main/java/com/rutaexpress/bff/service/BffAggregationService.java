package com.rutaexpress.bff.service;

import com.rutaexpress.bff.dto.FullShipmentTraceResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class BffAggregationService {

    private final RestClient shipmentsClient;
    private final RestClient catalogClient;
    private final RestClient auditClient;

    public BffAggregationService(
            RestClient.Builder builder,
            @Value("${services.shipments.url:http://localhost:8081}") String shipmentsUrl,
            @Value("${services.catalog.url:http://localhost:8082}") String catalogUrl,
            @Value("${services.audit.url:http://localhost:8083}") String auditUrl) {
        this.shipmentsClient = builder.baseUrl(shipmentsUrl).build();
        this.catalogClient = builder.baseUrl(catalogUrl).build();
        this.auditClient = builder.baseUrl(auditUrl).build();
    }

    public FullShipmentTraceResponse getFullTrace(Long shipmentId) {
        // 1. Obtener datos del envío
        Map<String, Object> shipment = null;
        try {
            shipment = shipmentsClient.get()
                    .uri("/api/shipments/{id}", shipmentId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Error al obtener shipment {}: {}", shipmentId, e.getMessage());
            throw new RuntimeException("No fue posible consultar el envío con ID: " + shipmentId);
        }

        // 2. Obtener catálogo asociado
        Map<String, Object> serviceDetails = null;
        if (shipment != null && shipment.get("serviceId") != null) {
            try {
                Object svcId = shipment.get("serviceId");
                serviceDetails = catalogClient.get()
                        .uri("/api/catalog/services/{id}", svcId)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .body(new ParameterizedTypeReference<Map<String, Object>>() {});
            } catch (Exception e) {
                log.warn("No fue posible obtener información del catálogo para servicio: {}", e.getMessage());
            }
        }

        // 3. Obtener timeline cronológico desde auditoría
        List<Map<String, Object>> timeline = Collections.emptyList();
        try {
            timeline = auditClient.get()
                    .uri("/api/audit/shipments/{id}", shipmentId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {});
        } catch (Exception e) {
            log.warn("No fue posible obtener timeline de auditoría para shipment {}: {}", shipmentId, e.getMessage());
        }

        return FullShipmentTraceResponse.builder()
                .shipment(shipment)
                .catalogService(serviceDetails)
                .timeline(timeline)
                .build();
    }
}