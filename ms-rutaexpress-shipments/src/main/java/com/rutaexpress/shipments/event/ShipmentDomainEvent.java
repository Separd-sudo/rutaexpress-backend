package com.rutaexpress.shipments.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evento publicado en el topic shipments.events.
 * Envelope comun (type, eventId, timestamp, traceId, correlationId) + payload de negocio.
 * Key del record Kafka = trackingNumber, para que todos los eventos de un envio
 * caigan en la misma particion y se consuman en orden.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentDomainEvent {
    private String type;
    private String eventId;
    private String timestamp;
    private String traceId;
    private String correlationId;
    private String source;
    private Payload payload;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Payload {
        private Long shipmentId;
        private String trackingNumber;
        private Long serviceId;
        private String serviceCode;
        private String eventType;
        private String previousStatus;
        private String newStatus;
        private String performedBy;
        private String userRole;
        private String details;
        private String timestamp;
    }
}
