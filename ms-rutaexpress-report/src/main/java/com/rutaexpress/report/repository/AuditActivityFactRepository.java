package com.rutaexpress.report.repository;

import com.rutaexpress.report.entity.AuditActivityFact;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

/** Consultas de actividad construidas a partir de los eventos de audit.timeline. */
public interface AuditActivityFactRepository extends JpaRepository<AuditActivityFact, Long> {

    /** Control de idempotencia antes de insertar. */
    boolean existsByEventId(String eventId);

    long countByEventTimeBetween(LocalDateTime from, LocalDateTime to);

    /** Ranking de usuarios mas activos en el rango: [who, role, cantidad]. */
    @Query("SELECT a.who, a.role, COUNT(a) FROM AuditActivityFact a " +
           "WHERE a.eventTime BETWEEN :from AND :to " +
           "GROUP BY a.who, a.role ORDER BY COUNT(a) DESC")
    List<Object[]> activityByUser(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);

    /** Cantidad de acciones por tipo en el rango: [action, cantidad]. */
    @Query("SELECT a.action, COUNT(a) FROM AuditActivityFact a " +
           "WHERE a.eventTime BETWEEN :from AND :to " +
           "GROUP BY a.action ORDER BY COUNT(a) DESC")
    List<Object[]> activityByAction(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    List<AuditActivityFact> findAllByOrderByReceivedAtDesc(Pageable pageable);
}
