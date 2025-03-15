package com.firesentinel.dataprocessing.controller;

import com.firesentinel.dataprocessing.model.AlarmEvent;
import com.firesentinel.dataprocessing.model.SensorData;
import com.firesentinel.dataprocessing.service.AlarmEventConsumerService;
import com.firesentinel.dataprocessing.service.AlarmEventProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.support.SendResult;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for alarm events.
 * Provides endpoints for sending alarm events to Kafka and managing active alarms.
 */
@RestController
@RequestMapping("/api/alarms")
@RequiredArgsConstructor
@Slf4j
public class AlarmEventController {

    private final AlarmEventProducerService producerService;
    private final AlarmEventConsumerService consumerService;

    /**
     * Sends an alarm event to Kafka.
     *
     * @param alarmEvent The alarm event to send
     * @return A response with the status of the operation
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> sendAlarmEvent(@RequestBody AlarmEvent alarmEvent) {
        try {
            CompletableFuture<SendResult<String, AlarmEvent>> future = producerService.sendAlarmEvent(alarmEvent);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "accepted");
            response.put("message", "Alarm event sent to Kafka");
            
            return ResponseEntity.accepted().body(response);
        } catch (Exception e) {
            log.error("Error sending alarm event to Kafka", e);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to send alarm event: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Creates and sends an alarm event based on sensor data.
     *
     * @param sensorData The sensor data that triggered the alarm
     * @param alarmType The type of alarm (FIRE, SMOKE, GAS, etc.)
     * @param severity The severity of the alarm (HIGH, MEDIUM, LOW)
     * @param buildingId The building identifier
     * @param floorId The floor identifier
     * @param roomId The room identifier
     * @param zoneId The zone identifier
     * @param notes Additional notes
     * @param metadata Additional metadata as JSON
     * @return A response with the status of the operation
     */
    @PostMapping("/from-sensor")
    public ResponseEntity<Map<String, String>> createAlarmFromSensor(
            @RequestBody SensorData sensorData,
            @RequestParam String alarmType,
            @RequestParam String severity,
            @RequestParam(required = false) String buildingId,
            @RequestParam(required = false) String floorId,
            @RequestParam(required = false) String roomId,
            @RequestParam(required = false) String zoneId,
            @RequestParam(required = false) String notes,
            @RequestParam(required = false) String metadata) {
        
        try {
            CompletableFuture<SendResult<String, AlarmEvent>> future = producerService.createAndSendAlarmEvent(
                    sensorData, alarmType, severity, buildingId, floorId, roomId, zoneId, notes, metadata);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "accepted");
            response.put("message", "Alarm event created and sent to Kafka");
            
            return ResponseEntity.accepted().body(response);
        } catch (Exception e) {
            log.error("Error creating and sending alarm event to Kafka", e);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to create and send alarm event: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Gets all active alarms.
     *
     * @return A list of all active alarms
     */
    @GetMapping
    public ResponseEntity<List<AlarmEvent>> getAllActiveAlarms() {
        List<AlarmEvent> alarms = consumerService.getAllActiveAlarms();
        return ResponseEntity.ok(alarms);
    }
    
    /**
     * Gets active alarms for a device.
     *
     * @param deviceId The device ID
     * @return A list of active alarms for the device
     */
    @GetMapping("/device/{deviceId}")
    public ResponseEntity<List<AlarmEvent>> getActiveAlarmsForDevice(@PathVariable String deviceId) {
        List<AlarmEvent> alarms = consumerService.getActiveAlarmsForDevice(deviceId);
        return ResponseEntity.ok(alarms);
    }
    
    /**
     * Acknowledges an alarm.
     *
     * @param alarmId The alarm ID
     * @param acknowledgedBy Who acknowledged the alarm
     * @return A response with the status of the operation
     */
    @PostMapping("/{alarmId}/acknowledge")
    public ResponseEntity<Map<String, String>> acknowledgeAlarm(
            @PathVariable Long alarmId,
            @RequestParam String acknowledgedBy) {
        
        boolean acknowledged = consumerService.acknowledgeAlarm(alarmId, acknowledgedBy);
        
        Map<String, String> response = new HashMap<>();
        if (acknowledged) {
            response.put("status", "success");
            response.put("message", "Alarm acknowledged");
            return ResponseEntity.ok(response);
        } else {
            response.put("status", "error");
            response.put("message", "Alarm not found or already acknowledged");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }
    
    /**
     * Resolves an alarm.
     *
     * @param alarmId The alarm ID
     * @param resolvedBy Who resolved the alarm
     * @return A response with the status of the operation
     */
    @PostMapping("/{alarmId}/resolve")
    public ResponseEntity<Map<String, String>> resolveAlarm(
            @PathVariable Long alarmId,
            @RequestParam String resolvedBy) {
        
        boolean resolved = consumerService.resolveAlarm(alarmId, resolvedBy);
        
        Map<String, String> response = new HashMap<>();
        if (resolved) {
            response.put("status", "success");
            response.put("message", "Alarm resolved");
            return ResponseEntity.ok(response);
        } else {
            response.put("status", "error");
            response.put("message", "Alarm not found or already resolved");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
    }
    
    /**
     * Gets statistics about the alarm event producer and consumer.
     *
     * @return A response with statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, String>> getStatistics() {
        Map<String, String> stats = new HashMap<>();
        stats.put("producer", producerService.getStatistics());
        stats.put("consumer", consumerService.getStatistics());
        
        return ResponseEntity.ok(stats);
    }
} 