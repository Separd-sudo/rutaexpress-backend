package com.rutaexpress.report.kafka;

/** Error permanente de negocio: el evento nunca podra procesarse, se envia directo a la DLT. */
public class InvalidReportEventException extends RuntimeException {
    public InvalidReportEventException(String message) {
        super(message);
    }
}
