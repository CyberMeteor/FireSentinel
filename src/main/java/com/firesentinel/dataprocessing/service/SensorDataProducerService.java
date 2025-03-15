package com.firesentinel.dataprocessing.service;

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
 * Service for producing sensor data to Kafka.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SensorDataProducerService {

    private final KafkaTemplate<String, SensorData> kafkaTemplate;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    
    @Value("${spring.kafka.topics.sensor-data}")
    private String sensorDataTopic;
    
    // Statistics counters
    private final AtomicLong totalMessages = new AtomicLong(0);
    private final AtomicLong successfulMessages = new AtomicLong(0);
    private final AtomicLong failedMessages = new AtomicLong(0);
    
    /**
     * Sends sensor data to Kafka.
     * Uses the device ID as the partition key to ensure that data from the same device
     * goes to the same partition, preserving order.
     *
     * @param sensorData The sensor data to send
     * @return A CompletableFuture for the send result
     */
    public CompletableFuture<SendResult<String, SensorData>> sendSensorData(SensorData sensorData) {
        totalMessages.incrementAndGet();
        
        // Use the device ID as the partition key
        String key = sensorData.getDeviceId();
        
        // Send the message
        CompletableFuture<SendResult<String, SensorData>> future = kafkaTemplate.send(sensorDataTopic, key, sensorData);
        
        // Add callbacks for success and failure
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                // Success
                successfulMessages.incrementAndGet();
                log.debug("Sent sensor data to Kafka: deviceId={}, sensorType={}, partition={}, offset={}",
                        sensorData.getDeviceId(), sensorData.getSensorType(),
                        result.getRecordMetadata().partition(), result.getRecordMetadata().offset());
            } else {
                // Failure
                failedMessages.incrementAndGet();
                log.error("Failed to send sensor data to Kafka: deviceId={}, sensorType={}",
                        sensorData.getDeviceId(), sensorData.getSensorType(), ex);
            }
        });
        
        return future;
    }
    
    /**
     * Creates and sends sensor data to Kafka.
     *
     * @param deviceId The device ID
     * @param deviceTypeId The device type ID
     * @param sensorType The sensor type
     * @param value The sensor value
     * @param unit The unit of measurement
     * @param timestamp The timestamp
     * @param locationX The X coordinate
     * @param locationY The Y coordinate
     * @param locationZ The Z coordinate
     * @param metadata Additional metadata
     * @return A CompletableFuture for the send result
     */
    public CompletableFuture<SendResult<String, SensorData>> sendSensorData(
            String deviceId,
            int deviceTypeId,
            String sensorType,
            double value,
            String unit,
            Instant timestamp,
            Double locationX,
            Double locationY,
            Double locationZ,
            String metadata) {
        
        // Generate a Snowflake ID
        long id = snowflakeIdGenerator.generateId(deviceTypeId);
        
        // Create the sensor data entity
        SensorData sensorData = SensorData.builder()
                .id(id)
                .deviceId(deviceId)
                .sensorType(sensorType)
                .value(value)
                .unit(unit)
                .timestamp(timestamp)
                .locationX(locationX)
                .locationY(locationY)
                .locationZ(locationZ)
                .metadata(metadata)
                .build();
        
        // Send the sensor data
        return sendSensorData(sensorData);
    }
    
    /**
     * Gets statistics about the producer.
     *
     * @return A string with statistics
     */
    public String getStatistics() {
        return String.format(
                "Total messages: %d, Successful: %d, Failed: %d, Success rate: %.2f%%",
                totalMessages.get(),
                successfulMessages.get(),
                failedMessages.get(),
                totalMessages.get() > 0 ? (double) successfulMessages.get() / totalMessages.get() * 100 : 0
        );
    }
} 