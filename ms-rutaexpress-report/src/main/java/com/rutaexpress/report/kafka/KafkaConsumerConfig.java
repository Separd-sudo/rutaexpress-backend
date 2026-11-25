package com.rutaexpress.report.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.KafkaListenerConfigurer;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistrar;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Consumidor Kafka de ms-rutaexpress-report (guia 3.3.3 - manejo de errores en streaming):
 *  - ErrorHandlingDeserializer: un JSON corrupto no deja al consumidor en bucle infinito.
 *  - Bean Validation (@Valid): payload invalido = error permanente, sin reintentos.
 *  - DefaultErrorHandler + backoff exponencial (1s, 2s, 4s): errores transitorios.
 *  - DeadLetterPublishingRecoverer: agotados los reintentos va a shipments.events.report.DLT
 *    con headers de excepcion (kafka_dlt-exception-message, stacktrace, topic/offset original).
 */
@Configuration
@EnableKafka
@Slf4j
public class KafkaConsumerConfig implements KafkaListenerConfigurer {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${kafka.consumer.group-id:report-svc}")
    private String groupId;

    @Value("${kafka.consumer.concurrency:3}")
    private int concurrency;

    @Value("${kafka.topics.dlt-suffix:.report.DLT}")
    private String dltSuffix;

    @Value("${kafka.retry.max-attempts:3}")
    private int maxRetries;

    private final LocalValidatorFactoryBean validator;

    public KafkaConsumerConfig(LocalValidatorFactoryBean validator) {
        this.validator = validator;
    }

    @Override
    public void configureKafkaListeners(KafkaListenerEndpointRegistrar registrar) {
        registrar.setValidator(validator);
    }

    @Bean
    public ConsumerFactory<String, ShipmentKafkaEvent> shipmentEventConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, ShipmentKafkaEvent.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /** Template que publica a DLT (bytes crudos si no se pudo deserializar, JSON si no) (DLT del consumidor report-svc). */
    @Bean
    public KafkaTemplate<Object, Object> reportKafkaTemplate() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 5000);

        Map<Class<?>, Serializer<?>> delegates = new LinkedHashMap<>();
        delegates.put(byte[].class, new ByteArraySerializer());
        delegates.put(String.class, new StringSerializer());
        JsonSerializer<Object> json = new JsonSerializer<>();
        json.setAddTypeInfo(false);
        delegates.put(Object.class, json);

        ProducerFactory<Object, Object> pf = new DefaultKafkaProducerFactory<>(props,
                new DelegatingByTypeSerializer(Map.of(Object.class, new StringSerializer()), true),
                new DelegatingByTypeSerializer(delegates, true));
        return new KafkaTemplate<>(pf);
    }

    @Bean
    public DefaultErrorHandler reportErrorHandler(KafkaTemplate<Object, Object> reportKafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(reportKafkaTemplate,
                (record, ex) -> {
                    log.error("[Kafka DLT] Fallo definitivo topic={} partition={} offset={} key={} -> {}{} | causa: {}",
                            record.topic(), record.partition(), record.offset(), record.key(),
                            record.topic(), dltSuffix, rootMessage(ex));
                    return new TopicPartition(record.topic() + dltSuffix, record.partition());
                });

        // 3 reintentos con espera exponencial: 1s, 2s, 4s (4 entregas en total)
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(maxRetries);
        backOff.setInitialInterval(1000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(8000L);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(InvalidReportEventException.class, IllegalArgumentException.class);
        handler.setRetryListeners((record, ex, attempt) ->
                log.warn("[Kafka Retry] Fallo al procesar key={} offset={} (entrega {} de {}): {}",
                        record.key(), record.offset(), attempt, maxRetries + 1, rootMessage(ex)));
        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, ShipmentKafkaEvent> shipmentEventListenerFactory(
            ConsumerFactory<String, ShipmentKafkaEvent> shipmentEventConsumerFactory,
            DefaultErrorHandler reportErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, ShipmentKafkaEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(shipmentEventConsumerFactory);
        factory.setConcurrency(concurrency); // 1 hilo por particion (3 particiones)
        factory.setCommonErrorHandler(reportErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.getContainerProperties().setMissingTopicsFatal(false);
        return factory;
    }

    // ------------------------- Consumidor de audit.timeline -------------------------

    /**
     * Mismas garantias que el consumidor de shipments.events (deserializacion tolerante a errores,
     * commit manual por registro, lectura desde el inicio si el grupo es nuevo), pero tipado con
     * AuditTimelineEvent y con su propio consumer group.
     */
    @Bean
    public ConsumerFactory<String, AuditTimelineEvent> auditTimelineConsumerFactory(
            @Value("${kafka.consumer.timeline-group-id:report-timeline-svc}") String timelineGroupId) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, timelineGroupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS, JsonDeserializer.class);
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, AuditTimelineEvent.class.getName());
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Reutiliza el mismo DefaultErrorHandler: 3 reintentos con backoff exponencial y luego
     * el mensaje se envia a audit.timeline.report.DLT (topic original + sufijo .report.DLT).
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, AuditTimelineEvent> auditTimelineListenerFactory(
            ConsumerFactory<String, AuditTimelineEvent> auditTimelineConsumerFactory,
            DefaultErrorHandler reportErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, AuditTimelineEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(auditTimelineConsumerFactory);
        factory.setConcurrency(concurrency); // 1 hilo por particion (3 particiones)
        factory.setCommonErrorHandler(reportErrorHandler);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.getContainerProperties().setMissingTopicsFatal(false);
        return factory;
    }

    private static String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getClass().getSimpleName() + ": " + t.getMessage();
    }
}
