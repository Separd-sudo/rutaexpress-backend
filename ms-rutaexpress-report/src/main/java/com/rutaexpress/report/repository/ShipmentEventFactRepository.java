package com.rutaexpress.report.repository;

import com.rutaexpress.report.entity.ShipmentEventFact;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ShipmentEventFactRepository extends JpaRepository<ShipmentEventFact, Long> {

    boolean existsByEventId(String eventId);

    long countByEventTimeBetween(LocalDateTime from, LocalDateTime to);

    long countByNewStatusAndEventTimeBetween(String newStatus, LocalDateTime from, LocalDateTime to);

    @Query("SELECT f.eventTime FROM ShipmentEventFact f WHERE f.eventType = 'SHIPMENT_CREATED' " +
           "AND f.eventTime BETWEEN :from AND :to")
    List<LocalDateTime> findCreatedTimes(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("SELECT COALESCE(f.serviceCode, 'SIN_SERVICIO'), f.serviceId, COUNT(f) FROM ShipmentEventFact f " +
           "WHERE f.eventType = 'SHIPMENT_CREATED' AND f.eventTime BETWEEN :from AND :to " +
           "GROUP BY f.serviceCode, f.serviceId ORDER BY COUNT(f) DESC")
    List<Object[]> topServices(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to, Pageable pageable);

    List<ShipmentEventFact> findAllByOrderByReceivedAtDesc(Pageable pageable);
}
