package com.rutaexpress.audit.repository;

import com.rutaexpress.audit.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
    List<AuditEvent> findByShipmentIdOrderByTimestampAsc(Long shipmentId);

    @Query("SELECT a FROM AuditEvent a WHERE " +
           "(:user IS NULL OR LOWER(a.performedBy) LIKE LOWER(CONCAT('%', :user, '%'))) AND " +
           "(:eventType IS NULL OR a.eventType = :eventType) AND " +
           "(:from IS NULL OR a.timestamp >= :from) AND " +
           "(:to IS NULL OR a.timestamp <= :to) " +
           "ORDER BY a.timestamp DESC")
    List<AuditEvent> filterEvents(
            @Param("user") String user,
            @Param("eventType") String eventType,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}