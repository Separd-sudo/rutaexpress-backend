package com.rutaexpress.report.kafka;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Evento consumido desde el topic audit.timeline (contrato publicado por ms-rutaexpress-audit
 * en AuditTimelineRecord). Responde a la pregunta: quien hizo que, cuando y desde donde.
 *
 * Las validaciones JSR-380 aseguran que el evento trae lo minimo para contabilizarlo:
 * si falta eventId (clave de idempotencia) o la accion (what), el mensaje no se reintenta
 * y se envia directo a audit.timeline.report.DLT.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditTimelineEvent {

    /** Identificador unico del evento; tambien es la key del mensaje en Kafka. */
    @NotBlank(message = "eventId es obligatorio (idempotencia)")
    private String eventId;

    private Long shipmentId;
    private String trackingNumber;

    /** Usuario que ejecuto la accion (ej: despachador@rutaexpress.cl). */
    private String who;

    /** Rol del usuario segun el token de Azure (Admin, Despachador, etc.). */
    private String role;

    /** Accion realizada (ej: SHIPMENT_CREATED, STATUS_CHANGED). */
    @NotBlank(message = "what (accion) es obligatorio")
    private String what;

    private String previousStatus;
    private String newStatus;

    /** Momento en que ocurrio la accion (ISO-8601). */
    private String when;

    /** Origen de la accion: IP del cliente o microservicio emisor. */
    private String from;

    private String traceId;
}
