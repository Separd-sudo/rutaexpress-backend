package com.rutaexpress.report.service;

import com.rutaexpress.report.entity.ShipmentEventFact;
import com.rutaexpress.report.entity.ShipmentSnapshot;
import com.rutaexpress.report.kafka.ShipmentKafkaEvent;
import com.rutaexpress.report.repository.ShipmentEventFactRepository;
import com.rutaexpress.report.repository.ShipmentSnapshotRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * Ingesta de eventos Kafka para reporteria. Idempotente (event_id UNIQUE + check previo)
 * y transaccional: el hecho y la proyeccion del envio se guardan juntos o no se guardan.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReportIngestionService {

    private final ShipmentEventFactRepository factRepository;
    private final ShipmentSnapshotRepository snapshotRepository;

    @Transactional
    public void ingest(ShipmentKafkaEvent event, int partition, long offset) {
        if (factRepository.existsByEventId(event.getEventId())) {
            log.info("[Idempotencia] eventId={} ya contabilizado, se ignora", event.getEventId());
            return;
        }
        ShipmentKafkaEvent.Payload p = event.getPayload();
        LocalDateTime eventTime = parse(event.getTimestamp());

        factRepository.save(ShipmentEventFact.builder()
                .eventId(event.getEventId())
                .shipmentId(p.getShipmentId())
                .trackingNumber(p.getTrackingNumber())
                .serviceId(p.getServiceId())
                .serviceCode(p.getServiceCode())
                .eventType(p.getEventType())
                .previousStatus(p.getPreviousStatus())
                .newStatus(p.getNewStatus())
                .performedBy(p.getPerformedBy())
                .eventTime(eventTime)
                .kafkaPartition(partition)
                .kafkaOffset(offset)
                .receivedAt(LocalDateTime.now())
                .build());

        ShipmentSnapshot snap = snapshotRepository.findById(p.getTrackingNumber())
                .orElseGet(() -> ShipmentSnapshot.builder().trackingNumber(p.getTrackingNumber()).build());
        if (p.getShipmentId() != null) snap.setShipmentId(p.getShipmentId());
        if (p.getServiceId() != null) snap.setServiceId(p.getServiceId());
        if (p.getServiceCode() != null) snap.setServiceCode(p.getServiceCode());

        String status = p.getNewStatus();
        if ("CREADO".equals(status) && snap.getCreatedAt() == null) snap.setCreatedAt(eventTime);
        if ("ACEPTADO".equals(status)) snap.setAcceptedAt(eventTime);
        if ("ENTREGADO".equals(status)) snap.setDeliveredAt(eventTime);

        // Solo avanza el estado si el evento no es mas antiguo que el ultimo aplicado
        if (status != null && (snap.getLastEventAt() == null || !eventTime.isBefore(snap.getLastEventAt()))) {
            snap.setCurrentStatus(status);
            snap.setLastEventAt(eventTime);
        }
        snapshotRepository.save(snap);

        log.info("[Kafka -> Report] {} {} -> {} contabilizado (partition={}, offset={})",
                p.getTrackingNumber(), p.getEventType(), status, partition, offset);
    }

    private static LocalDateTime parse(String ts) {
        if (ts == null || ts.isBlank()) return LocalDateTime.now();
        try {
            return OffsetDateTime.parse(ts).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception e) {
            return LocalDateTime.now();
        }
    }
}
