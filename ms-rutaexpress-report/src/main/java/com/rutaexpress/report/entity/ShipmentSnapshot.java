package com.rutaexpress.report.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Proyeccion del estado actual de cada envio, reconstruida solo a partir de eventos Kafka. */
@Entity
@Table(name = "shipment_snapshots", indexes = {
        @Index(name = "idx_snapshot_status", columnList = "current_status"),
        @Index(name = "idx_snapshot_delivered", columnList = "delivered_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentSnapshot {

    @Id
    @Column(name = "tracking_number", length = 64)
    private String trackingNumber;

    @Column(name = "shipment_id")
    private Long shipmentId;

    @Column(name = "service_id")
    private Long serviceId;

    @Column(name = "service_code", length = 50)
    private String serviceCode;

    @Column(name = "current_status", length = 32)
    private String currentStatus;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "delivered_at")
    private LocalDateTime deliveredAt;

    @Column(name = "last_event_at")
    private LocalDateTime lastEventAt;
}
