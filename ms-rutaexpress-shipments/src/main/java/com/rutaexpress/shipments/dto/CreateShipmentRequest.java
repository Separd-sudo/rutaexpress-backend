package com.rutaexpress.shipments.dto;

import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateShipmentRequest {

    @NotNull(message = "El ID del servicio es obligatorio")
    private Long serviceId;

    @NotBlank(message = "El nombre del remitente es obligatorio")
    @Size(max = 120)
    private String senderName;

    @NotBlank(message = "La dirección del remitente es obligatoria")
    @Size(max = 255)
    private String senderAddress;

    @Size(max = 30)
    private String senderPhone;

    @NotBlank(message = "El nombre del destinatario es obligatorio")
    @Size(max = 120)
    private String recipientName;

    @NotBlank(message = "La dirección del destinatario es obligatoria")
    @Size(max = 255)
    private String recipientAddress;

    @Email(message = "El formato de email de destinatario es inválido")
    private String recipientEmail;

    @Size(max = 30)
    private String recipientPhone;

    @NotNull(message = "El peso en Kg es obligatorio")
    @Positive(message = "El peso debe ser positivo")
    private Double weightKg;

    @Positive(message = "La distancia en Km debe ser positiva")
    private Double distanceKm;

    @PositiveOrZero(message = "El valor declarado debe ser mayor o igual a 0")
    private BigDecimal declaredValue;

    private String createdBy;
    private String notes;
}