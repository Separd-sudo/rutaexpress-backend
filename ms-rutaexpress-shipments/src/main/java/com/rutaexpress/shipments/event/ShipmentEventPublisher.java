package com.rutaexpress.shipments.event;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Publica los eventos del ciclo de vida del envio en Kafka (shipments.events).
 * Alimenta a ms-rutaexpress-audit (timeline) y ms-rutaexpress-report (KPIs)
 * sin bloquear el flujo principal: si Kafka no responde se registra y se sigue.
 */
@Component
@Slf4j
public class ShipmentEventPublisher {

    public static final String HEADER_TRACE_ID = "traceId";
    public static final String HEADER_CORRELATION_ID = "correlationId";
    public static final String HEADER_EVENT_TYPE = "eventType";

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String topic;
    private final boolean enabled;

    /**
     * Un unico hilo dedicado: los eventos se entregan al productor en el mismo orden en que
     * ocurrieron (orden por key garantizado) y la request HTTP nunca espera a Kafka,
     * aunque el cluster no este disponible (max.block.ms).
     */
    private final ExecutorService sender = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "kafka-shipment-events");
        t.setDaemon(true);
        return t;
    });

    public ShipmentEventPublisher(
            KafkaTemplate<String, Object> shipmentEventKafkaTemplate,
            @Value("${kafka.topics.shipments-events:shipments.events}") String topic,
            @Value("${kafka.enabled:true}") boolean enabled) {
        this.kafkaTemplate = shipmentEventKafkaTemplate;
        this.topic = topic;
        this.enabled = enabled;
    }

    public void publish(ShipmentDomainEvent event) {
        if (!enabled) {
            log.debug("Kafka deshabilitado (kafka.enabled=false); evento {} no publicado", event.getEventId());
            return;
        }
        String key = event.getPayload() != null && event.getPayload().getTrackingNumber() != null
                ? event.getPayload().getTrackingNumber()
                : event.getEventId();

        sender.execute(() -> send(event, key));
    }

    private void send(ShipmentDomainEvent event, String key) {
        ProducerRecord<String, Object> record = new ProducerRecord<>(topic, key, event);
        addHeader(record, HEADER_TRACE_ID, event.getTraceId());
        addHeader(record, HEADER_CORRELATION_ID, event.getCorrelationId());
        addHeader(record, HEADER_EVENT_TYPE, event.getType());

        try {
            kafkaTemplate.send(record).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("[Kafka Producer] Fallo al publicar eventId={} traceId={} en {}: {}",
                            event.getEventId(), event.getTraceId(), topic, ex.getMessage());
                } else {
                    log.info("[Kafka Producer] eventId={} type={} key={} -> {}-{}@offset {} (traceId={})",
                            event.getEventId(), event.getType(), key,
                            result.getRecordMetadata().topic(), result.getRecordMetadata().partition(),
                            result.getRecordMetadata().offset(), event.getTraceId());
                }
            });
        } catch (Exception ex) {
            // max.block.ms agotado (sin metadata del cluster): no se interrumpe el core del negocio
            log.error("[Kafka Producer] Kafka no disponible, eventId={} no publicado: {}", event.getEventId(), ex.getMessage());
        }
    }

    @PreDestroy
    void shutdown() {
        sender.shutdown();
    }

    private static void addHeader(ProducerRecord<String, Object> record, String name, String value) {
        if (value != null) {
            record.headers().add(name, value.getBytes(StandardCharsets.UTF_8));
        }
    }
}
