package com.rutaexpress.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateTariffCapacityRequest {

    @DecimalMin(value = "0.0", inclusive = false, message = "La tarifa base debe ser mayor a 0")
    private BigDecimal basePrice;

    @DecimalMin(value = "0.0", inclusive = true, message = "El precio por km debe ser mayor o igual a 0")
    private BigDecimal pricePerKm;

    @Min(value = 1, message = "La capacidad diaria debe ser al menos 1")
    private Integer maxDailyCapacity;

    private Integer availableCapacity;

    private Boolean active;
}
