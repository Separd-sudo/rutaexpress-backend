package com.rutaexpress.kafka.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rutaexpress.kafka.config.KafkaClusterConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

/**
 * Laboratorio de streaming para demostrar en la evaluacion:
 *  - Key -> particion (orden por envio) y offsets.
 *  - Reintentos + DLT (fallo transitorio), validacion y deserializacion (fallo permanente).
 *  - Prueba de carga con productor tuneado (batch.size / linger.ms / compresion).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StreamingLabService {

    public static final String FAILURE_SIMULATION = "KAFKA_FAILURE_SIMULATION";

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /** Publica un evento de prueba valido en shipments.events y devuelve donde cayo. */
    public Map<String, Object> publishTestEvent(String trackingNumber, String eventType, String newStatus) throws Exception {
        Map<String, Object> event = buildEvent(trackingNumber, eventType, newStatus);
        return sendAndDescribe(KafkaClusterConfig.SHIPMENTS_EVENTS, trackingNumber,
                objectMapper.writeValueAsString(event), (String) event.get("traceId"));
    }

    /** Fallo transitorio: los consumidores lanzan excepcion -> 3 reintentos con backoff -> *.DLT */
    public Map<String, Object> simulateTransientFailure(String trackingNumber) throws Exception {
        Map<String, Object> event = buildEvent(trackingNumber, FAILURE_SIMULATION, null);
        Map<String, Object> result = sendAndDescribe(KafkaClusterConfig.SHIPMENTS_EVENTS, trackingNumber,
                objectMapper.writeValueAsString(event), (String) event.get("traceId"));
        result.put("expected", "Cada consumidor reintenta 3 veces (1s, 2s, 4s) y luego lo envia a shipments.events.audit.DLT y shipments.events.report.DLT");
        return result;
    }

    /** Fallo permanente: JSON invalido -> ErrorHandlingDeserializer -> DLT inmediato, sin reintentos. */
    public Map<String, Object> simulatePoisonPill(String trackingNumber) throws Exception {
        Map<String, Object> result = sendAndDescribe(KafkaClusterConfig.SHIPMENTS_EVENTS, trackingNumber,
                "{esto-no-es-json: " + UUID.randomUUID() + "", null);
        result.put("expected", "DeserializationException: no se reintenta, va directo a las DLT de cada consumidor");
        return result;
    }

    /** Fallo permanente: JSON valido pero sin eventId/payload -> Bean Validation -> DLT inmediato. */
    public Map<String, Object> simulateInvalidPayload(String trackingNumber) throws Exception {
        Map<String, Object> invalid = new LinkedHashMap<>();
        invalid.put("type", "SHIPMENT_CREATED");
        invalid.put("timestamp", OffsetDateTime.now().toString());
        invalid.put("payload", Map.of("trackingNumber", trackingNumber));
        Map<String, Object> result = sendAndDescribe(KafkaClusterConfig.SHIPMENTS_EVENTS, trackingNumber,
                objectMapper.writeValueAsString(invalid), null);
        result.put("expected", "MethodArgumentNotValidException (eventId / payload.eventType obligatorios): DLT sin reintentos");
        return result;
    }

    /** Prueba de carga (guia 3.3.2): rafaga de mensajes fire-and-forget al topic de carga. */
    public Map<String, Object> loadTest(int count, int keys) {
        String payload = "x".repeat(200);
        AtomicLong errors = new AtomicLong();
        long start = System.currentTimeMillis();
        IntStream.range(0, count).parallel().forEach(i ->
                kafkaTemplate.send(KafkaClusterConfig.LOADTEST, "shipment-" + (i % Math.max(1, keys)),
                                "{\"seq\":" + i + ",\"data\":\"" + payload + "\"}")
                        .whenComplete((r, ex) -> { if (ex != null) errors.incrementAndGet(); }));
        kafkaTemplate.flush();
        long ms = Math.max(1, System.currentTimeMillis() - start);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topic", KafkaClusterConfig.LOADTEST);
        result.put("messages", count);
        result.put("distinctKeys", keys);
        result.put("durationMs", ms);
        result.put("throughputMsgPerSec", Math.round(count / (ms / 1000.0)));
        result.put("errors", errors.get());
        result.put("hint", "Revisa GET /api/kafka/consumer-groups/loadtest-group/lag para ver el consumer lag bajar");
        return result;
    }

    private Map<String, Object> buildEvent(String trackingNumber, String eventType, String newStatus) {
        String now = OffsetDateTime.now().toString();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("shipmentId", null);
        payload.put("trackingNumber", trackingNumber);
        payload.put("eventType", eventType);
        payload.put("newStatus", newStatus);
        payload.put("performedBy", "ms-rutaexpress-kafka");
        payload.put("userRole", "Admin");
        payload.put("details", "Evento generado desde el laboratorio de streaming");
        payload.put("timestamp", now);

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("type", eventType);
        event.put("eventId", UUID.randomUUID().toString());
        event.put("timestamp", now);
        event.put("traceId", UUID.randomUUID().toString());
        event.put("correlationId", trackingNumber);
        event.put("source", "ms-rutaexpress-kafka");
        event.put("payload", payload);
        return event;
    }

    private Map<String, Object> sendAndDescribe(String topic, String key, String value, String traceId) throws Exception {
        ProducerRecord<String, String> record = new ProducerRecord<>(topic, key, value);
        if (traceId != null) record.headers().add("traceId", traceId.getBytes(StandardCharsets.UTF_8));
        RecordMetadata md = kafkaTemplate.send(record).get(10, TimeUnit.SECONDS).getRecordMetadata();
        log.info("[Lab] Publicado key={} -> {}-{}@{}", key, md.topic(), md.partition(), md.offset());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topic", md.topic());
        result.put("key", key);
        result.put("partition", md.partition());
        result.put("offset", md.offset());
        result.put("traceId", traceId);
        return result;
    }
}
