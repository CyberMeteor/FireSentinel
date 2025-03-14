package com.firesentinel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the FireSentinel system.
 * This class bootstraps the Spring Boot application and enables various features
 * such as caching, Kafka, asynchronous processing, and scheduling.
 */
@SpringBootApplication
@EnableCaching
@EnableKafka
@EnableAsync
@EnableScheduling
public class FireSentinelApplication {

    public static void main(String[] args) {
        SpringApplication.run(FireSentinelApplication.class, args);
    }
} 