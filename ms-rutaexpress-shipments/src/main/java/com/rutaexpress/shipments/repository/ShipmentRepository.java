package com.rutaexpress.shipments.repository;

import com.rutaexpress.shipments.entity.Shipment;
import com.rutaexpress.shipments.entity.ShipmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ShipmentRepository extends JpaRepository<Shipment, Long> {
    Optional<Shipment> findByTrackingNumber(String trackingNumber);

    @Query("SELECT s FROM Shipment s WHERE " +
           "(:status IS NULL OR s.status = :status) AND " +
           "(:from IS NULL OR s.createdAt >= :from) AND " +
           "(:to IS NULL OR s.createdAt <= :to) " +
           "ORDER BY s.createdAt DESC")
    List<Shipment> filterShipments(
            @Param("status") ShipmentStatus status,
            @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to
    );
}