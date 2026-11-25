package com.rutaexpress.kafka.config;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuracion del microservicio administrador de Kafka.
 * Declara la topologia del Caso RutaExpress (seccion 9) como NewTopic: KafkaAdmin
 * la crea al arrancar si no existe (idempotente, igual que infra/kafka/create-topics.sh).
 */
@Configuration
@EnableKafka
public class KafkaClusterConfig {

    public static final String SHIPMENTS_EVENTS = "shipments.events";
    public static final String AUDIT_TIMELINE = "audit.timeline";
    public static final String AUDIT_DLT = "shipments.events.audit.DLT";
    public static final String REPORT_DLT = "shipments.events.report.DLT";
    /** DLT del consumidor de audit.timeline en ms-rutaexpress-report. */
    public static final String TIMELINE_REPORT_DLT = "audit.timeline.report.DLT";
    public static final String LOADTEST = "rutaexpress.loadtest";

    private static final long DAY_MS = Duration.ofDays(1).toMillis();

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${kafka.topology.partitions:3}")
    private int partitions;

    @Value("${kafka.topology.replicas:3}")
    private int replicas;

    // ------------------------- Topologia -------------------------

    @Bean
    public KafkaAdmin.NewTopics rutaExpressTopics() {
        return new KafkaAdmin.NewTopics(
                topic(SHIPMENTS_EVENTS, TopicConfig.CLEANUP_POLICY_DELETE, 7 * DAY_MS),
                topic(AUDIT_TIMELINE, TopicConfig.CLEANUP_POLICY_COMPACT + "," + TopicConfig.CLEANUP_POLICY_DELETE, 30 * DAY_MS),
                topic(AUDIT_DLT, TopicConfig.CLEANUP_POLICY_DELETE, 14 * DAY_MS),
                topic(REPORT_DLT, TopicConfig.CLEANUP_POLICY_DELETE, 14 * DAY_MS),
                topic(TIMELINE_REPORT_DLT, TopicConfig.CLEANUP_POLICY_DELETE, 14 * DAY_MS),
                topic(LOADTEST, TopicConfig.CLEANUP_POLICY_DELETE, Duration.ofHours(1).toMillis()));
    }

    private NewTopic topic(String name, String cleanup, long retentionMs) {
        return TopicBuilder.name(name)
                .partitions(partitions)
                .replicas(replicas)
                .config(TopicConfig.CLEANUP_POLICY_CONFIG, cleanup)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(retentionMs))
                .config(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG, replicas > 1 ? "2" : "1")
                .build();
    }

    // ------------------------- AdminClient -------------------------

    @Bean(destroyMethod = "close")
    public AdminClient adminClient() {
        Map<String, Object> props = new HashMap<>();
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 10000);
        props.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 15000);
        return AdminClient.create(props);
    }

    // ------------------------- Productor (tuning guia 3.3.2) -------------------------

    @Bean
    public ProducerFactory<String, String> producerFactory(
            @Value("${kafka.producer.batch-size:32768}") int batchSize,
            @Value("${kafka.producer.linger-ms:20}") int lingerMs,
            @Value("${kafka.producer.compression:lz4}") String compression) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, batchSize);
        props.put(ProducerConfig.LINGER_MS_CONFIG, lingerMs);
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, compression);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 10000);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(ProducerFactory<String, String> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    /** Template que reinyecta los bytes originales de una DLT al topic de origen, sin re-serializar. */
    @Bean
    public KafkaTemplate<byte[], byte[]> rawKafkaTemplate() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.ByteArraySerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.ByteArraySerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 10000);
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
    }

    // ------------------------- Consumidor lento (demo de consumer lag) -------------------------

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setMissingTopicsFatal(false);
        return factory;
    }

    /** Propiedades base para consumidores efimeros (lectura de DLT). */
    public Map<String, Object> rawConsumerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.ByteArrayDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, org.apache.kafka.common.serialization.ByteArrayDeserializer.class);
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        return props;
    }
}
