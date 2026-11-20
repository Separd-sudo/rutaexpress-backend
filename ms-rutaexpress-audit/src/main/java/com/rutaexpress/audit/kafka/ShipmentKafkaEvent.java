package com.rutaexpress.audit.kafka;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evento consumido desde shipments.events (contrato publicado por ms-rutaexpress-shipments).
 * Las anotaciones JSR-380 son la primera linea de defensa: un evento invalido
 * no se reintenta, va directo a shipments.events.audit.DLT.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentKafkaEvent {

    @NotBlank(message = "eventId es obligatorio (idempotencia)")
    private String eventId;

    @NotBlank(message = "type es obligatorio")
    private String type;

    private String timestamp;
    private String traceId;
    private String correlationId;
    private String source;

    @NotNull(message = "payload es obligatorio")
    @Valid
    private Payload payload;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Payload {
        private Long shipmentId;

        @NotBlank(message = "trackingNumber es obligatorio")
        private String trackingNumber;

        private Long serviceId;
        private String serviceCode;

        @NotBlank(message = "eventType es obligatorio")
        private String eventType;

        private String previousStatus;
        private String newStatus;
        private String performedBy;
        private String userRole;
        private String details;
        private String timestamp;
    }
}
