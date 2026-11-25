package com.rutaexpress.report.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Hecho analitico de actividad de usuarios: un registro por evento consumido de audit.timeline.
 * Permite calcular KPIs de operacion (acciones por usuario, por rol y por tipo de accion).
 * event_id UNIQUE garantiza idempotencia aunque Kafka entregue el mismo mensaje dos veces.
 */
@Entity
@Table(name = "audit_activity_facts", indexes = {
        @Index(name = "idx_activity_event_time", columnList = "event_time"),
        @Index(name = "idx_activity_who", columnList = "who"),
        @Index(name = "idx_activity_action", columnList = "action")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditActivityFact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 64)
    private String eventId;

    @Column(name = "tracking_number", length = 64)
    private String trackingNumber;

    /** Usuario que realizo la accion; "desconocido" si el evento no lo informa. */
    @Column(name = "who", nullable = false, length = 128)
    private String who;

    @Column(name = "role", length = 64)
    private String role;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "new_status", length = 32)
    private String newStatus;

    @Column(name = "origin", length = 128)
    private String origin;

    @Column(name = "event_time", nullable = false)
    private LocalDateTime eventTime;

    /** Particion y offset de Kafka: trazabilidad del mensaje dentro del topic. */
    @Column(name = "kafka_partition")
    private Integer kafkaPartition;

    @Column(name = "kafka_offset")
    private Long kafkaOffset;

    @Column(name = "received_at", nullable = false)
    private LocalDateTime receivedAt;
}
