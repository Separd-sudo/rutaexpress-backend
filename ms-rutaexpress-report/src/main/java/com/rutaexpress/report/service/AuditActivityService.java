package com.rutaexpress.report.service;

import com.rutaexpress.report.entity.AuditActivityFact;
import com.rutaexpress.report.kafka.AuditTimelineEvent;
import com.rutaexpress.report.repository.AuditActivityFactRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Logica de negocio de la actividad de usuarios (separada del listener de Kafka):
 *  - ingest(): guarda cada evento de audit.timeline una sola vez (idempotente).
 *  - activity(): KPIs de operacion por usuario y por tipo de accion en un rango de tiempo.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditActivityService {

    private static final String UNKNOWN_USER = "desconocido";

    private final AuditActivityFactRepository activityRepository;

    @Transactional
    public void ingest(AuditTimelineEvent event, int partition, long offset) {
        // Idempotencia: Kafka garantiza "al menos una vez", por lo que un mismo evento puede llegar repetido
        if (activityRepository.existsByEventId(event.getEventId())) {
            log.info("[Idempotencia] audit.timeline eventId={} ya contabilizado, se ignora", event.getEventId());
            return;
        }

        activityRepository.save(AuditActivityFact.builder()
                .eventId(event.getEventId())
                .trackingNumber(event.getTrackingNumber())
                .who(event.getWho() != null && !event.getWho().isBlank() ? event.getWho() : UNKNOWN_USER)
                .role(event.getRole())
                .action(event.getWhat())
                .newStatus(event.getNewStatus())
                .origin(event.getFrom())
                .eventTime(parse(event.getWhen()))
                .kafkaPartition(partition)
                .kafkaOffset(offset)
                .receivedAt(LocalDateTime.now())
                .build());

        log.info("[Kafka -> Report] actividad {} por {} sobre {} registrada (partition={}, offset={})",
                event.getWhat(), event.getWho(), event.getTrackingNumber(), partition, offset);
    }

    /** KPIs de actividad: total de acciones, ranking de usuarios, acciones por tipo y ultimas acciones. */
    @Transactional(readOnly = true)
    public Map<String, Object> activity(String range, int limit) {
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minus(ReportQueryService.parseRange(range));
        int size = Math.max(1, limit);

        List<Map<String, Object>> byUser = new ArrayList<>();
        for (Object[] row : activityRepository.activityByUser(from, to, PageRequest.of(0, size))) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("who", row[0]);
            item.put("role", row[1]);
            item.put("actions", row[2]);
            byUser.add(item);
        }

        Map<String, Object> byAction = new LinkedHashMap<>();
        for (Object[] row : activityRepository.activityByAction(from, to)) {
            byAction.put((String) row[0], row[1]);
        }

        List<Map<String, Object>> recent = new ArrayList<>();
        for (AuditActivityFact a : activityRepository.findAllByOrderByReceivedAtDesc(PageRequest.of(0, size))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("eventId", a.getEventId());
            m.put("who", a.getWho());
            m.put("role", a.getRole());
            m.put("action", a.getAction());
            m.put("trackingNumber", a.getTrackingNumber());
            m.put("newStatus", a.getNewStatus());
            m.put("from", a.getOrigin());
            m.put("eventTime", a.getEventTime().toString());
            m.put("partition", a.getKafkaPartition());
            m.put("offset", a.getKafkaOffset());
            recent.add(m);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("range", range);
        result.put("from", from.toString());
        result.put("to", to.toString());
        result.put("totalActions", activityRepository.countByEventTimeBetween(from, to));
        result.put("byUser", byUser);
        result.put("byAction", byAction);
        result.put("recent", recent);
        result.put("source", "kafka:audit.timeline");
        result.put("generatedAt", to.toString());
        return result;
    }

    /**
     * Acepta fechas con zona (2026-11-25T10:00:00-03:00) o sin zona (2026-11-25T10:00:00),
     * que es el formato que publica ms-rutaexpress-audit. Si no se puede leer, usa la hora actual.
     */
    private static LocalDateTime parse(String ts) {
        if (ts == null || ts.isBlank()) return LocalDateTime.now();
        try {
            return OffsetDateTime.parse(ts).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(ts);
            } catch (Exception e) {
                return LocalDateTime.now();
            }
        }
    }
}
