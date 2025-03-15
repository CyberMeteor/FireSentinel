package com.firesentinel.alarmsystem.config;

import io.micrometer.core.aop.TimedAspect;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.binder.system.UptimeMetrics;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for application metrics using Micrometer.
 * This class sets up various metrics collectors and provides beans for custom metrics.
 */
@Configuration
@RequiredArgsConstructor
public class MetricsConfig {

    private final MeterRegistry meterRegistry;

    /**
     * Enables the @Timed annotation for measuring method execution times.
     */
    @Bean
    public TimedAspect timedAspect() {
        return new TimedAspect(meterRegistry);
    }

    /**
     * Registers JVM metrics for monitoring memory usage.
     */
    @Bean
    public JvmMemoryMetrics jvmMemoryMetrics() {
        JvmMemoryMetrics memoryMetrics = new JvmMemoryMetrics();
        memoryMetrics.bindTo(meterRegistry);
        return memoryMetrics;
    }

    /**
     * Registers JVM metrics for monitoring garbage collection.
     */
    @Bean
    public JvmGcMetrics jvmGcMetrics() {
        JvmGcMetrics gcMetrics = new JvmGcMetrics();
        gcMetrics.bindTo(meterRegistry);
        return gcMetrics;
    }

    /**
     * Registers JVM metrics for monitoring thread usage.
     */
    @Bean
    public JvmThreadMetrics jvmThreadMetrics() {
        JvmThreadMetrics threadMetrics = new JvmThreadMetrics();
        threadMetrics.bindTo(meterRegistry);
        return threadMetrics;
    }

    /**
     * Registers JVM metrics for monitoring class loader.
     */
    @Bean
    public ClassLoaderMetrics classLoaderMetrics() {
        ClassLoaderMetrics classLoaderMetrics = new ClassLoaderMetrics();
        classLoaderMetrics.bindTo(meterRegistry);
        return classLoaderMetrics;
    }

    /**
     * Registers system metrics for monitoring processor usage.
     */
    @Bean
    public ProcessorMetrics processorMetrics() {
        ProcessorMetrics processorMetrics = new ProcessorMetrics();
        processorMetrics.bindTo(meterRegistry);
        return processorMetrics;
    }

    /**
     * Registers system metrics for monitoring uptime.
     */
    @Bean
    public UptimeMetrics uptimeMetrics() {
        UptimeMetrics uptimeMetrics = new UptimeMetrics();
        uptimeMetrics.bindTo(meterRegistry);
        return uptimeMetrics;
    }

    /**
     * Creates a counter for tracking alarm events.
     */
    @Bean
    public Counter alarmEventsCounter() {
        return Counter.builder("firesentinel.alarms.count")
                .description("Count of alarm events processed")
                .register(meterRegistry);
    }

    /**
     * Creates a counter for tracking alarm events by severity.
     */
    @Bean
    public Counter alarmEventsBySeverityCounter() {
        return Counter.builder("firesentinel.alarms.by.severity")
                .description("Count of alarm events by severity")
                .tag("severity", "unknown")
                .register(meterRegistry);
    }

    /**
     * Creates a timer for measuring alarm processing time.
     */
    @Bean
    public Timer alarmProcessingTimer() {
        return Timer.builder("firesentinel.alarms.processing.time")
                .description("Time taken to process alarm events")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }
} 