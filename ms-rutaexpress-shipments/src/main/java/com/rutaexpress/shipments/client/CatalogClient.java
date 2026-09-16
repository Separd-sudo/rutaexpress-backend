package com.rutaexpress.shipments.client;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;

@Component
@Slf4j
public class CatalogClient {

    private final RestClient restClient;

    public CatalogClient(RestClient.Builder builder, @Value("${services.catalog.url:http://localhost:8082}") String catalogUrl) {
        this.restClient = builder.baseUrl(catalogUrl).build();
    }

    public ServiceInfo getService(Long serviceId) {
        try {
            return restClient.get()
                    .uri("/api/catalog/services/{id}", serviceId)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(ServiceInfo.class);
        } catch (Exception ex) {
            log.warn("No se pudo obtener servicio de catálogo id {}: {}", serviceId, ex.getMessage());
            return null;
        }
    }

    public boolean reserveCapacity(Long serviceId, int amount) {
        try {
            restClient.post()
                    .uri("/api/catalog/services/{id}/reserve-capacity?amount={amount}", serviceId, amount)
                    .contentType(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Capacidad reservada en catálogo para servicio ID: {}", serviceId);
            return true;
        } catch (Exception ex) {
            log.error("Error al reservar capacidad en catálogo para servicio ID {}: {}", serviceId, ex.getMessage());
            throw new IllegalStateException("No se pudo reservar capacidad de flota: " + ex.getMessage(), ex);
        }
    }

    public void releaseCapacity(Long serviceId, int amount) {
        try {
            restClient.post()
                    .uri("/api/catalog/services/{id}/release-capacity?amount={amount}", serviceId, amount)
                    .contentType(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Capacidad restituida en catálogo para servicio ID: {}", serviceId);
        } catch (Exception ex) {
            log.error("Error al restituir capacidad en catálogo para servicio ID {}: {}", serviceId, ex.getMessage());
        }
    }

    @Data
    public static class ServiceInfo {
        private Long id;
        private String code;
        private String name;
        private BigDecimal basePrice;
        private BigDecimal pricePerKm;
        private Integer availableCapacity;
        private Boolean active;
    }
}