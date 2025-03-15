package com.firesentinel.alarmsystem.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firesentinel.alarmsystem.model.AlarmEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Service for storing and retrieving alarm history using Redis ZSets (Sorted Sets).
 * This service provides efficient time-based storage and retrieval of alarm events.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlarmHistoryService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${alarm.history.retention-days:30}")
    private int retentionDays;
    
    // Redis key prefixes
    private static final String ALARM_HISTORY_KEY = "alarm:history";
    private static final String ALARM_HISTORY_BY_DEVICE_KEY = "alarm:history:device:";
    private static final String ALARM_HISTORY_BY_SEVERITY_KEY = "alarm:history:severity:";
    private static final String ALARM_HISTORY_BY_TYPE_KEY = "alarm:history:type:";
    
    /**
     * Stores an alarm event in the history.
     * The event is stored in multiple sorted sets for efficient querying:
     * - Global history
     * - Device-specific history
     * - Severity-specific history
     * - Type-specific history
     *
     * @param alarmEvent The alarm event to store
     * @return True if the event was stored successfully, false otherwise
     */
    public boolean storeAlarmEvent(AlarmEvent alarmEvent) {
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
            
            log.debug("Stored alarm event in history: {}", alarmEvent.getId());
            return true;
            
        } catch (JsonProcessingException e) {
            log.error("Failed to store alarm event in history: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Gets the most recent alarm events.
     *
     * @param count The maximum number of events to retrieve
     * @return A list of alarm events, ordered by timestamp (most recent first)
     */
    public List<AlarmEvent> getRecentAlarms(int count) {
        // Get the most recent events from the global history
        Set<String> alarmJsonSet = redisTemplate.opsForZSet().reverseRange(
                ALARM_HISTORY_KEY, 
                0, 
                count - 1);
        
        return convertJsonSetToAlarmEvents(alarmJsonSet);
    }
    
    /**
     * Gets alarm events within a time window.
     *
     * @param startTime The start time of the window
     * @param endTime The end time of the window
     * @return A list of alarm events within the time window, ordered by timestamp
     */
    public List<AlarmEvent> getAlarmsInTimeWindow(Instant startTime, Instant endTime) {
        // Get events within the time window from the global history
        Set<String> alarmJsonSet = redisTemplate.opsForZSet().rangeByScore(
                ALARM_HISTORY_KEY, 
                startTime.toEpochMilli(), 
                endTime.toEpochMilli());
        
        return convertJsonSetToAlarmEvents(alarmJsonSet);
    }
    
    /**
     * Gets alarm events for a specific device.
     *
     * @param deviceId The device ID
     * @param count The maximum number of events to retrieve
     * @return A list of alarm events for the device, ordered by timestamp (most recent first)
     */
    public List<AlarmEvent> getAlarmsByDevice(String deviceId, int count) {
        // Get the most recent events for the device
        Set<String> alarmJsonSet = redisTemplate.opsForZSet().reverseRange(
                ALARM_HISTORY_BY_DEVICE_KEY + deviceId, 
                0, 
                count - 1);
        
        return convertJsonSetToAlarmEvents(alarmJsonSet);
    }
    
    /**
     * Gets alarm events for a specific severity.
     *
     * @param severity The severity level (HIGH, MEDIUM, LOW)
     * @param count The maximum number of events to retrieve
     * @return A list of alarm events with the specified severity, ordered by timestamp (most recent first)
     */
    public List<AlarmEvent> getAlarmsBySeverity(String severity, int count) {
        // Get the most recent events for the severity
        Set<String> alarmJsonSet = redisTemplate.opsForZSet().reverseRange(
                ALARM_HISTORY_BY_SEVERITY_KEY + severity.toLowerCase(), 
                0, 
                count - 1);
        
        return convertJsonSetToAlarmEvents(alarmJsonSet);
    }
    
    /**
     * Gets alarm events for a specific type.
     *
     * @param alarmType The alarm type (FIRE, SMOKE, GAS, etc.)
     * @param count The maximum number of events to retrieve
     * @return A list of alarm events with the specified type, ordered by timestamp (most recent first)
     */
    public List<AlarmEvent> getAlarmsByType(String alarmType, int count) {
        // Get the most recent events for the type
        Set<String> alarmJsonSet = redisTemplate.opsForZSet().reverseRange(
                ALARM_HISTORY_BY_TYPE_KEY + alarmType.toLowerCase(), 
                0, 
                count - 1);
        
        return convertJsonSetToAlarmEvents(alarmJsonSet);
    }
    
    /**
     * Gets alarm events with pagination.
     * This method implements rolling pagination, where each page contains events
     * from a specific time window.
     *
     * @param page The page number (0-based)
     * @param pageSize The number of events per page
     * @return A list of alarm events for the specified page
     */
    public List<AlarmEvent> getAlarmsWithPagination(int page, int pageSize) {
        // Calculate the start and end indices for the page
        long start = (long) page * pageSize;
        long end = start + pageSize - 1;
        
        // Get the events for the page from the global history
        Set<String> alarmJsonSet = redisTemplate.opsForZSet().reverseRange(
                ALARM_HISTORY_KEY, 
                start, 
                end);
        
        return convertJsonSetToAlarmEvents(alarmJsonSet);
    }
    
    /**
     * Gets alarm events with time-based pagination.
     * This method implements time-based pagination, where each page contains events
     * from a specific time window.
     *
     * @param startTime The start time of the window
     * @param pageSize The number of events per page
     * @return A list of alarm events for the specified time window
     */
    public List<AlarmEvent> getAlarmsWithTimePagination(Instant startTime, int pageSize) {
        // Calculate the end time (start time + 1 hour)
        Instant endTime = startTime.plus(1, ChronoUnit.HOURS);
        
        // Get events within the time window from the global history
        Set<String> alarmJsonSet = redisTemplate.opsForZSet().rangeByScore(
                ALARM_HISTORY_KEY, 
                startTime.toEpochMilli(), 
                endTime.toEpochMilli(), 
                0, 
                pageSize);
        
        return convertJsonSetToAlarmEvents(alarmJsonSet);
    }
    
    /**
     * Gets the next page of alarm events based on the last event timestamp.
     * This method implements cursor-based pagination, where each page contains events
     * older than the last event of the previous page.
     *
     * @param lastEventTimestamp The timestamp of the last event from the previous page
     * @param pageSize The number of events per page
     * @return A list of alarm events for the next page
     */
    public List<AlarmEvent> getNextAlarmPage(Instant lastEventTimestamp, int pageSize) {
        // Get events older than the last event timestamp
        Set<String> alarmJsonSet = redisTemplate.opsForZSet().reverseRangeByScore(
                ALARM_HISTORY_KEY, 
                0, 
                lastEventTimestamp.toEpochMilli() - 1, 
                0, 
                pageSize);
        
        return convertJsonSetToAlarmEvents(alarmJsonSet);
    }
    
    /**
     * Gets the count of alarm events in the history.
     *
     * @return The count of alarm events
     */
    public long getAlarmCount() {
        Long count = redisTemplate.opsForZSet().size(ALARM_HISTORY_KEY);
        return count != null ? count : 0;
    }
    
    /**
     * Gets the count of alarm events for a specific device.
     *
     * @param deviceId The device ID
     * @return The count of alarm events for the device
     */
    public long getAlarmCountByDevice(String deviceId) {
        Long count = redisTemplate.opsForZSet().size(ALARM_HISTORY_BY_DEVICE_KEY + deviceId);
        return count != null ? count : 0;
    }
    
    /**
     * Gets the count of alarm events for a specific severity.
     *
     * @param severity The severity level (HIGH, MEDIUM, LOW)
     * @return The count of alarm events with the specified severity
     */
    public long getAlarmCountBySeverity(String severity) {
        Long count = redisTemplate.opsForZSet().size(
                ALARM_HISTORY_BY_SEVERITY_KEY + severity.toLowerCase());
        return count != null ? count : 0;
    }
    
    /**
     * Gets the count of alarm events for a specific type.
     *
     * @param alarmType The alarm type (FIRE, SMOKE, GAS, etc.)
     * @return The count of alarm events with the specified type
     */
    public long getAlarmCountByType(String alarmType) {
        Long count = redisTemplate.opsForZSet().size(
                ALARM_HISTORY_BY_TYPE_KEY + alarmType.toLowerCase());
        return count != null ? count : 0;
    }
    
    /**
     * Gets the count of alarm events within a time window.
     *
     * @param startTime The start time of the window
     * @param endTime The end time of the window
     * @return The count of alarm events within the time window
     */
    public long getAlarmCountInTimeWindow(Instant startTime, Instant endTime) {
        Long count = redisTemplate.opsForZSet().count(
                ALARM_HISTORY_KEY, 
                startTime.toEpochMilli(), 
                endTime.toEpochMilli());
        return count != null ? count : 0;
    }
    
    /**
     * Gets the timestamp of the oldest alarm event in the history.
     *
     * @return The timestamp of the oldest alarm event, or null if there are no events
     */
    public Instant getOldestAlarmTimestamp() {
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().rangeWithScores(
                ALARM_HISTORY_KEY, 
                0, 
                0);
        
        if (tuples != null && !tuples.isEmpty()) {
            ZSetOperations.TypedTuple<String> tuple = tuples.iterator().next();
            Double score = tuple.getScore();
            if (score != null) {
                return Instant.ofEpochMilli(score.longValue());
            }
        }
        
        return null;
    }
    
    /**
     * Gets the timestamp of the most recent alarm event in the history.
     *
     * @return The timestamp of the most recent alarm event, or null if there are no events
     */
    public Instant getNewestAlarmTimestamp() {
        Set<ZSetOperations.TypedTuple<String>> tuples = redisTemplate.opsForZSet().reverseRangeWithScores(
                ALARM_HISTORY_KEY, 
                0, 
                0);
        
        if (tuples != null && !tuples.isEmpty()) {
            ZSetOperations.TypedTuple<String> tuple = tuples.iterator().next();
            Double score = tuple.getScore();
            if (score != null) {
                return Instant.ofEpochMilli(score.longValue());
            }
        }
        
        return null;
    }
    
    /**
     * Cleans up old alarm events.
     * This method removes events older than the retention period.
     *
     * @return The number of events removed
     */
    public long cleanupOldAlarms() {
        // Calculate the cutoff timestamp
        Instant cutoff = Instant.now().minus(retentionDays, ChronoUnit.DAYS);
        
        // Remove events older than the cutoff from the global history
        Long count = redisTemplate.opsForZSet().removeRangeByScore(
                ALARM_HISTORY_KEY, 
                0, 
                cutoff.toEpochMilli());
        
        log.info("Cleaned up {} old alarm events", count);
        return count != null ? count : 0;
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
} 