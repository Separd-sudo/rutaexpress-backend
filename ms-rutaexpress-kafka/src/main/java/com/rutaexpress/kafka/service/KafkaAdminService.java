package com.rutaexpress.kafka.service;

import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.*;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.TopicPartitionInfo;
import org.apache.kafka.common.config.ConfigResource;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

/**
 * Operaciones de administracion y observabilidad del cluster (AdminClient):
 * brokers, topics (crear / listar / describir / modificar configuracion / aumentar particiones / eliminar),
 * consumer groups (listar / lag / eliminar) y consumer lag.
 */
@Service
@RequiredArgsConstructor
public class KafkaAdminService {

    private static final List<String> RELEVANT_CONFIGS = List.of(
            "cleanup.policy", "retention.ms", "retention.bytes", "min.insync.replicas", "segment.ms");

    /** Configuraciones de topic que se pueden modificar desde la API de administracion. */
    private static final List<String> EDITABLE_CONFIGS = List.of(
            "cleanup.policy", "retention.ms", "retention.bytes", "min.insync.replicas", "segment.ms",
            "max.message.bytes", "min.compaction.lag.ms", "delete.retention.ms");

    /** Topics que sostienen el flujo de negocio: no se eliminan por accidente (requieren force=true). */
    private static final Set<String> PROTECTED_TOPICS = Set.of(
            "shipments.events", "audit.timeline",
            "shipments.events.audit.DLT", "shipments.events.report.DLT", "audit.timeline.report.DLT");

    private final AdminClient admin;

    public Map<String, Object> clusterInfo() throws ExecutionException, InterruptedException {
        DescribeClusterResult cluster = admin.describeCluster();
        Node controller = cluster.controller().get();
        List<Map<String, Object>> brokers = cluster.nodes().get().stream()
                .sorted(Comparator.comparingInt(Node::id))
                .map(n -> Map.<String, Object>of("id", n.id(), "host", n.host(), "port", n.port(),
                        "controller", n.id() == controller.id()))
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clusterId", cluster.clusterId().get());
        result.put("controllerId", controller.id());
        result.put("brokerCount", brokers.size());
        result.put("brokers", brokers);
        return result;
    }

