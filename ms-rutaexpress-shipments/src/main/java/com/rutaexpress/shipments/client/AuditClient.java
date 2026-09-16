package com.rutaexpress.shipments.client;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class AuditClient {

    private final RestClient restClient;

    public AuditClient(RestClient.Builder builder, @Value("${services.audit.url:http://localhost:8083}") String auditUrl) {
        this.restClient = builder.baseUrl(auditUrl).build();
    }

    @Async
    public void sendAuditEvent(Long shipmentId, String trackingNumber, String eventType,
                               String previousStatus, String newStatus, String performedBy,
                               String userRole, String details) {
        try {
            AuditEventPayload payload = AuditEventPayload.builder()
                    .shipmentId(shipmentId)
                    .trackingNumber(trackingNumber)
                    .eventType(eventType)
                    .previousStatus(previousStatus)
                    .newStatus(newStatus)
                    .performedBy(performedBy != null ? performedBy : "SISTEMA")
                    .userRole(userRole != null ? userRole : "Sistema")
                    .details(details)
                    .build();

            restClient.post()
                    .uri("/api/audit/events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();

            log.debug("Evento de auditoría enviado con éxito para shipment: {}", shipmentId);
        } catch (Exception ex) {
            log.warn("No se pudo registrar evento de auditoría para shipment {}: {}", shipmentId, ex.getMessage());
        }
    }

    @Data
    @Builder
    public static class AuditEventPayload {
        private Long shipmentId;
        private String trackingNumber;
        private String eventType;
        private String previousStatus;
        private String newStatus;
        private String performedBy;
        private String userRole;
        private String details;
    }
}