package com.rutaexpress.report.kafka;

import com.rutaexpress.report.service.ReportIngestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

/** Consume shipments.events con el grupo report-svc (independiente de audit-svc: cada grupo lee todo). */
@Component
@RequiredArgsConstructor
@Slf4j
public class ShipmentEventsListener {

    public static final String FAILURE_SIMULATION = "KAFKA_FAILURE_SIMULATION";

    private final ReportIngestionService ingestionService;

    @KafkaListener(
            id = "report-shipments-events",
            groupId = "${kafka.consumer.group-id:report-svc}",
            topics = "${kafka.topics.shipments-events:shipments.events}",
            containerFactory = "shipmentEventListenerFactory")
    public void onShipmentEvent(
            @Payload @Valid ShipmentKafkaEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(name = "traceId", required = false) String traceIdHeader) {

        String traceId = traceIdHeader != null ? traceIdHeader : event.getTraceId();
        MDC.put("traceId", traceId != null ? traceId : "-");
        try {
            if (FAILURE_SIMULATION.equalsIgnoreCase(event.getPayload().getEventType())) {
                throw new IllegalStateException("Fallo transitorio simulado para eventId=" + event.getEventId());
            }
            ingestionService.ingest(event, partition, offset);
        } finally {
            MDC.remove("traceId");
        }
    }
}
