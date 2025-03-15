package com.firesentinel.alarmsystem.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firesentinel.alarmsystem.model.AlarmEvent;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Resilient service for storing and retrieving alarm history using Redis ZSets (Sorted Sets).
 * This service provides efficient time-based storage and retrieval of alarm events with
 * circuit breaking, retry, bulkhead, and time limiting capabilities.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResilientAlarmHistoryService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    
    @Value("${alarm.history.retention-days:30}")
    private int retentionDays;
    
    // Redis key prefixes
    private static final String ALARM_HISTORY_KEY = "alarm:history";
    private static final String ALARM_HISTORY_BY_DEVICE_KEY = "alarm:history:device:";
    private static final String ALARM_HISTORY_BY_SEVERITY_KEY = "alarm:history:severity:";
    private static final String ALARM_HISTORY_BY_TYPE_KEY = "alarm:history:type:";
    
    // In-memory cache for fallback
    private final List<AlarmEvent> inMemoryCache = Collections.synchronizedList(new ArrayList<>());
    private static final int IN_MEMORY_CACHE_SIZE = 1000;
    
    /**
     * Stores an alarm event in the history with circuit breaking and retry capabilities.
     * If Redis is unavailable, the event is stored in an in-memory cache as a fallback.
     *
     * @param alarmEvent The alarm event to store
     * @return True if the event was stored successfully, false otherwise
     */
    @CircuitBreaker(name = "redisService", fallbackMethod = "storeAlarmEventFallback")
    @Retry(name = "redisService")
    @Bulkhead(name = "redisService")
    public boolean storeAlarmEvent(AlarmEvent alarmEvent) {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            // Convert the alarm event to JSON
            String alarmJson = objectMapper.writeValueAsString(alarmEvent);
            
            // Get the timestamp as a score for the sorted set
            double score = alarmEvent.getTimestamp().toEpochMilli();
            
            // Store in global history
            redisTemplate.opsForZSet().add(ALARM_HISTORY_KEY, alarmJson, score);
            
            // Store in device-specific history
            redisTemplate.opsForZSet().add(
                    ALARM_HISTORY_BY_DEVICE_KEY + alarmEvent.getDeviceId(), 
                    alarmJson, 
                    score);
            
            // Store in severity-specific history
            redisTemplate.opsForZSet().add(
                    ALARM_HISTORY_BY_SEVERITY_KEY + alarmEvent.getSeverity().toString().toLowerCase(), 
                    alarmJson, 
                    score);
            
            // Store in type-specific history
            redisTemplate.opsForZSet().add(
                    ALARM_HISTORY_BY_TYPE_KEY + alarmEvent.getType().toLowerCase(), 
                    alarmJson, 
                    score);
            
            // Set expiration for all keys
            long expirationSeconds = TimeUnit.DAYS.toSeconds(retentionDays);
            redisTemplate.expire(ALARM_HISTORY_KEY, expirationSeconds, TimeUnit.SECONDS);
            redisTemplate.expire(
                    ALARM_HISTORY_BY_DEVICE_KEY + alarmEvent.getDeviceId(), 
                    expirationSeconds, 
                    TimeUnit.SECONDS);
            redisTemplate.expire(
                    ALARM_HISTORY_BY_SEVERITY_KEY + alarmEvent.getSeverity().toString().toLowerCase(), 
                    expirationSeconds, 
                    TimeUnit.SECONDS);
            redisTemplate.expire(
                    ALARM_HISTORY_BY_TYPE_KEY + alarmEvent.getType().toLowerCase(), 
                    expirationSeconds, 
                    TimeUnit.SECONDS);
            
            // Add to in-memory cache for fallback
            addToInMemoryCache(alarmEvent);
            
            log.debug("Stored alarm event in history: {}", alarmEvent.getId());
            return true;
            
        } catch (JsonProcessingException e) {
            log.error("Failed to store alarm event in history: {}", e.getMessage(), e);
            return false;
        } finally {
            sample.stop(meterRegistry.timer("alarm.history.store.time"));
        }
    }
    
    /**
     * Fallback method for storing alarm events when Redis is unavailable.
     * Stores the event in an in-memory cache.
     *
     * @param alarmEvent The alarm event to store
     * @param e The exception that triggered the fallback
     * @return True if the event was stored in the fallback cache
     */
    public boolean storeAlarmEventFallback(AlarmEvent alarmEvent, Exception e) {
        log.warn("Using fallback for storing alarm event: {}", e.getMessage());
        
        // Record the fallback metric
        meterRegistry.counter("alarm.history.store.fallback").increment();
        
        // Add to in-memory cache
        addToInMemoryCache(alarmEvent);
        
        return true;
    }
    
    /**
     * Gets the most recent alarm events with circuit breaking and retry capabilities.
     *
     * @param count The maximum number of events to retrieve
     * @return A list of alarm events, ordered by timestamp (most recent first)
     */
    @CircuitBreaker(name = "redisService", fallbackMethod = "getRecentAlarmsFallback")
    @Retry(name = "redisService")
    @Bulkhead(name = "redisService")
    @TimeLimiter(name = "redisService")
    public CompletableFuture<List<AlarmEvent>> getRecentAlarms(int count) {
        return CompletableFuture.supplyAsync(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                // Get the most recent events from the global history
                Set<String> alarmJsonSet = redisTemplate.opsForZSet().reverseRange(
                        ALARM_HISTORY_KEY, 
                        0, 
                        count - 1);
                
                List<AlarmEvent> result = convertJsonSetToAlarmEvents(alarmJsonSet);
                
                // Record success metric
                meterRegistry.counter("alarm.history.get.success").increment();
                
                return result;
            } finally {
                sample.stop(meterRegistry.timer("alarm.history.get.time"));
            }
        });
    }
    
    /**
     * Fallback method for retrieving recent alarms when Redis is unavailable.
     * Returns alarms from the in-memory cache.
     *
     * @param count The maximum number of events to retrieve
     * @param e The exception that triggered the fallback
     * @return A list of alarm events from the in-memory cache
     */
    public CompletableFuture<List<AlarmEvent>> getRecentAlarmsFallback(int count, Exception e) {
        log.warn("Using fallback for retrieving recent alarms: {}", e.getMessage());
        
        // Record the fallback metric
        meterRegistry.counter("alarm.history.get.fallback").increment();
        
        // Return from in-memory cache
        return CompletableFuture.completedFuture(
                inMemoryCache.stream()
                        .sorted((a1, a2) -> a2.getTimestamp().compareTo(a1.getTimestamp()))
                        .limit(count)
                        .collect(Collectors.toList())
        );
    }
    
    /**
     * Gets alarm events within a time window with circuit breaking and retry capabilities.
     *
     * @param startTime The start time of the window
     * @param endTime The end time of the window
     * @return A list of alarm events within the time window, ordered by timestamp
     */
    @CircuitBreaker(name = "redisService", fallbackMethod = "getAlarmsInTimeWindowFallback")
    @Retry(name = "redisService")
    @Bulkhead(name = "redisService")
    @TimeLimiter(name = "redisService")
    public CompletableFuture<List<AlarmEvent>> getAlarmsInTimeWindow(Instant startTime, Instant endTime) {
        return CompletableFuture.supplyAsync(() -> {
            Timer.Sample sample = Timer.start(meterRegistry);
            try {
                // Get events within the time window from the global history
                Set<String> alarmJsonSet = redisTemplate.opsForZSet().rangeByScore(
                        ALARM_HISTORY_KEY, 
                        startTime.toEpochMilli(), 
                        endTime.toEpochMilli());
                
                return convertJsonSetToAlarmEvents(alarmJsonSet);
            } finally {
                sample.stop(meterRegistry.timer("alarm.history.time-window.time"));
            }
        });
    }
    
    /**
     * Fallback method for retrieving alarms in a time window when Redis is unavailable.
     * Returns alarms from the in-memory cache that fall within the time window.
     *
     * @param startTime The start time of the window
     * @param endTime The end time of the window
     * @param e The exception that triggered the fallback
     * @return A list of alarm events from the in-memory cache that fall within the time window
     */
    public CompletableFuture<List<AlarmEvent>> getAlarmsInTimeWindowFallback(Instant startTime, Instant endTime, Exception e) {
        log.warn("Using fallback for retrieving alarms in time window: {}", e.getMessage());
        
        // Record the fallback metric
        meterRegistry.counter("alarm.history.time-window.fallback").increment();
        
        // Return from in-memory cache
        return CompletableFuture.completedFuture(
                inMemoryCache.stream()
                        .filter(alarm -> !alarm.getTimestamp().isBefore(startTime) && !alarm.getTimestamp().isAfter(endTime))
                        .sorted((a1, a2) -> a1.getTimestamp().compareTo(a2.getTimestamp()))
                        .collect(Collectors.toList())
        );
    }
    
    /**
     * Cleans up old alarm events with circuit breaking and retry capabilities.
     * This method removes events older than the retention period.
     *
     * @return The number of events removed
     */
    @CircuitBreaker(name = "redisService", fallbackMethod = "cleanupOldAlarmsFallback")
    @Retry(name = "redisService")
    @Bulkhead(name = "redisService")
    public long cleanupOldAlarms() {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            // Calculate the cutoff timestamp
            Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
            
            // Remove events older than the cutoff from the global history
            Long count = redisTemplate.opsForZSet().removeRangeByScore(
                    ALARM_HISTORY_KEY, 
                    0, 
                    cutoff.toEpochMilli());
            
            // Clean up in-memory cache
            cleanupInMemoryCache(cutoff);
            
            log.info("Cleaned up {} old alarm events", count);
            return count != null ? count : 0;
        } finally {
            sample.stop(meterRegistry.timer("alarm.history.cleanup.time"));
        }
    }
    
    /**
     * Fallback method for cleaning up old alarms when Redis is unavailable.
     * Cleans up the in-memory cache.
     *
     * @param e The exception that triggered the fallback
     * @return The number of events removed from the in-memory cache
     */
    public long cleanupOldAlarmsFallback(Exception e) {
        log.warn("Using fallback for cleaning up old alarms: {}", e.getMessage());
        
        // Record the fallback metric
        meterRegistry.counter("alarm.history.cleanup.fallback").increment();
        
        // Clean up in-memory cache
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        return cleanupInMemoryCache(cutoff);
    }
    
    /**
     * Adds an alarm event to the in-memory cache.
     * If the cache exceeds the maximum size, the oldest events are removed.
     *
     * @param alarmEvent The alarm event to add
     */
    private synchronized void addToInMemoryCache(AlarmEvent alarmEvent) {
        // Add to in-memory cache
        inMemoryCache.add(alarmEvent);
        
        // If cache exceeds maximum size, remove oldest events
        if (inMemoryCache.size() > IN_MEMORY_CACHE_SIZE) {
            // Sort by timestamp (oldest first)
            inMemoryCache.sort((a1, a2) -> a1.getTimestamp().compareTo(a2.getTimestamp()));
            
            // Remove oldest events
            int removeCount = inMemoryCache.size() - IN_MEMORY_CACHE_SIZE;
            for (int i = 0; i < removeCount; i++) {
                inMemoryCache.remove(0);
            }
        }
    }
    
    /**
     * Cleans up the in-memory cache by removing events older than the cutoff timestamp.
     *
     * @param cutoff The cutoff timestamp
     * @return The number of events removed
     */
    private synchronized long cleanupInMemoryCache(Instant cutoff) {
        int sizeBefore = inMemoryCache.size();
        
        // Remove events older than the cutoff
        inMemoryCache.removeIf(alarm -> alarm.getTimestamp().isBefore(cutoff));
        
        return sizeBefore - inMemoryCache.size();
    }
    
    /**
     * Converts a set of JSON strings to a list of AlarmEvent objects.
     *
     * @param alarmJsonSet The set of JSON strings
     * @return A list of AlarmEvent objects
     */
    private List<AlarmEvent> convertJsonSetToAlarmEvents(Set<String> alarmJsonSet) {
        if (alarmJsonSet == null || alarmJsonSet.isEmpty()) {
            return new ArrayList<>();
        }
        
        return alarmJsonSet.stream()
                .map(json -> {
                    try {
                        return objectMapper.readValue(json, AlarmEvent.class);
                    } catch (JsonProcessingException e) {
                        log.error("Failed to parse alarm event JSON: {}", e.getMessage(), e);
                        return null;
                    }
                })
                .filter(alarm -> alarm != null)
                .collect(Collectors.toList());
    }
    
    /**
     * Checks if Redis is available.
     *
     * @return True if Redis is available, false otherwise
     */
    public boolean isRedisAvailable() {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey("health:check"));
        } catch (RedisConnectionFailureException e) {
            log.warn("Redis is not available: {}", e.getMessage());
            return false;
        }
    }
} 