package com.rutaexpress.catalog.service;

import com.rutaexpress.catalog.dto.CapacityChangeResponse;
import com.rutaexpress.catalog.dto.ServiceOfferingRequest;
import com.rutaexpress.catalog.dto.ServiceOfferingResponse;
import com.rutaexpress.catalog.dto.UpdateTariffCapacityRequest;
import com.rutaexpress.catalog.entity.ServiceOffering;
import com.rutaexpress.catalog.exception.InsufficientCapacityException;
import com.rutaexpress.catalog.exception.ResourceNotFoundException;
import com.rutaexpress.catalog.repository.ServiceOfferingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogService {

    private final ServiceOfferingRepository repository;

    @Transactional(readOnly = true)
    public List<ServiceOfferingResponse> getAllServices(Boolean onlyActive) {
        List<ServiceOffering> list = (onlyActive != null && onlyActive)
                ? repository.findByActiveTrue()
                : repository.findAll();
        return list.stream().map(this::mapToResponse).toList();
    }

    @Transactional(readOnly = true)
    public ServiceOfferingResponse getServiceById(Long id) {
        ServiceOffering service = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con ID: " + id));
        return mapToResponse(service);
    }

    @Transactional(readOnly = true)
    public ServiceOfferingResponse getServiceByCode(String code) {
        ServiceOffering service = repository.findByCode(code)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con código: " + code));
        return mapToResponse(service);
    }

    @Transactional
    public ServiceOfferingResponse createService(ServiceOfferingRequest request) {
        if (repository.existsByCode(request.getCode())) {
            throw new IllegalArgumentException("Ya existe un servicio con el código: " + request.getCode());
        }

        ServiceOffering service = ServiceOffering.builder()
                .code(request.getCode().toUpperCase().trim())
                .name(request.getName().trim())
                .description(request.getDescription())
                .basePrice(request.getBasePrice())
                .pricePerKm(request.getPricePerKm())
                .maxDailyCapacity(request.getMaxDailyCapacity())
                .availableCapacity(request.getMaxDailyCapacity())
                .active(request.getActive() != null ? request.getActive() : true)
                .build();

        ServiceOffering saved = repository.save(service);
        log.info("Servicio de catálogo creado exitosamente con ID: {}", saved.getId());
        return mapToResponse(saved);
    }

    @Transactional
    public ServiceOfferingResponse updateService(Long id, UpdateTariffCapacityRequest request) {
        ServiceOffering service = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con ID: " + id));

        if (request.getBasePrice() != null) {
            service.setBasePrice(request.getBasePrice());
        }
        if (request.getPricePerKm() != null) {
            service.setPricePerKm(request.getPricePerKm());
        }
        if (request.getMaxDailyCapacity() != null) {
            service.setMaxDailyCapacity(request.getMaxDailyCapacity());
        }
        if (request.getAvailableCapacity() != null) {
            if (request.getAvailableCapacity() > service.getMaxDailyCapacity()) {
                throw new IllegalArgumentException("La capacidad disponible no puede superar la capacidad máxima diaria (" + service.getMaxDailyCapacity() + ")");
            }
            service.setAvailableCapacity(request.getAvailableCapacity());
        }
        if (request.getActive() != null) {
            service.setActive(request.getActive());
        }

        ServiceOffering updated = repository.save(service);
        log.info("Servicio con ID {} actualizado", id);
        return mapToResponse(updated);
    }

    @Transactional
    public CapacityChangeResponse reserveCapacity(Long serviceId, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("La cantidad a reservar debe ser mayor a 0");
        }

        ServiceOffering service = repository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con ID: " + serviceId));

        if (!service.getActive()) {
            throw new IllegalStateException("El servicio '" + service.getName() + "' se encuentra inactivo");
        }

        if (service.getAvailableCapacity() < amount) {
            throw new InsufficientCapacityException(String.format(
                    "Capacidad de flota insuficiente para el servicio %s. Disponible: %d, Solicitada: %d",
                    service.getName(), service.getAvailableCapacity(), amount));
        }

        int previous = service.getAvailableCapacity();
        service.setAvailableCapacity(previous - amount);
        repository.save(service);

        log.info("Capacidad reservada para servicio ID {}: {} -> {}", serviceId, previous, service.getAvailableCapacity());

        return CapacityChangeResponse.builder()
                .serviceId(service.getId())
                .serviceCode(service.getCode())
                .previousAvailableCapacity(previous)
                .newAvailableCapacity(service.getAvailableCapacity())
                .maxDailyCapacity(service.getMaxDailyCapacity())
                .message("Capacidad reservada exitosamente")
                .build();
    }

    @Transactional
    public CapacityChangeResponse releaseCapacity(Long serviceId, int amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("La cantidad a liberar debe ser mayor a 0");
        }

        ServiceOffering service = repository.findById(serviceId)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con ID: " + serviceId));

        int previous = service.getAvailableCapacity();
        int newCap = Math.min(service.getMaxDailyCapacity(), previous + amount);
        service.setAvailableCapacity(newCap);
        repository.save(service);

        log.info("Capacidad liberada para servicio ID {}: {} -> {}", serviceId, previous, newCap);

        return CapacityChangeResponse.builder()
                .serviceId(service.getId())
                .serviceCode(service.getCode())
                .previousAvailableCapacity(previous)
                .newAvailableCapacity(newCap)
                .maxDailyCapacity(service.getMaxDailyCapacity())
                .message("Capacidad liberada exitosamente")
                .build();
    }

    @Transactional
    public void deleteService(Long id) {
        ServiceOffering service = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Servicio no encontrado con ID: " + id));

        service.setActive(false);
        repository.save(service);
        log.info("Servicio ID {} dado de baja", id);
    }

    private ServiceOfferingResponse mapToResponse(ServiceOffering entity) {
        return ServiceOfferingResponse.builder()
                .id(entity.getId())
                .code(entity.getCode())
                .name(entity.getName())
                .description(entity.getDescription())
                .basePrice(entity.getBasePrice())
                .pricePerKm(entity.getPricePerKm())
                .maxDailyCapacity(entity.getMaxDailyCapacity())
                .availableCapacity(entity.getAvailableCapacity())
                .active(entity.getActive())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
