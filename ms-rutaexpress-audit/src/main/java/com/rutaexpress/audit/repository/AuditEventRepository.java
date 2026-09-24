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

    List<AuditEvent> findAllByOrderByTimestampDesc();

    @Query("SELECT a FROM AuditEvent a WHERE " +
           "(cast(:user as string) IS NULL OR LOWER(a.performedBy) LIKE LOWER(CONCAT('%', :user, '%'))) AND " +
           "(cast(:eventType as string) IS NULL OR a.eventType = :eventType) AND " +
           "(cast(:from as string) IS NULL OR a.timestamp >= :from) AND " +
           "(cast(:to as string) IS NULL OR a.timestamp <= :to) " +
           "ORDER BY a.timestamp DESC")
    List<AuditEvent> filterEvents(
            @Param("user") String user,
            @Param("eventType") String eventType,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}