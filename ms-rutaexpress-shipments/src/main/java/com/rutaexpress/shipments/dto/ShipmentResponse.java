package com.rutaexpress.shipments.dto;

import com.rutaexpress.shipments.entity.ShipmentStatus;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentResponse {
    private Long id;
    private String trackingNumber;
    private Long serviceId;
    private String serviceCode;
    private String senderName;
    private String senderAddress;
    private String senderPhone;
    private String recipientName;
    private String recipientAddress;
    private String recipientEmail;
    private String recipientPhone;
    private Double weightKg;
    private Double distanceKm;
    private BigDecimal declaredValue;
    private BigDecimal shippingCost;
    private ShipmentStatus status;
    private Boolean wasAccepted;
    private LocalDateTime acceptedAt;
    private String createdBy;
    private String notes;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}