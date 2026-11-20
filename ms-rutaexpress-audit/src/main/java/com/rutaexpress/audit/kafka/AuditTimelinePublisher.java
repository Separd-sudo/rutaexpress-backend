package com.rutaexpress.audit.kafka;

import com.rutaexpress.audit.entity.AuditEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/** Publica cada evento auditado en audit.timeline (historial quien/que/cuando/desde donde). */
@Component
@Slf4j
public class AuditTimelinePublisher {

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final String topic;

    public AuditTimelinePublisher(KafkaTemplate<Object, Object> auditKafkaTemplate,
                                  @Value("${kafka.topics.audit-timeline:audit.timeline}") String topic) {
        this.kafkaTemplate = auditKafkaTemplate;
        this.topic = topic;
    }

    public void publish(AuditEvent e, String traceId) {
        AuditTimelineRecord record = AuditTimelineRecord.builder()
                .eventId(e.getEventId())
                .shipmentId(e.getShipmentId())
                .trackingNumber(e.getTrackingNumber())
                .who(e.getPerformedBy())
                .role(e.getUserRole())
                .what(e.getEventType())
                .previousStatus(e.getPreviousStatus())
                .newStatus(e.getNewStatus())
                .when(e.getTimestamp() != null ? e.getTimestamp().toString() : null)
                .from(e.getIpAddress() != null ? e.getIpAddress() : e.getSource())
                .traceId(traceId)
                .build();
        try {
            kafkaTemplate.send(topic, e.getEventId(), record).whenComplete((r, ex) -> {
                if (ex != null) {
                    log.warn("[audit.timeline] No se pudo publicar eventId={}: {}", e.getEventId(), ex.getMessage());
                } else {
                    log.debug("[audit.timeline] eventId={} -> partition {} offset {}", e.getEventId(),
                            r.getRecordMetadata().partition(), r.getRecordMetadata().offset());
                }
            });
        } catch (Exception ex) {
            log.warn("[audit.timeline] Kafka no disponible para eventId={}: {}", e.getEventId(), ex.getMessage());
        }
    }
}