    public List<Map<String, Object>> listTopics() throws ExecutionException, InterruptedException {
        Set<String> names = admin.listTopics().names().get();
        Map<String, TopicDescription> descriptions = admin.describeTopics(names).allTopicNames().get();
        return descriptions.values().stream()
                .sorted(Comparator.comparing(TopicDescription::name))
                .map(d -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", d.name());
                    m.put("partitions", d.partitions().size());
                    m.put("replicationFactor", d.partitions().isEmpty() ? 0 : d.partitions().get(0).replicas().size());
                    m.put("underReplicatedPartitions", d.partitions().stream()
                            .filter(p -> p.isr().size() < p.replicas().size()).count());
                    return m;
                })
                .toList();
    }

    public Map<String, Object> describeTopic(String topic) throws ExecutionException, InterruptedException {
        TopicDescription d = admin.describeTopics(List.of(topic)).allTopicNames().get().get(topic);

        Map<TopicPartition, OffsetSpec> latestSpec = new HashMap<>();
        Map<TopicPartition, OffsetSpec> earliestSpec = new HashMap<>();
        for (TopicPartitionInfo p : d.partitions()) {
            latestSpec.put(new TopicPartition(topic, p.partition()), OffsetSpec.latest());
            earliestSpec.put(new TopicPartition(topic, p.partition()), OffsetSpec.earliest());
        }
        var latest = admin.listOffsets(latestSpec).all().get();
        var earliest = admin.listOffsets(earliestSpec).all().get();

        List<Map<String, Object>> partitions = new ArrayList<>();
        long totalMessages = 0;
        for (TopicPartitionInfo p : d.partitions()) {
            TopicPartition tp = new TopicPartition(topic, p.partition());
            long end = latest.get(tp).offset();
            long start = earliest.get(tp).offset();
            totalMessages += end - start;
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("partition", p.partition());
            m.put("leader", p.leader() != null ? p.leader().id() : null);
            m.put("replicas", p.replicas().stream().map(Node::id).toList());
            m.put("isr", p.isr().stream().map(Node::id).toList());
            m.put("earliestOffset", start);
            m.put("logEndOffset", end);
            m.put("messages", end - start);
            partitions.add(m);
        }

        ConfigResource res = new ConfigResource(ConfigResource.Type.TOPIC, topic);
        Config config = admin.describeConfigs(List.of(res)).all().get().get(res);
        Map<String, String> configs = new LinkedHashMap<>();
        for (String key : RELEVANT_CONFIGS) {
            ConfigEntry e = config.get(key);
            if (e != null) configs.put(key, e.value());
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", topic);
        result.put("partitionCount", d.partitions().size());
        result.put("replicationFactor", d.partitions().isEmpty() ? 0 : d.partitions().get(0).replicas().size());
        result.put("retainedMessages", totalMessages);
        result.put("configs", configs);
        result.put("partitions", partitions);
        return result;
    }

    public Map<String, Object> createTopic(String name, int partitions, short replicas, Map<String, String> configs)
            throws ExecutionException, InterruptedException {
        NewTopic topic = new NewTopic(name, partitions, replicas);
        if (configs != null && !configs.isEmpty()) topic.configs(configs);
        admin.createTopics(List.of(topic)).all().get();
        return describeTopic(name);
    }

    // ---------------- Mantenimiento de topics (modificar / eliminar) ----------------

    /**
     * Modifica la configuracion de un topic sin recrearlo (incrementalAlterConfigs):
     *  - set:   claves a establecer, ej. {"retention.ms": "86400000", "cleanup.policy": "delete"}
     *  - reset: claves a devolver al valor por defecto del broker, ej. ["retention.ms"]
     * Solo se aceptan claves conocidas para evitar errores de tipeo que el broker rechazaria.
     */
    public Map<String, Object> updateTopicConfigs(String topic, Map<String, String> set, List<String> reset)
            throws ExecutionException, InterruptedException {
        requireExistingTopic(topic);
        List<AlterConfigOp> ops = new ArrayList<>();
        if (set != null) {
            set.forEach((key, value) -> {
                validateConfigKey(key);
                ops.add(new AlterConfigOp(new ConfigEntry(key, value), AlterConfigOp.OpType.SET));
            });
        }
        if (reset != null) {
            reset.forEach(key -> {
                validateConfigKey(key);
                ops.add(new AlterConfigOp(new ConfigEntry(key, null), AlterConfigOp.OpType.DELETE));
            });
        }
        if (ops.isEmpty()) {
            throw new IllegalArgumentException("Debes indicar al menos una clave en 'set' o 'reset'");
        }
        ConfigResource res = new ConfigResource(ConfigResource.Type.TOPIC, topic);
        admin.incrementalAlterConfigs(Map.of(res, ops)).all().get();
        return describeTopic(topic);
    }

    /**
     * Aumenta el numero de particiones de un topic. Kafka no permite disminuirlas,
     * porque eso implicaria perder mensajes o romper el orden por key.
     */
    public Map<String, Object> increasePartitions(String topic, int totalCount)
            throws ExecutionException, InterruptedException {
        TopicDescription d = requireExistingTopic(topic);
        int current = d.partitions().size();
        if (totalCount <= current) {
            throw new IllegalArgumentException("El topic " + topic + " ya tiene " + current
                    + " particiones; solo se puede aumentar (Kafka no permite reducirlas)");
        }
        admin.createPartitions(Map.of(topic, NewPartitions.increaseTo(totalCount))).all().get();
        return describeTopic(topic);
    }

    /**
     * Elimina un topic. Por seguridad:
     *  - los topics internos de Kafka (prefijo "__") nunca se pueden eliminar;
     *  - los topics base del sistema solo se eliminan con force=true.
     */
    public Map<String, Object> deleteTopic(String topic, boolean force)
            throws ExecutionException, InterruptedException {
        if (topic.startsWith("__")) {
            throw new IllegalArgumentException("El topic " + topic + " es interno de Kafka y no se puede eliminar");
        }
        if (PROTECTED_TOPICS.contains(topic) && !force) {
            throw new IllegalArgumentException("El topic " + topic
                    + " es parte del flujo de RutaExpress; usa force=true si realmente quieres eliminarlo");
        }
        requireExistingTopic(topic);
        admin.deleteTopics(List.of(topic)).all().get();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topic", topic);
        result.put("deleted", true);
        return result;
    }

    /**
     * Elimina un consumer group (sus offsets guardados). Kafka solo lo permite si el grupo
     * no tiene consumidores conectados (estado EMPTY o DEAD).
     */
    public Map<String, Object> deleteConsumerGroup(String groupId) throws ExecutionException, InterruptedException {
        ConsumerGroupDescription g = admin.describeConsumerGroups(List.of(groupId)).all().get().get(groupId);
        if (!g.members().isEmpty()) {
            throw new IllegalArgumentException("El grupo " + groupId + " tiene " + g.members().size()
                    + " consumidores activos; detenlos antes de eliminarlo");
        }
        admin.deleteConsumerGroups(List.of(groupId)).all().get();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("groupId", groupId);
        result.put("deleted", true);
        return result;
    }

    private TopicDescription requireExistingTopic(String topic) throws ExecutionException, InterruptedException {
        if (!admin.listTopics().names().get().contains(topic)) {
            throw new IllegalArgumentException("El topic " + topic + " no existe");
        }
        return admin.describeTopics(List.of(topic)).allTopicNames().get().get(topic);
    }

    private static void validateConfigKey(String key) {
        if (!EDITABLE_CONFIGS.contains(key)) {
            throw new IllegalArgumentException("Configuracion '" + key + "' no permitida. Claves validas: " + EDITABLE_CONFIGS);
        }
    }

    // ---------------- Consumer groups ----------------

    public List<Map<String, Object>> listConsumerGroups() throws ExecutionException, InterruptedException {
        List<String> ids = admin.listConsumerGroups().all().get().stream()
                .map(ConsumerGroupListing::groupId).sorted().toList();
        Map<String, ConsumerGroupDescription> desc = admin.describeConsumerGroups(ids).all().get();
        List<Map<String, Object>> list = new ArrayList<>();
        for (String id : ids) {
            ConsumerGroupDescription g = desc.get(id);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("groupId", id);
            m.put("state", g.state().toString());
            m.put("members", g.members().size());
            m.put("totalLag", totalLag(lagByPartition(id)));
            list.add(m);
        }
        return list;
    }

    /** Lag = LogEndOffset - CurrentOffset (guia 3.3.1), por particion y total. */
    public Map<String, Object> consumerGroupLag(String groupId) throws ExecutionException, InterruptedException {
        ConsumerGroupDescription g = admin.describeConsumerGroups(List.of(groupId)).all().get().get(groupId);
        Map<TopicPartition, String> owner = new HashMap<>();
        for (MemberDescription member : g.members()) {
            for (TopicPartition tp : member.assignment().topicPartitions()) {
                owner.put(tp, member.clientId() + " (" + member.host() + ")");
            }
        }

        List<Map<String, Object>> partitions = lagByPartition(groupId);
        partitions.forEach(p -> p.put("consumer",
                owner.getOrDefault(new TopicPartition((String) p.get("topic"), (Integer) p.get("partition")), "-")));

        long total = totalLag(partitions);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("groupId", groupId);
        result.put("state", g.state().toString());
        result.put("members", g.members().size());
        result.put("totalLag", total);
        result.put("health", total == 0 ? "SALUDABLE (tiempo real)" : total < 1000 ? "LAG MODERADO" : "LAG ALTO");
        result.put("partitions", partitions);
        return result;
    }

    private List<Map<String, Object>> lagByPartition(String groupId) throws ExecutionException, InterruptedException {
        Map<TopicPartition, OffsetAndMetadata> committed =
                admin.listConsumerGroupOffsets(groupId).partitionsToOffsetAndMetadata().get();
        Map<TopicPartition, OffsetSpec> spec = committed.keySet().stream()
                .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));
        var ends = admin.listOffsets(spec).all().get();

        List<Map<String, Object>> list = new ArrayList<>();
        committed.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<TopicPartition, OffsetAndMetadata> e) -> e.getKey().topic())
                        .thenComparingInt(e -> e.getKey().partition()))
                .forEach(e -> {
                    long current = e.getValue() != null ? e.getValue().offset() : 0;
                    long end = ends.get(e.getKey()).offset();
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("topic", e.getKey().topic());
                    m.put("partition", e.getKey().partition());
                    m.put("currentOffset", current);
                    m.put("logEndOffset", end);
                    m.put("lag", Math.max(0, end - current));
                    list.add(m);
                });
        return list;
    }

    private static long totalLag(List<Map<String, Object>> partitions) {
        return partitions.stream().mapToLong(p -> (Long) p.get("lag")).sum();
    }
}
