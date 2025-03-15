package com.firesentinel.alarmsystem.controller;

import com.firesentinel.alarmsystem.model.AlarmEvent;
import com.firesentinel.alarmsystem.model.AlarmSeverity;
import com.firesentinel.alarmsystem.service.ResilientAlarmHistoryService;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.micrometer.core.annotation.Timed;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * REST controller for accessing alarm history with resilience patterns.
 * This controller provides endpoints for retrieving alarm events with various filtering
 * and pagination options, with circuit breaking, retry, bulkhead, and time limiting capabilities.
 */
@RestController
@RequestMapping("/api/resilient/alarm-history")
@RequiredArgsConstructor
@Slf4j
public class ResilientAlarmHistoryController {

    private final ResilientAlarmHistoryService alarmHistoryService;
    private final MeterRegistry meterRegistry;
    
    /**
     * Gets the most recent alarm events.
     *
     * @param count The maximum number of events to retrieve (default: 10)
     * @return A list of alarm events, ordered by timestamp (most recent first)
     */
    @GetMapping("/recent")
    @Timed(value = "alarm.history.controller.recent", percentiles = {0.5, 0.95, 0.99})
    @CircuitBreaker(name = "apiService", fallbackMethod = "getRecentAlarmsFallback")
    @Retry(name = "apiService")
    @Bulkhead(name = "apiService")
    @TimeLimiter(name = "apiService")
    public CompletableFuture<ResponseEntity<List<AlarmEvent>>> getRecentAlarms(
            @RequestParam(defaultValue = "10") int count) {
        
        log.debug("Getting {} most recent alarm events", count);
        meterRegistry.counter("alarm.history.controller.recent.requests").increment();
        
        return alarmHistoryService.getRecentAlarms(count)
                .thenApply(ResponseEntity::ok);
    }
    
