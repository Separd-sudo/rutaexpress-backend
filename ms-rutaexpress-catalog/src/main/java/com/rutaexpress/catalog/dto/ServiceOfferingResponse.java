package com.rutaexpress.catalog.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceOfferingResponse {
    private Long id;
    private String code;
    private String name;
    private String description;
    private BigDecimal basePrice;
    private BigDecimal pricePerKm;
    private Integer maxDailyCapacity;
    private Integer availableCapacity;
    private Boolean active;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
