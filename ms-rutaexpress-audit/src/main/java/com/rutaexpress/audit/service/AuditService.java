package com.rutaexpress.audit.service;

import com.rutaexpress.audit.dto.AuditEventResponse;
import com.rutaexpress.audit.dto.CreateAuditEventRequest;
import com.rutaexpress.audit.entity.AuditEvent;
import com.rutaexpress.audit.kafka.AuditTimelinePublisher;
import com.rutaexpress.audit.kafka.ShipmentKafkaEvent;
import com.rutaexpress.audit.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditEventRepository repository;
    private final AuditTimelinePublisher timelinePublisher;

    @Transactional
    public AuditEventResponse recordEvent(CreateAuditEventRequest request) {
        // Idempotencia: el mismo evento puede llegar por HTTP (via RabbitMQ) y por Kafka
        if (request.getEventId() != null && !request.getEventId().isBlank()) {
            Optional<AuditEvent> existing = repository.findByEventId(request.getEventId());
            if (existing.isPresent()) {
                log.info("Evento {} ya registrado, se ignora duplicado (HTTP)", request.getEventId());
                return mapToResponse(existing.get());
            }
        }

        AuditEvent event = AuditEvent.builder()
                .eventId(request.getEventId() != null && !request.getEventId().isBlank()
                        ? request.getEventId() : "EVT-" + UUID.randomUUID())
                .shipmentId(request.getShipmentId())
                .trackingNumber(request.getTrackingNumber())
                .eventType(request.getEventType())
                .previousStatus(request.getPreviousStatus())
                .newStatus(request.getNewStatus())
                .performedBy(request.getPerformedBy())
                .userRole(request.getUserRole())
                .ipAddress(request.getIpAddress())
                .source(request.getSource() != null ? request.getSource() : "http")
                .details(request.getDetails())
                .timestamp(LocalDateTime.now())
                .build();

        AuditEvent saved = repository.save(event);
        log.info("Evento de auditoría registrado [{}] - Shipment: {}, Usuario: {}",
                saved.getEventType(), saved.getShipmentId(), saved.getPerformedBy());
        // Cada evento auditado (venga por HTTP/RabbitMQ o por Kafka) se publica una sola vez en audit.timeline
        timelinePublisher.publish(saved, null);
        return mapToResponse(saved);
    }

    /**
     * Persiste un evento consumido desde Kafka con patron Check-then-Act:
     * si el eventId ya existe es un duplicado (entrega at-least-once) y se ignora.
     * Si dos entregas compiten, la constraint UNIQUE de event_id hace fallar a una;
     * el error handler la reintenta y en el reintento se detecta como duplicado.
     */
    public void recordFromKafka(ShipmentKafkaEvent event, int partition, long offset) {
        if (repository.existsByEventId(event.getEventId())) {
            log.info("[Idempotencia] eventId={} ya procesado (partition={}, offset={}), se ignora",
                    event.getEventId(), partition, offset);
            return;
        }

        ShipmentKafkaEvent.Payload p = event.getPayload();
        AuditEvent saved = repository.save(AuditEvent.builder()
                .eventId(event.getEventId())
                .shipmentId(p.getShipmentId())
                .trackingNumber(p.getTrackingNumber())
                .eventType(p.getEventType())
                .previousStatus(p.getPreviousStatus())
                .newStatus(p.getNewStatus())
                .performedBy(p.getPerformedBy() != null ? p.getPerformedBy() : "SISTEMA")
                .userRole(p.getUserRole())
                .source(event.getSource() != null ? "kafka:" + event.getSource() : "kafka")
                .details(p.getDetails())
                .timestamp(parseTimestamp(event.getTimestamp()))
                .build());

        log.info("[Kafka -> Audit] Evento {} persistido para envío {} (eventId={})",
                saved.getEventType(), saved.getTrackingNumber(), saved.getEventId());
        timelinePublisher.publish(saved, event.getTraceId());
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> getTimelineForShipment(Long shipmentId) {
        return repository.findByShipmentIdOrderByTimestampAsc(shipmentId)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> searchEvents(String user, String eventType, LocalDateTime from, LocalDateTime to) {
        List<AuditEvent> list;
        if ((user == null || user.isBlank()) && (eventType == null || eventType.isBlank()) && from == null && to == null) {
            list = repository.findAllByOrderByTimestampDesc();
        } else {
            list = repository.filterEvents(user, eventType, from, to);
        }
        return list.stream()
                .map(this::mapToResponse)
                .toList();
    }

    private static LocalDateTime parseTimestamp(String ts) {
        if (ts == null || ts.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return OffsetDateTime.parse(ts).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }

    private AuditEventResponse mapToResponse(AuditEvent a) {
        return AuditEventResponse.builder()
                .id(a.getId())
                .eventId(a.getEventId())
                .shipmentId(a.getShipmentId())
                .trackingNumber(a.getTrackingNumber())
                .eventType(a.getEventType())
                .previousStatus(a.getPreviousStatus())
                .newStatus(a.getNewStatus())
                .performedBy(a.getPerformedBy())
                .userRole(a.getUserRole())
                .ipAddress(a.getIpAddress())
                .details(a.getDetails())
                .source(a.getSource())
                .timestamp(a.getTimestamp())
                .build();
    }
}
