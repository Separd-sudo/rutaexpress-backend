package com.rutaexpress.rabbitmq.service;

import com.rutaexpress.rabbitmq.dto.EventEnvelope;
import com.rutaexpress.rabbitmq.dto.ShipmentEventMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

@Service
@Slf4j
public class RabbitMQEventConsumerService {

    private static final int MAX_RECENT_EVENTS = 100;

    private final RabbitTemplate rabbitTemplate;
    private final RestClient auditRestClient;
    private final String directExchangeName;
    private final String warehouseDirectRk;
    private final String labelDirectRk;

    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();
    private final Deque<ShipmentEventMessage> processedEvents = new ConcurrentLinkedDeque<>();
    private final Deque<Map<String, Object>> dlqEvents = new ConcurrentLinkedDeque<>();
    private final AtomicLong totalProcessedCount = new AtomicLong(0);
    private final AtomicLong totalDlqCount = new AtomicLong(0);

    public RabbitMQEventConsumerService(
            RabbitTemplate rabbitTemplate,
            RestClient.Builder restClientBuilder,
            @Value("${services.audit.url:http://127.0.0.1:8083}") String auditServiceUrl,
            @Value("${rabbitmq.exchanges.direct:cmd.direct}") String directExchangeName,
            @Value("${rabbitmq.routing-keys.warehouse-direct:warehouse.ticket}") String warehouseDirectRk,
            @Value("${rabbitmq.routing-keys.label-direct:label.gen}") String labelDirectRk) {
        this.rabbitTemplate = rabbitTemplate;
        this.auditRestClient = restClientBuilder.baseUrl(auditServiceUrl).build();
        this.directExchangeName = directExchangeName;
        this.warehouseDirectRk = warehouseDirectRk;
        this.labelDirectRk = labelDirectRk;
    }

    public boolean isDuplicateEvent(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return false;
        }
        return !processedEventIds.add(eventId);
    }

    public void recordProcessedEvent(String domainQueue, EventEnvelope envelope) {
        totalProcessedCount.incrementAndGet();
        ShipmentEventMessage event = envelope != null ? envelope.getPayload() : null;
        if (event == null) {
            return;
        }
        if (event.getTimestamp() == null || event.getTimestamp().isBlank()) {
            event.setTimestamp(OffsetDateTime.now().toString());
        }

        processedEvents.addFirst(event);
        while (processedEvents.size() > MAX_RECENT_EVENTS) {
            processedEvents.removeLast();
        }

        forwardToAuditService(event);
    }

    public void recordDlqEvent(String dlqName, EventEnvelope envelope, String reason) {
        totalDlqCount.incrementAndGet();
        Map<String, Object> dlqRecord = new LinkedHashMap<>();
        dlqRecord.put("dlqName", dlqName);
        dlqRecord.put("quarantinedAt", OffsetDateTime.now().toString());
        dlqRecord.put("reason", reason);
        dlqRecord.put("eventId", envelope != null ? envelope.getEventId() : "N/A");
        dlqRecord.put("type", envelope != null ? envelope.getType() : "UNKNOWN");
        dlqRecord.put("payload", envelope != null ? envelope.getPayload() : null);

        dlqEvents.addFirst(dlqRecord);
        while (dlqEvents.size() > MAX_RECENT_EVENTS) {
            dlqEvents.removeLast();
        }
    }

    public EventEnvelope publishEvent(ShipmentEventMessage event, boolean simulateFailure) {
        if (event.getTimestamp() == null || event.getTimestamp().isBlank()) {
            event.setTimestamp(OffsetDateTime.now().toString());
        }

        String routingKey = resolveRoutingKeyForEvent(event);
        EventEnvelope envelope = EventEnvelope.builder()
                .type(event.getEventType() != null ? event.getEventType() : "SHIPMENT_EVENT")
                .eventId(UUID.randomUUID().toString())
                .timestamp(event.getTimestamp())
                .traceId(UUID.randomUUID().toString())
                .correlationId(event.getTrackingNumber() != null ? event.getTrackingNumber() : UUID.randomUUID().toString())
                .simulateFailure(simulateFailure)
                .payload(event)
                .build();

        rabbitTemplate.convertAndSend(directExchangeName, routingKey, envelope);
        log.info("[RabbitMQ Producer] Envelope eventId={} publicado en exchange='{}' con routingKey='{}' (simulateFailure={})",
                envelope.getEventId(), directExchangeName, routingKey, simulateFailure);
        return envelope;
    }

    private String resolveRoutingKeyForEvent(ShipmentEventMessage event) {
        String newStatus = event.getNewStatus() != null ? event.getNewStatus().toUpperCase() : "";
        return switch (newStatus) {
            case "CREADO" -> labelDirectRk;
            default -> warehouseDirectRk;
        };
    }

    public List<ShipmentEventMessage> getRecentProcessedEvents() {
        return new ArrayList<>(processedEvents);
    }

    public List<Map<String, Object>> getRecentDlqEvents() {
        return new ArrayList<>(dlqEvents);
    }

    public Map<String, Object> getMetrics() {
        long processed = totalProcessedCount.get();
        long dlq = totalDlqCount.get();
        long total = processed + dlq;
        double dlqRate = total > 0 ? Math.round(((double) dlq / total) * 10000.0) / 100.0 : 0.0;

        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("totalProcessed", processed);
        metrics.put("totalInDlq", dlq);
        metrics.put("dlqRatePercent", dlqRate);
        metrics.put("uniqueEventIdsTracked", processedEventIds.size());
        return metrics;
    }

    private void forwardToAuditService(ShipmentEventMessage event) {
        try {
            auditRestClient.post()
                    .uri("/api/audit/events")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(event)
                    .retrieve()
                    .toBodilessEntity();
            log.debug("[RabbitMQ -> Audit] Evento persistido en ms-rutaexpress-audit para envío {}", event.getShipmentId());
        } catch (Exception ex) {
            log.warn("[RabbitMQ -> Audit] No se pudo reenviar a ms-rutaexpress-audit: {}", ex.getMessage());
        }
    }
}
