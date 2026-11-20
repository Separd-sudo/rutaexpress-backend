package com.rutaexpress.kafka.service;

import com.rutaexpress.kafka.config.KafkaClusterConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.header.Header;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Inspeccion y reprocesamiento de Dead Letter Topics.
 * Los mensajes llegan con headers que agrega DeadLetterPublishingRecoverer
 * (topic/particion/offset original, clase y mensaje de la excepcion, stacktrace).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeadLetterService {

    private final KafkaClusterConfig clusterConfig;
    private final KafkaTemplate<byte[], byte[]> rawKafkaTemplate;

    public List<Map<String, Object>> readMessages(String dltTopic, int limit) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (ConsumerRecord<byte[], byte[]> r : fetchAll(dltTopic, limit)) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("partition", r.partition());
            m.put("offset", r.offset());
            m.put("timestamp", Instant.ofEpochMilli(r.timestamp()).toString());
            m.put("key", str(r.key()));
            m.put("originalTopic", headerStr(r, KafkaHeaders.DLT_ORIGINAL_TOPIC));
            m.put("originalPartition", headerInt(r, KafkaHeaders.DLT_ORIGINAL_PARTITION));
            m.put("originalOffset", headerLong(r, KafkaHeaders.DLT_ORIGINAL_OFFSET));
            m.put("originalConsumerGroup", headerStr(r, KafkaHeaders.DLT_ORIGINAL_CONSUMER_GROUP));
            m.put("exceptionClass", headerStr(r, KafkaHeaders.DLT_EXCEPTION_FQCN));
            m.put("exceptionCause", headerStr(r, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN));
            m.put("exceptionMessage", headerStr(r, KafkaHeaders.DLT_EXCEPTION_MESSAGE));
            m.put("value", str(r.value()));
            out.add(m);
        }
        return out;
    }

    /**
     * Re-inyecta mensajes de la DLT en su topic original (una vez corregido el bug).
     * Si partition/offset vienen informados se reprocesa solo ese mensaje.
     * Es seguro reenviar: los consumidores son idempotentes por eventId.
     */
    public Map<String, Object> reprocess(String dltTopic, Integer partition, Long offset) {
        int sent = 0;
        List<String> targets = new ArrayList<>();
        for (ConsumerRecord<byte[], byte[]> r : fetchAll(dltTopic, 10_000)) {
            if (partition != null && r.partition() != partition) continue;
            if (offset != null && r.offset() != offset) continue;

            String original = headerStr(r, KafkaHeaders.DLT_ORIGINAL_TOPIC);
            String target = original != null ? original : KafkaClusterConfig.SHIPMENTS_EVENTS;
            ProducerRecord<byte[], byte[]> pr = new ProducerRecord<>(target, r.key(), r.value());
            for (Header h : r.headers()) {
                if (!h.key().startsWith("kafka_dlt")) pr.headers().add(h);
            }
            pr.headers().add("reprocessedFrom", (dltTopic + "@" + r.partition() + ":" + r.offset()).getBytes(StandardCharsets.UTF_8));
            rawKafkaTemplate.send(pr);
            targets.add(target + " <- " + dltTopic + "@" + r.partition() + ":" + r.offset());
            sent++;
        }
        rawKafkaTemplate.flush();
        log.info("[DLT] {} mensajes reinyectados desde {}", sent, dltTopic);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dltTopic", dltTopic);
        result.put("reprocessed", sent);
        result.put("details", targets);
        return result;
    }

    private List<ConsumerRecord<byte[], byte[]>> fetchAll(String topic, int limit) {
        Map<String, Object> props = clusterConfig.rawConsumerProps();
        List<ConsumerRecord<byte[], byte[]>> records = new ArrayList<>();
        try (KafkaConsumer<byte[], byte[]> consumer = new KafkaConsumer<>(props)) {
            List<TopicPartition> tps = consumer.partitionsFor(topic, Duration.ofSeconds(10)).stream()
                    .map(p -> new TopicPartition(topic, p.partition())).toList();
            consumer.assign(tps);
            consumer.seekToBeginning(tps);
            Map<TopicPartition, Long> ends = consumer.endOffsets(tps);

            long deadline = System.currentTimeMillis() + 10_000;
            while (records.size() < limit && System.currentTimeMillis() < deadline
                    && tps.stream().anyMatch(tp -> consumer.position(tp) < ends.get(tp))) {
                for (ConsumerRecord<byte[], byte[]> r : consumer.poll(Duration.ofMillis(500))) {
                    records.add(r);
                    if (records.size() >= limit) break;
                }
            }
        }
        records.sort(Comparator.comparingLong(ConsumerRecord::timestamp));
        return records;
    }

    private static String str(byte[] b) {
        return b == null ? null : new String(b, StandardCharsets.UTF_8);
    }

    private static String headerStr(ConsumerRecord<?, ?> r, String key) {
        Header h = r.headers().lastHeader(key);
        return h == null ? null : str(h.value());
    }

    private static Integer headerInt(ConsumerRecord<?, ?> r, String key) {
        Header h = r.headers().lastHeader(key);
        return h == null || h.value().length != 4 ? null : ByteBuffer.wrap(h.value()).getInt();
    }

    private static Long headerLong(ConsumerRecord<?, ?> r, String key) {
        Header h = r.headers().lastHeader(key);
        return h == null || h.value().length != 8 ? null : ByteBuffer.wrap(h.value()).getLong();
    }
}
