package com.rutaexpress.audit.kafka;

import com.rutaexpress.audit.service.AuditService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/**
 * Consume shipments.events (grupo audit-svc) y persiste el timeline del envio.
 * El traceId viaja en los headers de Kafka y se pone en el MDC para que todas
 * las lineas de log de este procesamiento lo incluyan.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShipmentEventsListener {

    /** eventType reservado para demostrar reintentos + DLT desde ms-rutaexpress-kafka. */
    public static final String FAILURE_SIMULATION = "KAFKA_FAILURE_SIMULATION";

    private final AuditService auditService;

    @KafkaListener(
            id = "audit-shipments-events",
            groupId = "${kafka.consumer.group-id:audit-svc}",
            topics = "${kafka.topics.shipments-events:shipments.events}",
            containerFactory = "shipmentEventListenerFactory")
    public void onShipmentEvent(
            @Payload @Valid ShipmentKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(name = KafkaHeaders.RECEIVED_KEY, required = false) String key,
            @Header(name = "traceId", required = false) String traceIdHeader) {

        String traceId = traceIdHeader != null ? traceIdHeader : event.getTraceId();
        MDC.put("traceId", traceId != null ? traceId : "-");
        try {
            log.info("[Kafka Consumer] Evento {} recibido eventId={} key={} partition={} offset={}",
                    event.getType(), event.getEventId(), key, partition, offset);

            if (FAILURE_SIMULATION.equalsIgnoreCase(event.getPayload().getEventType())) {
                throw new IllegalStateException("Fallo transitorio simulado para eventId=" + event.getEventId());
            }

            auditService.recordFromKafka(event, partition, offset);
        } finally {
            MDC.remove("traceId");
        }
    }
}
