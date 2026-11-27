package com.rutaexpress.audit.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateAuditEventRequest {

    /** Opcional: si el productor envia su eventId, se usa para deduplicar (idempotencia). */
    private String eventId;
    private String source;

    private Long shipmentId;
    private String trackingNumber;

    @NotBlank(message = "El tipo de evento es obligatorio")
    private String eventType;

    private String previousStatus;
    private String newStatus;

    @NotBlank(message = "El usuario ejecutor (performedBy) es obligatorio")
    private String performedBy;

    private String userRole;
    private String ipAddress;
    private String details;
}