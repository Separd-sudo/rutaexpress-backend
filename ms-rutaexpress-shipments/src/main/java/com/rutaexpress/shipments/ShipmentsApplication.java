package com.rutaexpress.shipments;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class ShipmentsApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShipmentsApplication.class, args);
    }
}