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
public class CreateBindingRequest {

    @NotBlank(message = "El nombre de la cola (queueName) no puede estar vacío")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "El nombre de la cola contiene caracteres inválidos")
    private String queueName;

    @NotBlank(message = "El nombre del exchange (exchangeName) no puede estar vacío")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "El nombre del exchange contiene caracteres inválidos")
    private String exchangeName;

    @NotBlank(message = "La routingKey no puede estar vacía")
    @Pattern(regexp = "^[a-zA-Z0-9._*#-]+$", message = "La routingKey contiene caracteres inválidos")
    private String routingKey;
}
