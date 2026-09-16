package com.rutaexpress.shipments.service;

import com.rutaexpress.shipments.client.AuditClient;
import com.rutaexpress.shipments.client.CatalogClient;
import com.rutaexpress.shipments.dto.CreateShipmentRequest;
import com.rutaexpress.shipments.dto.ShipmentResponse;
import com.rutaexpress.shipments.dto.UpdateStatusRequest;
import com.rutaexpress.shipments.entity.Shipment;
import com.rutaexpress.shipments.entity.ShipmentStatus;
import com.rutaexpress.shipments.exception.InvalidStateTransitionException;
import com.rutaexpress.shipments.exception.ShipmentNotFoundException;
import com.rutaexpress.shipments.repository.ShipmentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
public class ShipmentService {

    private final ShipmentRepository repository;
    private final CatalogClient catalogClient;
    private final AuditClient auditClient;

    @Transactional
    public ShipmentResponse createShipment(CreateShipmentRequest request) {
        CatalogClient.ServiceInfo serviceInfo = catalogClient.getService(request.getServiceId());
        String serviceCode = serviceInfo != null ? serviceInfo.getCode() : "STANDARD";

        BigDecimal calculatedCost = BigDecimal.ZERO;
        if (serviceInfo != null && serviceInfo.getBasePrice() != null) {
            calculatedCost = serviceInfo.getBasePrice();
            if (request.getDistanceKm() != null && serviceInfo.getPricePerKm() != null) {
                BigDecimal kmCost = serviceInfo.getPricePerKm().multiply(BigDecimal.valueOf(request.getDistanceKm()));
                calculatedCost = calculatedCost.add(kmCost);
            }
        }

        String tracking = generateTrackingNumber();

        Shipment shipment = Shipment.builder()
                .trackingNumber(tracking)
                .serviceId(request.getServiceId())
                .serviceCode(serviceCode)
                .senderName(request.getSenderName())
                .senderAddress(request.getSenderAddress())
                .senderPhone(request.getSenderPhone())
                .recipientName(request.getRecipientName())
                .recipientAddress(request.getRecipientAddress())
                .recipientEmail(request.getRecipientEmail())
                .recipientPhone(request.getRecipientPhone())
                .weightKg(request.getWeightKg())
                .distanceKm(request.getDistanceKm())
                .declaredValue(request.getDeclaredValue())
                .shippingCost(calculatedCost)
                .status(ShipmentStatus.CREADO)
                .wasAccepted(false)
                .createdBy(request.getCreatedBy() != null ? request.getCreatedBy() : "cliente@rutaexpress.cl")
                .notes(request.getNotes())
                .build();

        Shipment saved = repository.save(shipment);
        log.info("Envío creado exitosamente ID: {}, Tracking: {}", saved.getId(), saved.getTrackingNumber());

        auditClient.sendAuditEvent(
                saved.getId(),
                saved.getTrackingNumber(),
                "SHIPMENT_CREATED",
                null,
                ShipmentStatus.CREADO.name(),
                saved.getCreatedBy(),
                "Cliente",
                "Envío creado en el sistema con servicio " + serviceCode
        );

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public ShipmentResponse getShipmentById(Long id) {
        Shipment s = repository.findById(id)
                .orElseThrow(() -> new ShipmentNotFoundException("Envío no encontrado con ID: " + id));
        return mapToResponse(s);
    }

    @Transactional(readOnly = true)
    public ShipmentResponse getShipmentByTracking(String trackingNumber) {
        Shipment s = repository.findByTrackingNumber(trackingNumber)
                .orElseThrow(() -> new ShipmentNotFoundException("Envío no encontrado con tracking: " + trackingNumber));
        return mapToResponse(s);
    }

    @Transactional(readOnly = true)
    public List<ShipmentResponse> getShipments(ShipmentStatus status, LocalDateTime from, LocalDateTime to) {
        return repository.filterShipments(status, from, to)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public ShipmentResponse updateStatus(Long id, UpdateStatusRequest request) {
        Shipment shipment = repository.findById(id)
                .orElseThrow(() -> new ShipmentNotFoundException("Envío no encontrado con ID: " + id));

        ShipmentStatus current = shipment.getStatus();
        ShipmentStatus target = request.getStatus();

        // Regla clave: Validar máquina de estados y si fue aceptado
        if (!current.canTransitionTo(target, Boolean.TRUE.equals(shipment.getWasAccepted()))) {
            throw new InvalidStateTransitionException(String.format(
                    "Transición no permitida de estado [%s] a [%s]. Regla: No se puede pasar a EN_RUTA sin haber sido ACEPTADO previamente, y no se pueden alterar estados finalizados.",
                    current, target
            ));
        }

        // Si transiciona a ACEPTADO, se debe descontar capacidad de flota en catálogo
        if (target == ShipmentStatus.ACEPTADO && current != ShipmentStatus.ACEPTADO) {
            catalogClient.reserveCapacity(shipment.getServiceId(), 1);
            shipment.setWasAccepted(true);
            shipment.setAcceptedAt(LocalDateTime.now());
            log.info("Envío {} ACEPTADO: Capacidad de flota descontada en catálogo", shipment.getTrackingNumber());
        }

        // Si se CANCELA y ya había sido aceptado, se libera la capacidad
        if (target == ShipmentStatus.CANCELADO && Boolean.TRUE.equals(shipment.getWasAccepted())) {
            catalogClient.releaseCapacity(shipment.getServiceId(), 1);
            log.info("Envío {} CANCELADO: Capacidad de flota restituida en catálogo", shipment.getTrackingNumber());
        }

        shipment.setStatus(target);
        if (request.getNote() != null && !request.getNote().isBlank()) {
            shipment.setNotes(request.getNote());
        }

        Shipment updated = repository.save(shipment);
        log.info("Estado de envío {} actualizado de {} a {} por {}",
                updated.getTrackingNumber(), current, target, request.getPerformedBy());

        auditClient.sendAuditEvent(
                updated.getId(),
                updated.getTrackingNumber(),
                "STATUS_CHANGED_" + target.name(),
                current.name(),
                target.name(),
                request.getPerformedBy(),
                request.getUserRole() != null ? request.getUserRole() : "Operador",
                request.getNote() != null ? request.getNote() : "Cambio de estado a " + target
        );

        return mapToResponse(updated);
    }

    private String generateTrackingNumber() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        int randomPart = 1000 + new Random().nextInt(9000);
        return "RTX-" + datePart + "-" + randomPart;
    }

    private ShipmentResponse mapToResponse(Shipment s) {
        return ShipmentResponse.builder()
                .id(s.getId())
                .trackingNumber(s.getTrackingNumber())
                .serviceId(s.getServiceId())
                .serviceCode(s.getServiceCode())
                .senderName(s.getSenderName())
                .senderAddress(s.getSenderAddress())
                .senderPhone(s.getSenderPhone())
                .recipientName(s.getRecipientName())
                .recipientAddress(s.getRecipientAddress())
                .recipientEmail(s.getRecipientEmail())
                .recipientPhone(s.getRecipientPhone())
                .weightKg(s.getWeightKg())
                .distanceKm(s.getDistanceKm())
                .declaredValue(s.getDeclaredValue())
                .shippingCost(s.getShippingCost())
                .status(s.getStatus())
                .wasAccepted(s.getWasAccepted())
                .acceptedAt(s.getAcceptedAt())
                .createdBy(s.getCreatedBy())
                .notes(s.getNotes())
                .createdAt(s.getCreatedAt())
                .updatedAt(s.getUpdatedAt())
                .build();
    }
}