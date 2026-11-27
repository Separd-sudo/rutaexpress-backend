package com.rutaexpress.report.repository;

import com.rutaexpress.report.entity.ShipmentSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface ShipmentSnapshotRepository extends JpaRepository<ShipmentSnapshot, String> {

    @Query("SELECT s.currentStatus, COUNT(s) FROM ShipmentSnapshot s " +
           "WHERE s.currentStatus NOT IN :finalStates GROUP BY s.currentStatus")
    List<Object[]> countActiveByStatus(@Param("finalStates") Collection<String> finalStates);

    @Query("SELECT s FROM ShipmentSnapshot s WHERE s.deliveredAt BETWEEN :from AND :to AND s.createdAt IS NOT NULL")
    List<ShipmentSnapshot> findDeliveredBetween(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
