package com.rutaexpress.catalog.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ServiceOfferingRequest {

    @NotBlank(message = "El código del servicio es obligatorio")
    @Size(max = 50, message = "El código no debe superar 50 caracteres")
    private String code;

    @NotBlank(message = "El nombre del servicio es obligatorio")
    @Size(max = 100, message = "El nombre no debe superar 100 caracteres")
    private String name;

    @Size(max = 500, message = "La descripción no debe superar 500 caracteres")
    private String description;

    @NotNull(message = "La tarifa base es obligatoria")
    @DecimalMin(value = "0.0", inclusive = false, message = "La tarifa base debe ser mayor a 0")
    private BigDecimal basePrice;

    @NotNull(message = "El precio por km es obligatorio")
    @DecimalMin(value = "0.0", inclusive = true, message = "El precio por km debe ser mayor o igual a 0")
    private BigDecimal pricePerKm;

    @NotNull(message = "La capacidad diaria máxima es obligatoria")
    @Min(value = 1, message = "La capacidad diaria debe ser al menos 1")
    private Integer maxDailyCapacity;

    private Boolean active;
}
