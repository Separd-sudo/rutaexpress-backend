package com.rutaexpress.kafka.listener;

import com.rutaexpress.kafka.config.KafkaClusterConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Consumidor deliberadamente lento (guia 3.3.2) para provocar y observar consumer lag.
 * Con processing-delay-ms=10 procesa ~100 msg/s por hilo; con 3 particiones y
 * concurrency=3 el lag baja 3 veces mas rapido (escalamiento horizontal).
 */
@Component
@Slf4j
public class LoadTestSlowConsumer {

    public static final String LISTENER_ID = "loadtest-slow-consumer";

    private final long delayMs;
    private final AtomicLong processed = new AtomicLong();

    public LoadTestSlowConsumer(@Value("${kafka.loadtest.processing-delay-ms:10}") long delayMs) {
        this.delayMs = delayMs;
    }

    @KafkaListener(id = LISTENER_ID, groupId = "loadtest-group", topics = KafkaClusterConfig.LOADTEST,
            concurrency = "${kafka.loadtest.concurrency:1}")
    public void listen(String message) throws InterruptedException {
        Thread.sleep(delayMs);
        long n = processed.incrementAndGet();
        if (n % 1000 == 0) {
            log.info("[LoadTest] {} mensajes procesados por loadtest-group", n);
        }
    }

    public long getProcessed() {
        return processed.get();
    }
}
