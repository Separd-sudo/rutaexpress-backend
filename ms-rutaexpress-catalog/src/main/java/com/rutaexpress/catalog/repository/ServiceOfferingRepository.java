package com.rutaexpress.catalog.repository;

import com.rutaexpress.catalog.entity.ServiceOffering;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, Long> {
    Optional<ServiceOffering> findByCode(String code);
    List<ServiceOffering> findByActiveTrue();
    boolean existsByCode(String code);
}
