package com.rutaexpress.rabbitmq.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración centralizada de la topología RabbitMQ para RutaExpress (EP3).
 * Declara los Exchanges (cmd.direct, cmd.topic, cmd.dead.dlx),
 * las colas principales (q.cmd.warehouse, q.cmd.label),
 * las colas Dead Letter Queue (q.cmd.warehouse.dlq, q.cmd.label.dlq)
 * y todos sus Bindings respectivos.
 */
@Configuration
public class RabbitMQConfig {

    @Value("${rabbitmq.exchanges.direct:cmd.direct}")
    private String directExchangeName;

    @Value("${rabbitmq.exchanges.topic:cmd.topic}")
    private String topicExchangeName;

    @Value("${rabbitmq.exchanges.dlx:cmd.dead.dlx}")
    private String dlxExchangeName;

    @Value("${rabbitmq.queues.warehouse:q.cmd.warehouse}")
    private String warehouseQueueName;

    @Value("${rabbitmq.queues.warehouse-dlq:q.cmd.warehouse.dlq}")
    private String warehouseDlqName;

    @Value("${rabbitmq.queues.label:q.cmd.label}")
    private String labelQueueName;

    @Value("${rabbitmq.queues.label-dlq:q.cmd.label.dlq}")
    private String labelDlqName;

    @Value("${rabbitmq.routing-keys.warehouse-direct:warehouse.ticket}")
    private String warehouseDirectRk;

    @Value("${rabbitmq.routing-keys.warehouse-topic:warehouse.#}")
    private String warehouseTopicRk;

    @Value("${rabbitmq.routing-keys.warehouse-dlq:warehouse.dead}")
    private String warehouseDlqRk;

    @Value("${rabbitmq.routing-keys.label-direct:label.gen}")
    private String labelDirectRk;

    @Value("${rabbitmq.routing-keys.label-topic:label.*}")
    private String labelTopicRk;

    @Value("${rabbitmq.routing-keys.label-dlq:label.dead}")
    private String labelDlqRk;

    @Value("${rabbitmq.ttl.main-ms:30000}")
    private Integer mainQueueTtl;

    @Value("${rabbitmq.ttl.dlq-ms:86400000}")
    private Integer dlqTtl;

    // =========================================================================
    // 1. EXCHANGES (cmd.direct, cmd.topic, cmd.dead.dlx)
    // =========================================================================

    @Bean
    public DirectExchange cmdDirectExchange() {
        return new DirectExchange(directExchangeName, true, false);
    }

    @Bean
    public TopicExchange cmdTopicExchange() {
        return new TopicExchange(topicExchangeName, true, false);
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(dlxExchangeName, true, false);
    }

    // =========================================================================
    // 2. CASO DE USO 1: TICKET DE PICKING BODEGA (q.cmd.warehouse + DLQ)
    // =========================================================================

    @Bean
    public Queue warehouseQueue() {
        return QueueBuilder.durable(warehouseQueueName)
                .withArgument("x-message-ttl", mainQueueTtl)
                .withArgument("x-dead-letter-exchange", dlxExchangeName)
                .withArgument("x-dead-letter-routing-key", warehouseDlqRk)
                .build();
    }

    @Bean
    public Queue warehouseDlq() {
        return QueueBuilder.durable(warehouseDlqName)
                .withArgument("x-message-ttl", dlqTtl)
                .build();
    }

    @Bean
    public Binding warehouseDirectBinding(Queue warehouseQueue, DirectExchange cmdDirectExchange) {
        return BindingBuilder.bind(warehouseQueue).to(cmdDirectExchange).with(warehouseDirectRk);
    }

    @Bean
    public Binding warehouseTopicBinding(Queue warehouseQueue, TopicExchange cmdTopicExchange) {
        return BindingBuilder.bind(warehouseQueue).to(cmdTopicExchange).with(warehouseTopicRk);
    }

    @Bean
    public Binding warehouseDlqBinding(Queue warehouseDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(warehouseDlq).to(deadLetterExchange).with(warehouseDlqRk);
    }

    // =========================================================================
    // 3. CASO DE USO 2: GENERACIÓN DE ETIQUETA / GUÍA PDF (q.cmd.label + DLQ)
    // =========================================================================

    @Bean
    public Queue labelQueue() {
        return QueueBuilder.durable(labelQueueName)
                .withArgument("x-message-ttl", mainQueueTtl)
                .withArgument("x-dead-letter-exchange", dlxExchangeName)
                .withArgument("x-dead-letter-routing-key", labelDlqRk)
                .build();
    }

    @Bean
    public Queue labelDlq() {
        return QueueBuilder.durable(labelDlqName)
                .withArgument("x-message-ttl", dlqTtl)
                .build();
    }

    @Bean
    public Binding labelDirectBinding(Queue labelQueue, DirectExchange cmdDirectExchange) {
        return BindingBuilder.bind(labelQueue).to(cmdDirectExchange).with(labelDirectRk);
    }

    @Bean
    public Binding labelTopicBinding(Queue labelQueue, TopicExchange cmdTopicExchange) {
        return BindingBuilder.bind(labelQueue).to(cmdTopicExchange).with(labelTopicRk);
    }

    @Bean
    public Binding labelDlqBinding(Queue labelDlq, DirectExchange deadLetterExchange) {
        return BindingBuilder.bind(labelDlq).to(deadLetterExchange).with(labelDlqRk);
    }

    // =========================================================================
    // 4. BEANS INFRAESTRUCTURA (RabbitAdmin, MessageConverter, RabbitTemplate)
    // =========================================================================

    @Bean
    public RabbitAdmin rabbitAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(jsonMessageConverter);
        return template;
    }
}
