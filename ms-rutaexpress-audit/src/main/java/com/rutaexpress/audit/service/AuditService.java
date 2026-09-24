package com.rutaexpress.audit.service;

import com.rutaexpress.audit.dto.AuditEventResponse;
import com.rutaexpress.audit.dto.CreateAuditEventRequest;
import com.rutaexpress.audit.entity.AuditEvent;
import com.rutaexpress.audit.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditEventRepository repository;

    @Transactional
    public AuditEventResponse recordEvent(CreateAuditEventRequest request) {
        AuditEvent event = AuditEvent.builder()
                .eventId("EVT-" + UUID.randomUUID())
                .shipmentId(request.getShipmentId())
                .trackingNumber(request.getTrackingNumber())
                .eventType(request.getEventType())
                .previousStatus(request.getPreviousStatus())
                .newStatus(request.getNewStatus())
                .performedBy(request.getPerformedBy())
                .userRole(request.getUserRole())
                .ipAddress(request.getIpAddress())
                .details(request.getDetails())
                .timestamp(LocalDateTime.now())
                .build();

        AuditEvent saved = repository.save(event);
        log.info("Evento de auditoría registrado [{}] - Shipment: {}, Usuario: {}",
                saved.getEventType(), saved.getShipmentId(), saved.getPerformedBy());
        return mapToResponse(saved);
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
                .timestamp(a.getTimestamp())
                .build();
    }
}