    /**
     * Fallback method for getting recent alarms when the service is unavailable.
     *
     * @param count The maximum number of events to retrieve
     * @param e The exception that triggered the fallback
     * @return A response entity with an empty list and a 503 status code
     */
    public CompletableFuture<ResponseEntity<List<AlarmEvent>>> getRecentAlarmsFallback(
            int count, Exception e) {
        
        log.warn("Fallback for getting recent alarms: {}", e.getMessage());
        meterRegistry.counter("alarm.history.controller.recent.fallback").increment();
        
        return CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .header("X-Fallback-Reason", e.getMessage())
                        .body(List.of())
        );
    }
    
    /**
     * Gets alarm events within a time window.
     *
     * @param startTime The start time of the window
     * @param endTime The end time of the window
     * @return A list of alarm events within the time window, ordered by timestamp
     */
    @GetMapping("/time-window")
    @Timed(value = "alarm.history.controller.time-window", percentiles = {0.5, 0.95, 0.99})
    @CircuitBreaker(name = "apiService", fallbackMethod = "getAlarmsInTimeWindowFallback")
    @Retry(name = "apiService")
    @Bulkhead(name = "apiService")
    @TimeLimiter(name = "apiService")
    public CompletableFuture<ResponseEntity<List<AlarmEvent>>> getAlarmsInTimeWindow(
            @RequestParam Instant startTime,
            @RequestParam Instant endTime) {
        
        log.debug("Getting alarm events between {} and {}", startTime, endTime);
        meterRegistry.counter("alarm.history.controller.time-window.requests").increment();
        
        if (startTime.isAfter(endTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Start time must be before end time");
        }
        
        return alarmHistoryService.getAlarmsInTimeWindow(startTime, endTime)
                .thenApply(ResponseEntity::ok);
    }
    
    /**
     * Fallback method for getting alarms in a time window when the service is unavailable.
     *
     * @param startTime The start time of the window
     * @param endTime The end time of the window
     * @param e The exception that triggered the fallback
     * @return A response entity with an empty list and a 503 status code
     */
    public CompletableFuture<ResponseEntity<List<AlarmEvent>>> getAlarmsInTimeWindowFallback(
            Instant startTime, Instant endTime, Exception e) {
        
        log.warn("Fallback for getting alarms in time window: {}", e.getMessage());
        meterRegistry.counter("alarm.history.controller.time-window.fallback").increment();
        
        return CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .header("X-Fallback-Reason", e.getMessage())
                        .body(List.of())
        );
    }
    
    /**
     * Gets alarm events for a specific device.
     *
     * @param deviceId The device ID
     * @param count The maximum number of events to retrieve (default: 10)
     * @return A list of alarm events for the device, ordered by timestamp (most recent first)
     */
    @GetMapping("/device/{deviceId}")
    @Timed(value = "alarm.history.controller.device", percentiles = {0.5, 0.95, 0.99})
    @CircuitBreaker(name = "apiService", fallbackMethod = "getDeviceAlarmsFallback")
    @Retry(name = "apiService")
    @Bulkhead(name = "apiService")
    @TimeLimiter(name = "apiService")
    public CompletableFuture<ResponseEntity<List<AlarmEvent>>> getDeviceAlarms(
            @PathVariable String deviceId,
            @RequestParam(defaultValue = "10") int count) {
        
        log.debug("Getting {} alarm events for device {}", count, deviceId);
        meterRegistry.counter("alarm.history.controller.device.requests").increment();
        
        // For this example, we'll use the recent alarms and filter by device ID
        // In a real implementation, you would add a specific method to the service
        return alarmHistoryService.getRecentAlarms(100)
                .thenApply(alarms -> alarms.stream()
                        .filter(alarm -> deviceId.equals(alarm.getDeviceId()))
                        .limit(count)
                        .collect(Collectors.toList()))
                .thenApply(ResponseEntity::ok);
    }
    
    /**
     * Fallback method for getting device alarms when the service is unavailable.
     *
     * @param deviceId The device ID
     * @param count The maximum number of events to retrieve
     * @param e The exception that triggered the fallback
     * @return A response entity with an empty list and a 503 status code
     */
    public CompletableFuture<ResponseEntity<List<AlarmEvent>>> getDeviceAlarmsFallback(
            String deviceId, int count, Exception e) {
        
        log.warn("Fallback for getting device alarms: {}", e.getMessage());
        meterRegistry.counter("alarm.history.controller.device.fallback").increment();
        
        return CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .header("X-Fallback-Reason", e.getMessage())
                        .body(List.of())
        );
    }
    
    /**
     * Gets alarm events filtered by severity.
     *
     * @param severity The alarm severity (HIGH, MEDIUM, LOW)
     * @param count The maximum number of events to retrieve (default: 10)
     * @return A list of alarm events with the specified severity, ordered by timestamp (most recent first)
     */
    @GetMapping("/severity/{severity}")
    @Timed(value = "alarm.history.controller.severity", percentiles = {0.5, 0.95, 0.99})
    @CircuitBreaker(name = "apiService", fallbackMethod = "getSeverityAlarmsFallback")
    @Retry(name = "apiService")
    @Bulkhead(name = "apiService")
    @TimeLimiter(name = "apiService")
    public CompletableFuture<ResponseEntity<List<AlarmEvent>>> getSeverityAlarms(
            @PathVariable AlarmSeverity severity,
            @RequestParam(defaultValue = "10") int count) {
        
        log.debug("Getting {} alarm events with severity {}", count, severity);
        meterRegistry.counter("alarm.history.controller.severity.requests").increment();
        
        // For this example, we'll use the recent alarms and filter by severity
        // In a real implementation, you would add a specific method to the service
        return alarmHistoryService.getRecentAlarms(100)
                .thenApply(alarms -> alarms.stream()
                        .filter(alarm -> severity == alarm.getSeverity())
                        .limit(count)
                        .collect(Collectors.toList()))
                .thenApply(ResponseEntity::ok);
    }
    
    /**
     * Fallback method for getting severity alarms when the service is unavailable.
     *
     * @param severity The alarm severity
     * @param count The maximum number of events to retrieve
     * @param e The exception that triggered the fallback
     * @return A response entity with an empty list and a 503 status code
     */
    public CompletableFuture<ResponseEntity<List<AlarmEvent>>> getSeverityAlarmsFallback(
            AlarmSeverity severity, int count, Exception e) {
        
        log.warn("Fallback for getting severity alarms: {}", e.getMessage());
        meterRegistry.counter("alarm.history.controller.severity.fallback").increment();
        
        return CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .header("X-Fallback-Reason", e.getMessage())
                        .body(List.of())
        );
    }
    
    /**
     * Gets alarm events filtered by type.
     *
     * @param alarmType The alarm type (e.g., FIRE, SMOKE)
     * @param count The maximum number of events to retrieve (default: 10)
     * @return A list of alarm events with the specified type, ordered by timestamp (most recent first)
     */
    @GetMapping("/type/{alarmType}")
    @Timed(value = "alarm.history.controller.type", percentiles = {0.5, 0.95, 0.99})
    @CircuitBreaker(name = "apiService", fallbackMethod = "getTypeAlarmsFallback")
    @Retry(name = "apiService")
    @Bulkhead(name = "apiService")
    @TimeLimiter(name = "apiService")
    public CompletableFuture<ResponseEntity<List<AlarmEvent>>> getTypeAlarms(
            @PathVariable String alarmType,
            @RequestParam(defaultValue = "10") int count) {
        
        log.debug("Getting {} alarm events with type {}", count, alarmType);
        meterRegistry.counter("alarm.history.controller.type.requests").increment();
        
        // For this example, we'll use the recent alarms and filter by type
        // In a real implementation, you would add a specific method to the service
        return alarmHistoryService.getRecentAlarms(100)
                .thenApply(alarms -> alarms.stream()
                        .filter(alarm -> alarmType.equalsIgnoreCase(alarm.getType()))
                        .limit(count)
                        .collect(Collectors.toList()))
                .thenApply(ResponseEntity::ok);
    }
    
    /**
     * Fallback method for getting type alarms when the service is unavailable.
     *
     * @param alarmType The alarm type
     * @param count The maximum number of events to retrieve
     * @param e The exception that triggered the fallback
     * @return A response entity with an empty list and a 503 status code
     */
    public CompletableFuture<ResponseEntity<List<AlarmEvent>>> getTypeAlarmsFallback(
            String alarmType, int count, Exception e) {
        
        log.warn("Fallback for getting type alarms: {}", e.getMessage());
        meterRegistry.counter("alarm.history.controller.type.fallback").increment();
        
        return CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .header("X-Fallback-Reason", e.getMessage())
                        .body(List.of())
        );
    }
    
    /**
     * Gets statistics about alarm events.
     *
     * @return A map of statistics, including total counts and counts by severity and type
     */
    @GetMapping("/stats")
    @Timed(value = "alarm.history.controller.stats", percentiles = {0.5, 0.95, 0.99})
    @CircuitBreaker(name = "apiService", fallbackMethod = "getAlarmStatsFallback")
    @Retry(name = "apiService")
    @Bulkhead(name = "apiService")
    @TimeLimiter(name = "apiService")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getAlarmStats() {
        
        log.debug("Getting alarm statistics");
        meterRegistry.counter("alarm.history.controller.stats.requests").increment();
        
        Timer.Sample sample = Timer.start(meterRegistry);
        
        return alarmHistoryService.getRecentAlarms(1000)
                .thenApply(alarms -> {
                    Map<String, Object> stats = new HashMap<>();
                    
                    // Total count
                    stats.put("totalCount", alarms.size());
                    
                    // Counts by severity
                    Map<AlarmSeverity, Long> countsBySeverity = alarms.stream()
                            .collect(Collectors.groupingBy(AlarmEvent::getSeverity, Collectors.counting()));
                    stats.put("countsBySeverity", countsBySeverity);
                    
                    // Counts by type
                    Map<String, Long> countsByType = alarms.stream()
                            .collect(Collectors.groupingBy(AlarmEvent::getType, Collectors.counting()));
                    stats.put("countsByType", countsByType);
                    
                    // Record timing
                    sample.stop(meterRegistry.timer("alarm.history.controller.stats.processing.time"));
                    
                    return ResponseEntity.ok(stats);
                });
    }
    
    /**
     * Fallback method for getting alarm statistics when the service is unavailable.
     *
     * @param e The exception that triggered the fallback
     * @return A response entity with empty statistics and a 503 status code
     */
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getAlarmStatsFallback(Exception e) {
        
        log.warn("Fallback for getting alarm statistics: {}", e.getMessage());
        meterRegistry.counter("alarm.history.controller.stats.fallback").increment();
        
        Map<String, Object> emptyStats = new HashMap<>();
        emptyStats.put("totalCount", 0);
        emptyStats.put("countsBySeverity", Map.of());
        emptyStats.put("countsByType", Map.of());
        emptyStats.put("fallbackReason", e.getMessage());
        
        return CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .header("X-Fallback-Reason", e.getMessage())
                        .body(emptyStats)
        );
    }
    
    /**
     * Cleans up old alarm events.
     *
     * @return The number of events removed
     */
    @DeleteMapping("/cleanup")
    @Timed(value = "alarm.history.controller.cleanup", percentiles = {0.5, 0.95, 0.99})
    @CircuitBreaker(name = "apiService", fallbackMethod = "cleanupAlarmsFallback")
    @Retry(name = "apiService")
    @Bulkhead(name = "apiService")
    public ResponseEntity<Map<String, Object>> cleanupAlarms() {
        
        log.debug("Cleaning up old alarm events");
        meterRegistry.counter("alarm.history.controller.cleanup.requests").increment();
        
        long removedCount = alarmHistoryService.cleanupOldAlarms();
        
        Map<String, Object> result = new HashMap<>();
        result.put("removedCount", removedCount);
        result.put("timestamp", Instant.now());
        
        return ResponseEntity.ok(result);
    }
    
    /**
     * Fallback method for cleaning up alarms when the service is unavailable.
     *
     * @param e The exception that triggered the fallback
     * @return A response entity with a 503 status code
     */
    public ResponseEntity<Map<String, Object>> cleanupAlarmsFallback(Exception e) {
        
        log.warn("Fallback for cleaning up alarms: {}", e.getMessage());
        meterRegistry.counter("alarm.history.controller.cleanup.fallback").increment();
        
        Map<String, Object> result = new HashMap<>();
        result.put("removedCount", 0);
        result.put("timestamp", Instant.now());
        result.put("fallbackReason", e.getMessage());
        
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("X-Fallback-Reason", e.getMessage())
                .body(result);
    }
    
    /**
     * Health check endpoint to verify the service is available.
     *
     * @return A response entity with the service status
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        
        Map<String, Object> health = new HashMap<>();
        health.put("status", alarmHistoryService.isRedisAvailable() ? "UP" : "DOWN");
        health.put("timestamp", Instant.now());
        
        HttpStatus status = alarmHistoryService.isRedisAvailable() ? 
                HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        
        return ResponseEntity.status(status).body(health);
    }
} 