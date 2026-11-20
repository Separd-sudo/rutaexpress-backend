package com.rutaexpress.report.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Hecho analitico: un registro por evento consumido de shipments.events (event_id UNIQUE = idempotencia). */
@Entity
@Table(name = "shipment_event_facts", indexes = {
        @Index(name = "idx_fact_event_time", columnList = "event_time"),
        @Index(name = "idx_fact_event_type", columnList = "event_type"),
        @Index(name = "idx_fact_tracking", columnList = "tracking_number")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipmentEventFact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(name = "shipment_id")
    private Long shipmentId;

    @Column(name = "tracking_number", nullable = false, length = 64)
    private String trackingNumber;

    @Column(name = "service_id")
    private Long serviceId;

    @Column(name = "service_code", length = 50)
    private String serviceCode;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "previous_status", length = 32)
    private String previousStatus;

    @Column(name = "new_status", length = 32)
    private String newStatus;

    @Column(name = "performed_by", length = 128)
    private String performedBy;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    @Column(name = "kafka_partition")
    private Integer kafkaPartition;

    @Column(name = "kafka_offset")
    private Long kafkaOffset;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;
}
