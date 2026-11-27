package com.rutaexpress.audit.kafka;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Registro publicado en audit.timeline: quien / que / cuando / desde donde.
 * Key = eventId, por lo que la compactacion del topic elimina re-publicaciones
 * del mismo evento y la politica delete purga lo mas antiguo que la retencion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditTimelineRecord {
    private String eventId;
    private Long shipmentId;
    private String trackingNumber;
    private String who;
    private String role;
    private String what;
    private String previousStatus;
    private String newStatus;
    private String when;
    private String from;
    private String traceId;
}
