package com.firesentinel.alarmsystem;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FireSentinelApplication {

    public static void main(String[] args) {
        SpringApplication.run(FireSentinelApplication.class, args);
    }
} 