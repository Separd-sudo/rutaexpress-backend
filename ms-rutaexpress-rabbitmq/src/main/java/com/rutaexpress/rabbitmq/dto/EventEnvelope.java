package com.rutaexpress.rabbitmq.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventEnvelope {
    private String type;
    private String eventId;
    private String timestamp;
    private String traceId;
    private String correlationId;
    private boolean simulateFailure;
    private ShipmentEventMessage payload;
}
