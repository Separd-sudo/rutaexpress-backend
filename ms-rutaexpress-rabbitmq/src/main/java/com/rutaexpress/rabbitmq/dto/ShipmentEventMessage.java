package com.rutaexpress.rabbitmq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShipmentEventMessage {
    private Long shipmentId;
    private String trackingNumber;
    private String eventType;
    private String previousStatus;
    private String newStatus;
    private String performedBy;
    private String userRole;
    private String details;
    private String timestamp;
}
