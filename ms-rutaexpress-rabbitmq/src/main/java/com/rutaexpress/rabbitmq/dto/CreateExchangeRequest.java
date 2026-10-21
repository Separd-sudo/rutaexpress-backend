package com.rutaexpress.rabbitmq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateExchangeRequest {

    @NotBlank(message = "El nombre del exchange (exchangeName) no puede estar vacío")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "El nombre del exchange solo puede contener letras, números, puntos, guiones y guiones bajos")
    private String exchangeName;

    @Builder.Default
    @Pattern(regexp = "^(direct|topic|fanout|DIRECT|TOPIC|FANOUT)$", message = "El tipo de exchange debe ser direct, topic o fanout")
    private String type = "direct";

    @Builder.Default
    private boolean durable = true;
}
