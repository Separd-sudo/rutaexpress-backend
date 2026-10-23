package com.rutaexpress.rabbitmq;

import com.rutaexpress.rabbitmq.dto.CreateBindingRequest;
import com.rutaexpress.rabbitmq.dto.CreateExchangeRequest;
import com.rutaexpress.rabbitmq.dto.CreateQueueRequest;
import com.rutaexpress.rabbitmq.service.RabbitAdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

class RabbitAdminServiceTest {

    private RabbitAdmin rabbitAdmin;
    private RabbitAdminService rabbitAdminService;

    @BeforeEach
    void setUp() {
        rabbitAdmin = Mockito.mock(RabbitAdmin.class);
        rabbitAdminService = new RabbitAdminService(rabbitAdmin);
    }

    @Test
    @DisplayName("Debe crear cola durable correctamente mediante RabbitAdminService")
    void shouldCreateQueueSuccessfully() {
        CreateQueueRequest request = CreateQueueRequest.builder()
                .queueName("q.cmd.test")
                .durable(true)
                .messageTtlMs(30000)
                .deadLetterExchange("cmd.dead.dlx")
                .deadLetterRoutingKey("test.dead")
                .build();

        Map<String, Object> result = rabbitAdminService.createQueue(request);

        assertEquals("CREATED", result.get("status"));
        assertEquals("q.cmd.test", result.get("queueName"));
        verify(rabbitAdmin).declareQueue(any(Queue.class));
    }

    @Test
    @DisplayName("Debe rechazar la creación de colas con nombre vacío")
    void shouldRejectBlankQueueName() {
        CreateQueueRequest request = CreateQueueRequest.builder()
                .queueName("   ")
                .build();

        assertThrows(IllegalArgumentException.class, () -> rabbitAdminService.createQueue(request));
    }

    @Test
    @DisplayName("Debe crear exchange correctamente mediante RabbitAdminService")
    void shouldCreateExchangeSuccessfully() {
        CreateExchangeRequest request = CreateExchangeRequest.builder()
                .exchangeName("cmd.direct.test")
                .type("direct")
                .durable(true)
                .build();

        Map<String, Object> result = rabbitAdminService.createExchange(request);

        assertEquals("CREATED", result.get("status"));
        verify(rabbitAdmin).declareExchange(any(Exchange.class));
    }

    @Test
    @DisplayName("Debe crear binding correctamente mediante RabbitAdminService")
    void shouldCreateBindingSuccessfully() {
        CreateBindingRequest request = CreateBindingRequest.builder()
                .queueName("q.cmd.test")
                .exchangeName("cmd.direct.test")
                .routingKey("test.key")
                .build();

        Map<String, Object> result = rabbitAdminService.createBinding(request);

        assertEquals("CREATED", result.get("status"));
        verify(rabbitAdmin).declareBinding(any(Binding.class));
    }
}
