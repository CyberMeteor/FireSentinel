package com.firesentinel.alarmsystem.controller;

import com.firesentinel.alarmsystem.model.AlarmEvent;
import com.firesentinel.alarmsystem.service.AlarmHistoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for accessing alarm history.
 * Provides endpoints for retrieving alarm events with various filtering and pagination options.
 */
@RestController
@RequestMapping("/api/alarm-history")
@RequiredArgsConstructor
@Slf4j
public class AlarmHistoryController {

    private final AlarmHistoryService alarmHistoryService;
    
    /**
     * Gets the most recent alarm events.
     *
     * @param count The maximum number of events to retrieve (default: 10)
     * @return A list of alarm events, ordered by timestamp (most recent first)
     */
    @GetMapping("/recent")
    public ResponseEntity<List<AlarmEvent>> getRecentAlarms(
            @RequestParam(defaultValue = "10") int count) {
        
        List<AlarmEvent> alarms = alarmHistoryService.getRecentAlarms(count);
        return ResponseEntity.ok(alarms);
    }
    
    /**
     * Gets alarm events within a time window.
     *
     * @param startTime The start time of the window
     * @param endTime The end time of the window
     * @return A list of alarm events within the time window, ordered by timestamp
     */
    @GetMapping("/time-window")
    public ResponseEntity<List<AlarmEvent>> getAlarmsInTimeWindow(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant startTime,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant endTime) {
        
        List<AlarmEvent> alarms = alarmHistoryService.getAlarmsInTimeWindow(startTime, endTime);
        return ResponseEntity.ok(alarms);
    }
    
    /**
     * Gets alarm events for a specific device.
     *
     * @param deviceId The device ID
     * @param count The maximum number of events to retrieve (default: 10)
     * @return A list of alarm events for the device, ordered by timestamp (most recent first)
     */
    @GetMapping("/device/{deviceId}")
    public ResponseEntity<List<AlarmEvent>> getAlarmsByDevice(
            @PathVariable String deviceId,
            @RequestParam(defaultValue = "10") int count) {
        
        List<AlarmEvent> alarms = alarmHistoryService.getAlarmsByDevice(deviceId, count);
        return ResponseEntity.ok(alarms);
    }
    
    /**
     * Gets alarm events for a specific severity.
     *
     * @param severity The severity level (HIGH, MEDIUM, LOW)
     * @param count The maximum number of events to retrieve (default: 10)
     * @return A list of alarm events with the specified severity, ordered by timestamp (most recent first)
     */
    @GetMapping("/severity/{severity}")
    public ResponseEntity<List<AlarmEvent>> getAlarmsBySeverity(
            @PathVariable String severity,
            @RequestParam(defaultValue = "10") int count) {
        
        List<AlarmEvent> alarms = alarmHistoryService.getAlarmsBySeverity(severity, count);
        return ResponseEntity.ok(alarms);
    }
    
    /**
     * Gets alarm events for a specific type.
     *
     * @param alarmType The alarm type (FIRE, SMOKE, GAS, etc.)
     * @param count The maximum number of events to retrieve (default: 10)
     * @return A list of alarm events with the specified type, ordered by timestamp (most recent first)
     */
    @GetMapping("/type/{alarmType}")
    public ResponseEntity<List<AlarmEvent>> getAlarmsByType(
            @PathVariable String alarmType,
            @RequestParam(defaultValue = "10") int count) {
        
        List<AlarmEvent> alarms = alarmHistoryService.getAlarmsByType(alarmType, count);
        return ResponseEntity.ok(alarms);
    }
    
    /**
     * Gets alarm events with pagination.
     *
     * @param page The page number (0-based, default: 0)
     * @param pageSize The number of events per page (default: 10)
     * @return A list of alarm events for the specified page
     */
    @GetMapping("/page")
    public ResponseEntity<Map<String, Object>> getAlarmsWithPagination(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        
        List<AlarmEvent> alarms = alarmHistoryService.getAlarmsWithPagination(page, pageSize);
        
        Map<String, Object> response = new HashMap<>();
        response.put("alarms", alarms);
        response.put("page", page);
        response.put("pageSize", pageSize);
        response.put("totalCount", alarmHistoryService.getAlarmCount());
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Gets the next page of alarm events based on the last event timestamp.
     * This endpoint implements cursor-based pagination, where each page contains events
     * older than the last event of the previous page.
     *
     * @param lastEventTimestamp The timestamp of the last event from the previous page
     * @param pageSize The number of events per page (default: 10)
     * @return A list of alarm events for the next page
     */
    @GetMapping("/next-page")
    public ResponseEntity<Map<String, Object>> getNextAlarmPage(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant lastEventTimestamp,
            @RequestParam(defaultValue = "10") int pageSize) {
        
        List<AlarmEvent> alarms = alarmHistoryService.getNextAlarmPage(lastEventTimestamp, pageSize);
        
        Map<String, Object> response = new HashMap<>();
        response.put("alarms", alarms);
        response.put("pageSize", pageSize);
        
        // Add the timestamp of the last event in the current page for the next page request
        if (!alarms.isEmpty()) {
            response.put("lastEventTimestamp", alarms.get(alarms.size() - 1).getTimestamp());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Gets alarm statistics.
     *
     * @return A map of alarm statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getAlarmStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // Get total count
        stats.put("totalCount", alarmHistoryService.getAlarmCount());
        
        // Get counts by severity
        Map<String, Long> severityCounts = new HashMap<>();
        severityCounts.put("HIGH", alarmHistoryService.getAlarmCountBySeverity("HIGH"));
        severityCounts.put("MEDIUM", alarmHistoryService.getAlarmCountBySeverity("MEDIUM"));
        severityCounts.put("LOW", alarmHistoryService.getAlarmCountBySeverity("LOW"));
        stats.put("severityCounts", severityCounts);
        
        // Get counts by type
        Map<String, Long> typeCounts = new HashMap<>();
        typeCounts.put("FIRE", alarmHistoryService.getAlarmCountByType("FIRE"));
        typeCounts.put("SMOKE", alarmHistoryService.getAlarmCountByType("SMOKE"));
        typeCounts.put("GAS", alarmHistoryService.getAlarmCountByType("GAS"));
        stats.put("typeCounts", typeCounts);
        
        // Get time range
        Instant oldestTimestamp = alarmHistoryService.getOldestAlarmTimestamp();
        Instant newestTimestamp = alarmHistoryService.getNewestAlarmTimestamp();
        
        if (oldestTimestamp != null && newestTimestamp != null) {
            stats.put("oldestTimestamp", oldestTimestamp);
            stats.put("newestTimestamp", newestTimestamp);
            stats.put("timeRangeSeconds", newestTimestamp.getEpochSecond() - oldestTimestamp.getEpochSecond());
        }
        
        return ResponseEntity.ok(stats);
    }
    
    /**
     * Cleans up old alarm events.
     * This endpoint removes events older than the retention period.
     *
     * @return The number of events removed
     */
    @DeleteMapping("/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupOldAlarms() {
        long removedCount = alarmHistoryService.cleanupOldAlarms();
        
        Map<String, Object> response = new HashMap<>();
        response.put("removedCount", removedCount);
        response.put("remainingCount", alarmHistoryService.getAlarmCount());
        
        return ResponseEntity.ok(response);
    }
} 