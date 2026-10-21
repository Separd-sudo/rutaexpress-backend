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
 * Consumidor del dominio de Documentos y Etiquetas de Despacho (Cola: q.cmd.label).
 * Procesa generación asíncrona de guías/etiquetas con confirmación manual (ACK/NACK) hacia q.cmd.label.dlq.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ShippingLabelListener {

    private final RabbitMQEventConsumerService consumerService;

    @Value("${rabbitmq.queues.label:q.cmd.label}")
    private String labelQueueName;

    @RabbitListener(id = "shipping-label-listener", queues = "${rabbitmq.queues.label:q.cmd.label}")
    public void consumeShippingLabel(
            EventEnvelope envelope,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {
        try {
            if (envelope == null || envelope.getPayload() == null) {
                throw new IllegalArgumentException("Envelope o payload nulo en generación de etiqueta");
            }

            if (envelope.isSimulateFailure()) {
                throw new RuntimeException("Fallo simulado al generar etiqueta/guía de despacho para demostrar desvío a DLQ");
            }

            if (consumerService.isDuplicateEvent(envelope.getEventId())) {
                log.info("[ShippingLabelListener] Evento duplicado ignorado por idempotencia (eventId={})", envelope.getEventId());
                channel.basicAck(deliveryTag, false);
                return;
            }

            log.info("[ShippingLabelListener] Guía/Etiqueta de despacho generada para tracking={} (shipmentId={})",
                    envelope.getPayload().getTrackingNumber(), envelope.getPayload().getShipmentId());

            consumerService.recordProcessedEvent(labelQueueName, envelope);
            channel.basicAck(deliveryTag, false);

        } catch (Exception ex) {
            log.error("[ShippingLabelListener -> DLX] Error procesando mensaje en '{}': {}. Rechazando con basicNack (requeue=false)",
                    labelQueueName, ex.getMessage());
            channel.basicNack(deliveryTag, false, false);
        }
    }
}
