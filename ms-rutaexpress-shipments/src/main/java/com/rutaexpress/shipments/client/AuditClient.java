package com.rutaexpress.shipments.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.UUID;

@Component
@Slf4j
public class AuditClient {

    private final RestClient restClient;
    private final RabbitTemplate rabbitTemplate;
    private final String directExchangeName;
    private final String warehouseDirectRk;
    private final String labelDirectRk;
    private final boolean rabbitEnabled;

    public AuditClient(
            RestClient.Builder builder,
            RabbitTemplate rabbitTemplate,
            @Value("${services.audit.url:http://localhost:8083}") String auditUrl,
            @Value("${rabbitmq.exchanges.direct:cmd.direct}") String directExchangeName,
            @Value("${rabbitmq.routing-keys.warehouse-direct:warehouse.ticket}") String warehouseDirectRk,
            @Value("${rabbitmq.routing-keys.label-direct:label.gen}") String labelDirectRk,
            @Value("${rabbitmq.enabled:true}") boolean rabbitEnabled) {
        this.restClient = builder.baseUrl(auditUrl).build();
        this.rabbitTemplate = rabbitTemplate;
        this.directExchangeName = directExchangeName;
        this.warehouseDirectRk = warehouseDirectRk;
        this.labelDirectRk = labelDirectRk;
        this.rabbitEnabled = rabbitEnabled;
    }

    @Async
    public void sendAuditEvent(Long shipmentId, String trackingNumber, String eventType,
                               String previousStatus, String newStatus, String performedBy,
                               String userRole, String details) {
        publishShipmentEvent(shipmentId, trackingNumber, eventType, previousStatus, newStatus,
                performedBy, userRole, details, false);
    }

    public EventEnvelope sendDemoDlqEvent(String trackingNumber, String details) {
        return publishShipmentEvent(
                9999L,
                trackingNumber != null && !trackingNumber.isBlank() ? trackingNumber : "RTX-DLQ-DEMO",
                "DLQ_SIMULATION",
                "CREADO",
                "ACEPTADO",
                "OPERADOR_DEMO",
                "Admin",
                details != null && !details.isBlank() ? details : "Simulación de fallo hacia Dead Letter Queue (DLQ)",
                true
        );
    }

    private EventEnvelope publishShipmentEvent(Long shipmentId, String trackingNumber, String eventType,
                                               String previousStatus, String newStatus, String performedBy,
                                               String userRole, String details, boolean simulateFailure) {
        String now = OffsetDateTime.now().toString();
        AuditEventPayload payload = AuditEventPayload.builder()
                .shipmentId(shipmentId)
                .trackingNumber(trackingNumber)
                .eventType(eventType)
                .previousStatus(previousStatus)
                .newStatus(newStatus)
                .performedBy(performedBy != null ? performedBy : "SISTEMA")
                .userRole(userRole != null ? userRole : "Sistema")
                .details(details)
                .timestamp(now)
                .build();

        EventEnvelope envelope = EventEnvelope.builder()
                .type(eventType != null ? eventType : "SHIPMENT_EVENT")
                .eventId(UUID.randomUUID().toString())
                .timestamp(now)
                .traceId(UUID.randomUUID().toString())
                .correlationId(trackingNumber != null ? trackingNumber : UUID.randomUUID().toString())
                .simulateFailure(simulateFailure)
                .payload(payload)
                .build();

        if (rabbitEnabled) {
            try {
                String routingKey = "CREADO".equalsIgnoreCase(newStatus) ? labelDirectRk : warehouseDirectRk;
                rabbitTemplate.convertAndSend(directExchangeName, routingKey, envelope);
                log.info("Evento publicado en RabbitMQ (exchange={}, routingKey={}, eventId={}, simulateFailure={}) para shipment: {}",
                        directExchangeName, routingKey, envelope.getEventId(), simulateFailure, shipmentId);
                return envelope;
            } catch (Exception rabbitEx) {
                log.warn("RabbitMQ no disponible ({}), aplicando fallback HTTP directo hacia ms-rutaexpress-audit para shipment {}",
                        rabbitEx.getMessage(), shipmentId);
            }
        }

        if (!simulateFailure) {
            try {
                restClient.post()
                        .uri("/api/audit/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(payload)
                        .retrieve()
                        .toBodilessEntity();
                log.debug("Evento de auditoría enviado por HTTP con éxito para shipment: {}", shipmentId);
            } catch (Exception ex) {
                log.warn("No se pudo registrar evento de auditoría para shipment {}: {}", shipmentId, ex.getMessage());
            }
        }
        return envelope;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventEnvelope {
        private String type;
        private String eventId;
        private String timestamp;
        private String traceId;
        private String correlationId;
        private boolean simulateFailure;
        private AuditEventPayload payload;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditEventPayload {
        private Long shipmentId;
        private String trackingNumber;
        private String eventType;
        private String previousStatus;
        private String newStatus;
        private String performedBy;
        private String userRole;
        private String details;
        private String timestamp;
    }
}