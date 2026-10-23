package com.rutaexpress.rabbitmq.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateQueueRequest {

    @NotBlank(message = "El nombre de la cola (queueName) no puede estar vacío")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "El nombre de la cola solo puede contener letras, números, puntos, guiones y guiones bajos")
    private String queueName;

    @Builder.Default
    private boolean durable = true;

    @Positive(message = "El TTL del mensaje debe ser un número positivo en milisegundos")
    private Integer messageTtlMs;

    private String deadLetterExchange;

    private String deadLetterRoutingKey;
}
