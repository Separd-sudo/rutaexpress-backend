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
 * Consumidor de Dead Letter Queues (DLQ) para monitoreo y registro en logs de mensajes no entregados
 * (q.cmd.warehouse.dlq y q.cmd.label.dlq).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DeadLetterQueueListener {

    private final RabbitMQEventConsumerService consumerService;

    @Value("${rabbitmq.queues.warehouse-dlq:q.cmd.warehouse.dlq}")
    private String warehouseDlqName;

    @Value("${rabbitmq.queues.label-dlq:q.cmd.label.dlq}")
    private String labelDlqName;

    @RabbitListener(id = "warehouse-dlq-listener", queues = "${rabbitmq.queues.warehouse-dlq:q.cmd.warehouse.dlq}")
    public void consumeWarehouseDlq(
            EventEnvelope envelope,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.error("[DLQ CUARENTENA - {}] Mensaje fallido recibido para análisis: eventId={}, tracking={}",
                warehouseDlqName,
                envelope != null ? envelope.getEventId() : "N/A",
                envelope != null && envelope.getPayload() != null ? envelope.getPayload().getTrackingNumber() : "N/A");

        consumerService.recordDlqEvent(warehouseDlqName, envelope, "Fallo en procesamiento de ticket de bodega");
        channel.basicAck(deliveryTag, false);
    }

    @RabbitListener(id = "label-dlq-listener", queues = "${rabbitmq.queues.label-dlq:q.cmd.label.dlq}")
    public void consumeLabelDlq(
            EventEnvelope envelope,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        log.error("[DLQ CUARENTENA - {}] Mensaje fallido recibido para análisis: eventId={}, tracking={}",
                labelDlqName,
                envelope != null ? envelope.getEventId() : "N/A",
                envelope != null && envelope.getPayload() != null ? envelope.getPayload().getTrackingNumber() : "N/A");

        consumerService.recordDlqEvent(labelDlqName, envelope, "Fallo en generación de guía/etiqueta de despacho");
        channel.basicAck(deliveryTag, false);
    }
}
