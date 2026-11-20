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
 * brokers, topics (lider / replicas / ISR / retencion), consumer groups y consumer lag.
 */
@Service
@RequiredArgsConstructor
public class KafkaAdminService {

    private static final List<String> RELEVANT_CONFIGS = List.of(
            "cleanup.policy", "retention.ms", "retention.bytes", "min.insync.replicas", "segment.ms");

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
