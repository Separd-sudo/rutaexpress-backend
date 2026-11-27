package com.rutaexpress.shipments.client;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.rutaexpress.shipments.event.ShipmentDomainEvent;
import com.rutaexpress.shipments.event.ShipmentEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.MediaType;
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
    private final ShipmentEventPublisher kafkaPublisher;
    private final TaskExecutor taskExecutor;

    public AuditClient(
            RestClient.Builder builder,
            RabbitTemplate rabbitTemplate,
            ShipmentEventPublisher kafkaPublisher,
            @Qualifier("applicationTaskExecutor") TaskExecutor taskExecutor,
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
        this.kafkaPublisher = kafkaPublisher;
        this.taskExecutor = taskExecutor;
    }

    /**
     * Despacha un evento del ciclo de vida del envio:
     *  - Kafka se encola en orden desde el hilo de la request (ShipmentEventPublisher usa un
     *    hilo dedicado): los eventos de un mismo envio entran a la particion en orden.
     *  - RabbitMQ / fallback HTTP se ejecutan en segundo plano, como antes con @Async.
     */
    public void sendAuditEvent(Long shipmentId, String trackingNumber, Long serviceId, String serviceCode,
                               String eventType, String previousStatus, String newStatus, String performedBy,
                               String userRole, String details) {
        EventEnvelope envelope = buildEnvelope(shipmentId, trackingNumber, eventType, previousStatus, newStatus,
                performedBy, userRole, details, false);

        // Streaming: el mismo evento (mismo eventId) va a Kafka para auditoria y reporteria
        kafkaPublisher.publish(ShipmentDomainEvent.builder()
                .type(envelope.getType())
                .eventId(envelope.getEventId())
                .timestamp(envelope.getTimestamp())
                .traceId(envelope.getTraceId())
                .correlationId(envelope.getCorrelationId())
                .source("ms-rutaexpress-shipments")
                .payload(ShipmentDomainEvent.Payload.builder()
                        .shipmentId(shipmentId)
                        .trackingNumber(trackingNumber)
                        .serviceId(serviceId)
                        .serviceCode(serviceCode)
                        .eventType(eventType)
                        .previousStatus(previousStatus)
                        .newStatus(newStatus)
                        .performedBy(envelope.getPayload().getPerformedBy())
                        .userRole(envelope.getPayload().getUserRole())
                        .details(details)
                        .timestamp(envelope.getTimestamp())
                        .build())
                .build());

        taskExecutor.execute(() -> dispatchCommand(envelope, newStatus, shipmentId));
    }

    public EventEnvelope sendDemoDlqEvent(String trackingNumber, String details) {
        EventEnvelope envelope = buildEnvelope(
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
        dispatchCommand(envelope, "ACEPTADO", 9999L);
        return envelope;
    }

    private EventEnvelope buildEnvelope(Long shipmentId, String trackingNumber, String eventType,
                                               String previousStatus, String newStatus, String performedBy,
                                               String userRole, String details, boolean simulateFailure) {
        String now = OffsetDateTime.now().toString();
        String eventId = UUID.randomUUID().toString();
        AuditEventPayload payload = AuditEventPayload.builder()
                .eventId(eventId)
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
        // 1. Arma el sobre/mensaje con datos del envío (UUID, tracking, estado)
        return EventEnvelope.builder()
                .type(eventType != null ? eventType : "SHIPMENT_EVENT")
                .eventId(eventId)
                .timestamp(now)
                .traceId(UUID.randomUUID().toString())
                .correlationId(trackingNumber != null ? trackingNumber : UUID.randomUUID().toString())
                .simulateFailure(simulateFailure)
                .payload(payload)
                .build();
    }

    private void dispatchCommand(EventEnvelope envelope, String newStatus, Long shipmentId) {
        boolean simulateFailure = envelope.isSimulateFailure();
        if (rabbitEnabled) {
            try {
                String routingKey = "CREADO".equalsIgnoreCase(newStatus) ? labelDirectRk : warehouseDirectRk;
                // 2. Publica de forma asíncrona indicando el Exchange y la Routing Key
                rabbitTemplate.convertAndSend(directExchangeName, routingKey, envelope);
                log.info("Evento publicado en RabbitMQ (exchange={}, routingKey={}, eventId={}, simulateFailure={}) para shipment: {}",
                        directExchangeName, routingKey, envelope.getEventId(), simulateFailure, shipmentId);
                return;
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
                        .body(envelope.getPayload())
                        .retrieve()
                        .toBodilessEntity();
                log.debug("Evento de auditoría enviado por HTTP con éxito para shipment: {}", shipmentId);
            } catch (Exception ex) {
                log.warn("No se pudo registrar evento de auditoría para shipment {}: {}", shipmentId, ex.getMessage());
            }
        }
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
        private String eventId;
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