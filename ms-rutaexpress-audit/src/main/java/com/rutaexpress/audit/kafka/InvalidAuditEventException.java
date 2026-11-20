package com.rutaexpress.audit.kafka;

/** Error permanente de negocio: el evento nunca podra procesarse, se envia directo a la DLT. */
public class InvalidAuditEventException extends RuntimeException {
    public InvalidAuditEventException(String message) {
        super(message);
    }
}
