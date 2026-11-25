package com.rutaexpress.report.kafka;

import com.rutaexpress.report.service.AuditActivityService;
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
 * Consumidor del topic audit.timeline (segundo topic del flujo de streaming).
 *
 * Flujo: shipments-svc -> shipments.events -> audit-svc -> audit.timeline -> report-svc (este listener).
 * Usa su propio consumer group (report-timeline-svc) para que el avance (offsets) y el lag de este
 * topic se monitoreen por separado del consumo de shipments.events.
 *
 * El listener solo se ocupa de la mensajeria (headers, traceId); la logica de negocio
 * vive en AuditActivityService.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditTimelineListener {

    private final AuditActivityService activityService;

    @KafkaListener(
            id = "report-audit-timeline",
            groupId = "${kafka.consumer.timeline-group-id:report-timeline-svc}",
            topics = "${kafka.topics.audit-timeline:audit.timeline}",
            containerFactory = "auditTimelineListenerFactory")
    public void onAuditTimeline(
            @Payload @Valid AuditTimelineEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset) {

        // El traceId viaja dentro del evento: se agrega al MDC para correlacionar los logs de punta a punta
        MDC.put("traceId", event.getTraceId() != null ? event.getTraceId() : "-");
        try {
            activityService.ingest(event, partition, offset);
        } finally {
            MDC.remove("traceId");
        }
    }
}
