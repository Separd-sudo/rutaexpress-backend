package com.rutaexpress.kafka.controller;

import com.rutaexpress.kafka.listener.LoadTestSlowConsumer;
import com.rutaexpress.kafka.service.DeadLetterService;
import com.rutaexpress.kafka.service.KafkaAdminService;
import com.rutaexpress.kafka.service.StreamingLabService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/kafka")
@RequiredArgsConstructor
@Tag(name = "Kafka Admin", description = "Administracion y observabilidad del cluster Kafka de RutaExpress")
public class KafkaAdminController {

    private final KafkaAdminService adminService;
    private final DeadLetterService deadLetterService;
    private final StreamingLabService labService;
    private final KafkaListenerEndpointRegistry listenerRegistry;
    private final LoadTestSlowConsumer slowConsumer;

    // ---------------- Estado del cluster ----------------

    @GetMapping("/health")
    @Operation(summary = "Salud del microservicio y conectividad con el cluster")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", "ms-rutaexpress-kafka");
        try {
            Map<String, Object> cluster = adminService.clusterInfo();
            body.put("status", "UP");
            body.put("brokers", cluster.get("brokerCount"));
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            body.put("status", "DOWN");
            body.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(body);
        }
    }

    @GetMapping("/cluster")
    @Operation(summary = "Brokers del cluster y controller activo")
    public Map<String, Object> cluster() throws Exception {
        return adminService.clusterInfo();
    }

    @GetMapping("/topics")
    @Operation(summary = "Listar topics con particiones, RF y particiones sub-replicadas")
    public List<Map<String, Object>> topics() throws Exception {
        return adminService.listTopics();
    }

    @GetMapping("/topics/{topic}")
    @Operation(summary = "Detalle de un topic: lider, replicas, ISR, offsets y retencion por particion")
    public Map<String, Object> topic(@PathVariable("topic") String topic) throws Exception {
        return adminService.describeTopic(topic);
    }

    @PostMapping("/topics")
    @Operation(summary = "Crear un topic (body: name, partitions, replicationFactor, configs)")
    public ResponseEntity<Map<String, Object>> createTopic(@RequestBody Map<String, Object> body) throws Exception {
        String name = Objects.requireNonNull((String) body.get("name"), "name es obligatorio");
        int partitions = ((Number) body.getOrDefault("partitions", 3)).intValue();
        short rf = ((Number) body.getOrDefault("replicationFactor", 3)).shortValue();
        @SuppressWarnings("unchecked")
        Map<String, String> configs = (Map<String, String>) body.getOrDefault("configs", Map.of());
        return ResponseEntity.status(HttpStatus.CREATED).body(adminService.createTopic(name, partitions, rf, configs));
    }

    @PutMapping("/topics/{topic}/configs")
    @Operation(summary = "Modificar la configuracion de un topic (body: set {clave: valor}, reset [claves])",
            description = "Ej: {\"set\": {\"retention.ms\": \"86400000\"}, \"reset\": [\"segment.ms\"]}")
    public Map<String, Object> updateTopicConfigs(@PathVariable("topic") String topic,
                                                  @RequestBody Map<String, Object> body) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> rawSet = (Map<String, Object>) body.getOrDefault("set", Map.of());
        // Los valores se convierten a texto: Kafka recibe todas las configuraciones como String
        Map<String, String> set = new LinkedHashMap<>();
        rawSet.forEach((k, v) -> set.put(k, String.valueOf(v)));
        @SuppressWarnings("unchecked")
        List<String> reset = (List<String>) body.getOrDefault("reset", List.of());
        return adminService.updateTopicConfigs(topic, set, reset);
    }

    @PostMapping("/topics/{topic}/partitions")
    @Operation(summary = "Aumentar las particiones de un topic (body: totalCount)",
            description = "Kafka solo permite aumentar particiones, nunca reducirlas")
    public Map<String, Object> increasePartitions(@PathVariable("topic") String topic,
                                                  @RequestBody Map<String, Object> body) throws Exception {
        Number total = Objects.requireNonNull((Number) body.get("totalCount"), "totalCount es obligatorio");
        return adminService.increasePartitions(topic, total.intValue());
    }

    @DeleteMapping("/topics/{topic}")
    @Operation(summary = "Eliminar un topic (los topics del flujo de negocio requieren force=true)")
    public Map<String, Object> deleteTopic(@PathVariable("topic") String topic,
                                           @RequestParam(name = "force", defaultValue = "false") boolean force) throws Exception {
        return adminService.deleteTopic(topic, force);
    }

    @DeleteMapping("/consumer-groups/{groupId}")
    @Operation(summary = "Eliminar un consumer group sin consumidores activos (borra sus offsets guardados)")
    public Map<String, Object> deleteConsumerGroup(@PathVariable("groupId") String groupId) throws Exception {
        return adminService.deleteConsumerGroup(groupId);
    }

    @GetMapping("/consumer-groups")
    @Operation(summary = "Consumer groups con estado, miembros y lag total")
    public List<Map<String, Object>> consumerGroups() throws Exception {
        return adminService.listConsumerGroups();
    }

    @GetMapping("/consumer-groups/{groupId}/lag")
    @Operation(summary = "Consumer lag por particion (LogEndOffset - CurrentOffset)")
    public Map<String, Object> lag(@PathVariable("groupId") String groupId) throws Exception {
        return adminService.consumerGroupLag(groupId);
    }

    // ---------------- Dead Letter Topics ----------------

    @GetMapping("/dlt/{dltTopic}/messages")
    @Operation(summary = "Ver mensajes de una DLT con los headers de error")
    public List<Map<String, Object>> dltMessages(@PathVariable("dltTopic") String dltTopic,
                                                 @RequestParam(name = "limit", defaultValue = "50") int limit) {
        return deadLetterService.readMessages(dltTopic, limit);
    }

    @PostMapping("/dlt/{dltTopic}/reprocess")
    @Operation(summary = "Reinyectar mensajes de la DLT en su topic original (todos o uno por partition+offset)")
    public Map<String, Object> reprocess(@PathVariable("dltTopic") String dltTopic,
                                         @RequestParam(name = "partition", required = false) Integer partition,
                                         @RequestParam(name = "offset", required = false) Long offset) {
        return deadLetterService.reprocess(dltTopic, partition, offset);
    }

    // ---------------- Laboratorio ----------------

    @PostMapping("/lab/publish")
    @Operation(summary = "Publicar evento de prueba con key (demuestra key -> misma particion)")
    public Map<String, Object> publish(@RequestParam(name = "key", defaultValue = "RTX-LAB-0001") String key,
                                       @RequestParam(name = "eventType", defaultValue = "KAFKA_TEST_EVENT") String eventType,
                                       @RequestParam(name = "newStatus", required = false) String newStatus) throws Exception {
        return labService.publishTestEvent(key, eventType, newStatus);
    }

    @PostMapping("/lab/simulate/transient-failure")
    @Operation(summary = "Fallo transitorio: reintentos con backoff y luego DLT")
    public Map<String, Object> transientFailure(@RequestParam(name = "key", defaultValue = "RTX-LAB-FAIL") String key) throws Exception {
        return labService.simulateTransientFailure(key);
    }

    @PostMapping("/lab/simulate/poison-pill")
    @Operation(summary = "Mensaje no deserializable: DLT inmediato")
    public Map<String, Object> poisonPill(@RequestParam(name = "key", defaultValue = "RTX-LAB-POISON") String key) throws Exception {
        return labService.simulatePoisonPill(key);
    }

    @PostMapping("/lab/simulate/invalid-payload")
    @Operation(summary = "Payload que no pasa Bean Validation: DLT inmediato")
    public Map<String, Object> invalidPayload(@RequestParam(name = "key", defaultValue = "RTX-LAB-INVALID") String key) throws Exception {
        return labService.simulateInvalidPayload(key);
    }

    @PostMapping("/lab/load-test")
    @Operation(summary = "Prueba de carga al topic rutaexpress.loadtest (productor con batching/compresion)")
    public Map<String, Object> loadTest(@RequestParam(name = "count", defaultValue = "10000") int count,
                                        @RequestParam(name = "keys", defaultValue = "5") int keys) {
        return labService.loadTest(Math.min(count, 1_000_000), keys);
    }

    @PostMapping("/lab/load-test/consumer/{action}")
    @Operation(summary = "Pausar / reanudar el consumidor lento (pause | resume) para ver el lag crecer o bajar")
    public Map<String, Object> consumerAction(@PathVariable("action") String action) {
        MessageListenerContainer c = listenerRegistry.getListenerContainer(LoadTestSlowConsumer.LISTENER_ID);
        if (c == null) throw new IllegalStateException("Listener de carga no registrado");
        switch (action.toLowerCase()) {
            case "pause" -> c.pause();
            case "resume" -> c.resume();
            default -> throw new IllegalArgumentException("Accion invalida: usa pause o resume");
        }
        return Map.of("listener", LoadTestSlowConsumer.LISTENER_ID, "action", action,
                "paused", c.isPauseRequested(), "processedSoFar", slowConsumer.getProcessed());
    }

    @ExceptionHandler({IllegalArgumentException.class, NullPointerException.class})
    public ResponseEntity<Map<String, Object>> badRequest(RuntimeException ex) {
        return ResponseEntity.badRequest().body(Map.of("error", String.valueOf(ex.getMessage())));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> error(Exception ex) {
        Throwable root = ex;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(Map.of("error", root.getClass().getSimpleName() + ": " + root.getMessage()));
    }
}
