package com.rutaexpress.audit.dto;

import lombok.*;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEventResponse {
    private Long id;
    private String eventId;
    private Long shipmentId;
    private String trackingNumber;
    private String eventType;
    private String previousStatus;
    private String newStatus;
    private String performedBy;
    private String userRole;
    private String ipAddress;
    private String details;
    private LocalDateTime timestamp;
}