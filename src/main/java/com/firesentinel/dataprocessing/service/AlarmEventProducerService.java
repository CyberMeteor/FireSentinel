package com.firesentinel.dataprocessing.service;

import com.firesentinel.dataprocessing.model.AlarmEvent;
import com.firesentinel.dataprocessing.model.SensorData;
import com.firesentinel.dataprocessing.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for producing alarm events to Kafka.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlarmEventProducerService {

    private final KafkaTemplate<String, AlarmEvent> kafkaTemplate;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    
    @Value("${spring.kafka.topics.alarm-events}")
    private String alarmEventsTopic;
    
    // Statistics counters
    private final AtomicLong totalAlarms = new AtomicLong(0);
    private final AtomicLong successfulAlarms = new AtomicLong(0);
    private final AtomicLong failedAlarms = new AtomicLong(0);
    
    /**
     * Sends an alarm event to Kafka.
     * Uses the device ID as the partition key to ensure that alarms from the same device
     * go to the same partition, preserving order.
     *
     * @param alarmEvent The alarm event to send
     * @return A CompletableFuture for the send result
     */
    public CompletableFuture<SendResult<String, AlarmEvent>> sendAlarmEvent(AlarmEvent alarmEvent) {
        totalAlarms.incrementAndGet();
        
        // Use the device ID as the partition key
        String key = alarmEvent.getDeviceId();
        
        // Send the message
        CompletableFuture<SendResult<String, AlarmEvent>> future = kafkaTemplate.send(alarmEventsTopic, key, alarmEvent);
        
        // Add callbacks for success and failure
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                // Success
                successfulAlarms.incrementAndGet();
                log.debug("Sent alarm event to Kafka: deviceId={}, alarmType={}, partition={}, offset={}",
                        alarmEvent.getDeviceId(), alarmEvent.getAlarmType(),
                        result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            } else {
                // Failure
                failedAlarms.incrementAndGet();
                log.error("Failed to send alarm event to Kafka: deviceId={}, alarmType={}",
                        alarmEvent.getDeviceId(), alarmEvent.getAlarmType(), ex);
            }
        });
        
        return future;
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
     * @return A CompletableFuture for the send result
     */
    public CompletableFuture<SendResult<String, AlarmEvent>> createAndSendAlarmEvent(
            SensorData sensorData,
            String alarmType,
            String severity,
            String buildingId,
            String floorId,
            String roomId,
            String zoneId,
            String notes,
            String metadata) {
        
        // Generate a Snowflake ID
        long id = snowflakeIdGenerator.generateId(1); // 1 = alarm event type
        
        // Create the alarm event
        AlarmEvent alarmEvent = AlarmEvent.builder()
                .id(id)
                .deviceId(sensorData.getDeviceId())
                .alarmType(alarmType)
                .severity(severity)
                .value(sensorData.getValue())
                .unit(sensorData.getUnit())
                .timestamp(Instant.now())
                .locationX(sensorData.getLocationX())
                .locationY(sensorData.getLocationY())
                .locationZ(sensorData.getLocationZ())
                .buildingId(buildingId)
                .floorId(floorId)
                .roomId(roomId)
                .zoneId(zoneId)
                .acknowledged(false)
                .resolved(false)
                .notes(notes)
                .metadata(metadata)
                .build();
        
        // Send the alarm event
        return sendAlarmEvent(alarmEvent);
    }
    
    /**
     * Gets statistics about the producer.
     *
     * @return A string with statistics
     */
    public String getStatistics() {
        return String.format(
                "Total alarms: %d, Successful: %d, Failed: %d, Success rate: %.2f%%",
                totalAlarms.get(),
                successfulAlarms.get(),
                failedAlarms.get(),
                totalAlarms.get() > 0 ? (double) successfulAlarms.get() / totalAlarms.get() * 100 : 0
        );
    }
} 