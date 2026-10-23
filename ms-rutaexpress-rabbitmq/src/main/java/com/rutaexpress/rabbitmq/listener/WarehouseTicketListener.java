package com.rutaexpress.rabbitmq.listener;

import com.rabbitmq.client.Channel;
import com.rutaexpress.rabbitmq.dto.EventEnvelope;
import com.rutaexpress.rabbitmq.service.RabbitMQEventConsumerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Consumidor del dominio de Bodega y Picking (Cola: q.cmd.warehouse).
 * Procesa tickets de preparación de encomiendas con ACK/NACK manual hacia q.cmd.warehouse.dlq.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WarehouseTicketListener {

    private final RabbitMQEventConsumerService consumerService;

    @Value("${rabbitmq.queues.warehouse:q.cmd.warehouse}")
    private String warehouseQueueName;

    @RabbitListener(id = "warehouse-ticket-listener", queues = "${rabbitmq.queues.warehouse:q.cmd.warehouse}")
    public void consumeWarehouseTicket(
            EventEnvelope envelope,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            if (envelope == null || envelope.getPayload() == null) {
                throw new IllegalArgumentException("Envelope o payload nulo en ticket de bodega");
            }

            if (envelope.isSimulateFailure()) {
                throw new RuntimeException("Fallo simulado de impresora de bodega / picking para demostrar desvío a DLQ");
            }

            if (consumerService.isDuplicateEvent(envelope.getEventId())) {
                log.info("[WarehouseTicketListener] Ticket duplicado ignorado por idempotencia (eventId={})", envelope.getEventId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            log.info("[WarehouseTicketListener] Ticket de Picking generado en bodega para tracking={} (estado={})",
                    envelope.getPayload().getTrackingNumber(), envelope.getPayload().getNewStatus());

            consumerService.recordProcessedEvent(warehouseQueueName, envelope);
            channel.basicAck(deliveryTag, false);

        } catch (Exception ex) {
            log.error("[WarehouseTicketListener -> DLX] Error procesando mensaje en '{}': {}. Rechazando con basicNack (requeue=false)",
                    warehouseQueueName, ex.getMessage());
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
