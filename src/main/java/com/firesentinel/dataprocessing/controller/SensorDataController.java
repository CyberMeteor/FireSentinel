package com.firesentinel.dataprocessing.controller;

import com.firesentinel.dataprocessing.model.SensorData;
import com.firesentinel.dataprocessing.service.SensorDataConsumerService;
import com.firesentinel.dataprocessing.service.SensorDataProducerService;
import com.firesentinel.dataprocessing.service.TimescaleDBService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.support.SendResult;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Controller for sensor data processing.
 * Provides endpoints for sending sensor data to Kafka and retrieving statistics.
 */
@RestController
@RequestMapping("/api/sensor-data")
@RequiredArgsConstructor
@Slf4j
public class SensorDataController {

    private final SensorDataProducerService producerService;
    private final SensorDataConsumerService consumerService;
    private final TimescaleDBService timescaleDBService;

    /**
     * Sends sensor data to Kafka.
     *
     * @param sensorData The sensor data to send
     * @return A response with the status of the operation
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> sendSensorData(@RequestBody SensorData sensorData) {
        try {
            CompletableFuture<SendResult<String, SensorData>> future = producerService.sendSensorData(sensorData);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "accepted");
            response.put("message", "Sensor data sent to Kafka");
            
            return ResponseEntity.accepted().body(response);
        } catch (Exception e) {
            log.error("Error sending sensor data to Kafka", e);
            
            Map<String, String> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to send sensor data: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Sends multiple sensor data records to Kafka.
     *
     * @param sensorDataList The list of sensor data to send
     * @return A response with the status of the operation
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> sendSensorDataBatch(@RequestBody List<SensorData> sensorDataList) {
        try {
            int count = 0;
            for (SensorData sensorData : sensorDataList) {
                producerService.sendSensorData(sensorData);
                count++;
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "accepted");
            response.put("message", "Sensor data batch sent to Kafka");
            response.put("count", count);
            
            return ResponseEntity.accepted().body(response);
        } catch (Exception e) {
            log.error("Error sending sensor data batch to Kafka", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", "error");
            response.put("message", "Failed to send sensor data batch: " + e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }
    
    /**
     * Gets statistics about the Kafka producer and consumer.
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
    
    /**
     * Gets sensor data for a device.
     *
     * @param deviceId The device ID
     * @return A list of sensor data
     */
    @GetMapping("/device/{deviceId}")
    public ResponseEntity<List<SensorData>> getSensorDataForDevice(@PathVariable String deviceId) {
        List<SensorData> sensorDataList = timescaleDBService.getSensorDataForDevice(deviceId);
        return ResponseEntity.ok(sensorDataList);
    }
    
    /**
     * Gets sensor data for a device and sensor type.
     *
     * @param deviceId The device ID
     * @param sensorType The sensor type
     * @return A list of sensor data
     */
    @GetMapping("/device/{deviceId}/type/{sensorType}")
    public ResponseEntity<List<SensorData>> getSensorDataForDeviceAndType(
            @PathVariable String deviceId,
            @PathVariable String sensorType) {
        List<SensorData> sensorDataList = timescaleDBService.getSensorDataForDeviceAndType(deviceId, sensorType);
        return ResponseEntity.ok(sensorDataList);
    }
    
    /**
     * Gets sensor data for a device within a time range.
     *
     * @param deviceId The device ID
     * @param startTime The start time (ISO-8601 format)
     * @param endTime The end time (ISO-8601 format)
     * @return A list of sensor data
     */
    @GetMapping("/device/{deviceId}/range")
    public ResponseEntity<List<SensorData>> getSensorDataForDeviceInTimeRange(
            @PathVariable String deviceId,
            @RequestParam String startTime,
            @RequestParam String endTime) {
        Instant start = Instant.parse(startTime);
        Instant end = Instant.parse(endTime);
        
        List<SensorData> sensorDataList = timescaleDBService.getSensorDataForDeviceInTimeRange(deviceId, start, end);
        return ResponseEntity.ok(sensorDataList);
    }
    
    /**
     * Gets aggregated data for a device and sensor type.
     *
     * @param deviceId The device ID
     * @param sensorType The sensor type
     * @param interval The interval (e.g., '1 hour', '1 day')
     * @param startTime The start time (ISO-8601 format)
     * @param endTime The end time (ISO-8601 format)
     * @return Aggregated data
     */
    @GetMapping("/device/{deviceId}/type/{sensorType}/aggregate")
    public ResponseEntity<List<Map<String, Object>>> getAggregatedData(
            @PathVariable String deviceId,
            @PathVariable String sensorType,
            @RequestParam String interval,
            @RequestParam String startTime,
            @RequestParam String endTime) {
        Instant start = Instant.parse(startTime);
        Instant end = Instant.parse(endTime);
        
        List<Map<String, Object>> aggregatedData = timescaleDBService.getAggregatedData(
                deviceId, sensorType, interval, start, end);
        return ResponseEntity.ok(aggregatedData);
    }
} 