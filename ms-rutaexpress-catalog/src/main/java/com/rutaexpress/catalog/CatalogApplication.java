package com.rutaexpress.catalog;

import com.rutaexpress.catalog.dto.ServiceOfferingRequest;
import com.rutaexpress.catalog.service.CatalogService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import java.math.BigDecimal;

@SpringBootApplication
public class CatalogApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogApplication.class, args);
    }

    @Bean
    CommandLineRunner initDatabase(CatalogService service) {
        return args -> {
            try {
                service.createService(ServiceOfferingRequest.builder()
                        .code("SAME_DAY")
                        .name("Express Mismo Día")
                        .description("Despacho urgente entregado dentro de las 4 horas hábiles")
                        .basePrice(new BigDecimal("4500.00"))
                        .pricePerKm(new BigDecimal("350.00"))
                        .maxDailyCapacity(25)
                        .active(true)
                        .build());

                service.createService(ServiceOfferingRequest.builder()
                        .code("NEXT_DAY")
                        .name("Estándar Día Siguiente")
                        .description("Despacho garantizado para el día hábil siguiente")
                        .basePrice(new BigDecimal("2900.00"))
                        .pricePerKm(new BigDecimal("200.00"))
                        .maxDailyCapacity(50)
                        .active(true)
                        .build());

                service.createService(ServiceOfferingRequest.builder()
                        .code("ECONOMY")
                        .name("Económico 48-72h")
                        .description("Entrega económica para envíos no urgentes")
                        .basePrice(new BigDecimal("1990.00"))
                        .pricePerKm(new BigDecimal("120.00"))
                        .maxDailyCapacity(100)
                        .active(true)
                        .build());
            } catch (Exception ignored) {
                // Ya inicializados
            }
        };
    }
}
