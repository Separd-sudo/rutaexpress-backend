package com.rutaexpress.shipments.dto;

import com.rutaexpress.shipments.entity.ShipmentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateStatusRequest {

    @NotNull(message = "El estado destino es obligatorio")
    private ShipmentStatus status;

    @NotBlank(message = "El identificador del operador/usuario es obligatorio")
    private String performedBy;

    private String userRole; // Ej: Despachador, Admin
    private String note;
}