package com.rutaexpress.report.service;

import com.rutaexpress.report.entity.ShipmentEventFact;
import com.rutaexpress.report.entity.ShipmentSnapshot;
import com.rutaexpress.report.repository.ShipmentEventFactRepository;
import com.rutaexpress.report.repository.ShipmentSnapshotRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** KPIs del panel de operaciones: envios por hora, lead time y estados activos. */
@Service
@RequiredArgsConstructor
public class ReportQueryService {

    private static final Pattern RANGE = Pattern.compile("last(\\d+)([mhd])");
    private static final List<String> FINAL_STATES = List.of("ENTREGADO", "CANCELADO", "ELIMINADO");

    private final ShipmentEventFactRepository factRepository;
    private final ShipmentSnapshotRepository snapshotRepository;

    @Transactional(readOnly = true)
    public Map<String, Object> kpis(String range) {
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minus(parseRange(range));

        // Envios creados por hora (serie completa, con 0 en horas sin actividad)
        TreeMap<LocalDateTime, Long> perHour = new TreeMap<>();
        for (LocalDateTime h = from.truncatedTo(ChronoUnit.HOURS); !h.isAfter(to); h = h.plusHours(1)) {
            perHour.put(h, 0L);
        }
        List<LocalDateTime> created = factRepository.findCreatedTimes(from, to);
        created.forEach(t -> perHour.merge(t.truncatedTo(ChronoUnit.HOURS), 1L, Long::sum));
        List<Map<String, Object>> perHourSeries = new ArrayList<>();
        perHour.forEach((hour, count) -> perHourSeries.add(Map.of("hour", hour.toString(), "count", count)));

        // Lead time: CREADO -> ENTREGADO de los envios entregados en el rango
        List<ShipmentSnapshot> delivered = snapshotRepository.findDeliveredBetween(from, to);
        LongSummaryStatistics lead = delivered.stream()
                .mapToLong(s -> Duration.between(s.getCreatedAt(), s.getDeliveredAt()).toMinutes())
                .summaryStatistics();
        Map<String, Object> leadTime = new LinkedHashMap<>();
        leadTime.put("deliveredCount", lead.getCount());
        leadTime.put("avgMinutes", lead.getCount() > 0 ? Math.round(lead.getAverage() * 100.0) / 100.0 : 0);
        leadTime.put("minMinutes", lead.getCount() > 0 ? lead.getMin() : 0);
        leadTime.put("maxMinutes", lead.getCount() > 0 ? lead.getMax() : 0);

        // Estados activos (foto actual, no depende del rango)
        Map<String, Long> active = new LinkedHashMap<>();
        for (String st : List.of("CREADO", "ACEPTADO", "EN_BODEGA", "EN_RUTA")) active.put(st, 0L);
        for (Object[] row : snapshotRepository.countActiveByStatus(FINAL_STATES)) {
            active.put((String) row[0], (Long) row[1]);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("range", range);
        result.put("from", from.toString());
        result.put("to", to.toString());
        result.put("totalEvents", factRepository.countByEventTimeBetween(from, to));
        result.put("shipmentsCreated", (long) created.size());
        result.put("shipmentsDelivered", factRepository.countByNewStatusAndEventTimeBetween("ENTREGADO", from, to));
        result.put("shipmentsCancelled", factRepository.countByNewStatusAndEventTimeBetween("CANCELADO", from, to));
        result.put("shipmentsPerHour", perHourSeries);
        result.put("leadTime", leadTime);
        result.put("activeByStatus", active);
        result.put("activeTotal", active.values().stream().mapToLong(Long::longValue).sum());
        result.put("source", "kafka:shipments.events");
        result.put("generatedAt", to.toString());
        return result;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> topServices(String range, int limit) {
        LocalDateTime to = LocalDateTime.now();
        LocalDateTime from = to.minus(parseRange(range));
        List<Map<String, Object>> list = new ArrayList<>();
        for (Object[] row : factRepository.topServices(from, to, PageRequest.of(0, Math.max(1, limit)))) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("serviceCode", row[0]);
            item.put("serviceId", row[1]);
            item.put("shipments", row[2]);
            list.add(item);
        }
        return list;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> recentEvents(int limit) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (ShipmentEventFact f : factRepository.findAllByOrderByReceivedAtDesc(PageRequest.of(0, Math.max(1, limit)))) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("eventId", f.getEventId());
            m.put("trackingNumber", f.getTrackingNumber());
            m.put("eventType", f.getEventType());
            m.put("newStatus", f.getNewStatus());
            m.put("serviceCode", f.getServiceCode());
            m.put("eventTime", f.getEventTime().toString());
            m.put("partition", f.getKafkaPartition());
            m.put("offset", f.getKafkaOffset());
            list.add(m);
        }
        return list;
    }

    static Duration parseRange(String range) {
        Matcher m = RANGE.matcher(range == null ? "" : range.trim().toLowerCase());
        if (!m.matches()) {
            throw new IllegalArgumentException("Rango invalido '" + range + "'. Usa lastNm, lastNh o lastNd (ej: last24h, last7d)");
        }
        long n = Long.parseLong(m.group(1));
        return switch (m.group(2)) {
            case "m" -> Duration.ofMinutes(n);
            case "h" -> Duration.ofHours(n);
            default -> Duration.ofDays(n);
        };
    }
}
