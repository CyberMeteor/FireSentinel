package com.firesentinel.dataprocessing.service;

import com.firesentinel.dataprocessing.model.SensorData;
import com.firesentinel.dataprocessing.repository.SensorDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for consuming sensor data from Kafka.
 * Implements backpressure handling by using different consumer groups
 * with different concurrency settings.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SensorDataConsumerService {

    private final SensorDataRepository sensorDataRepository;
    private final TimescaleDBService timescaleDBService;
    
    // Statistics counters
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong batchesProcessed = new AtomicLong(0);
    private final AtomicLong errorsEncountered = new AtomicLong(0);
    
    /**
     * Main consumer for sensor data.
     * Uses higher concurrency for normal operation.
     *
     * @param sensorData The sensor data payload
     * @param partition The Kafka partition
     * @param offset The Kafka offset
     * @param timestamp The Kafka timestamp
     */
    @KafkaListener(
            topics = "${spring.kafka.topics.sensor-data}",
            groupId = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeSensorData(
            @Payload SensorData sensorData,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        try {
            log.debug("Received sensor data: deviceId={}, sensorType={}, partition={}, offset={}",
                    sensorData.getDeviceId(), sensorData.getSensorType(), partition, offset);
            
            // Process and save the sensor data
            sensorDataRepository.save(sensorData);
            
            // Update statistics
            totalProcessed.incrementAndGet();
            
        } catch (Exception e) {
            errorsEncountered.incrementAndGet();
            log.error("Error processing sensor data: deviceId={}, sensorType={}, partition={}, offset={}",
                    sensorData.getDeviceId(), sensorData.getSensorType(), partition, offset, e);
        }
    }
    
    /**
     * Batch consumer for sensor data with backpressure handling.
     * Uses lower concurrency and smaller batch sizes to handle high load situations.
     *
     * @param sensorDataList The list of sensor data payloads
     */
    @KafkaListener(
            topics = "${spring.kafka.topics.sensor-data}",
            groupId = "${spring.kafka.consumer.backpressure-group-id}",
            containerFactory = "backpressureKafkaListenerContainerFactory"
    )
    public void consumeSensorDataWithBackpressure(
            @Payload List<SensorData> sensorDataList) {
        
        if (sensorDataList.isEmpty()) {
            return;
        }
        
        try {
            log.debug("Received batch of {} sensor data records for backpressure processing", 
                    sensorDataList.size());
            
            // Use batch insert for better performance
            timescaleDBService.batchInsertSensorData(sensorDataList);
            
            // Update statistics
            totalProcessed.addAndGet(sensorDataList.size());
            batchesProcessed.incrementAndGet();
            
            log.debug("Successfully processed batch of {} sensor data records", sensorDataList.size());
            
        } catch (Exception e) {
            errorsEncountered.incrementAndGet();
            log.error("Error processing sensor data batch of size {}", sensorDataList.size(), e);
        }
    }
    
    /**
     * Gets statistics about the consumer.
     *
     * @return A string with statistics
     */
    public String getStatistics() {
        return String.format(
                "Total processed: %d, Batches processed: %d, Errors: %d, Avg batch size: %.2f",
                totalProcessed.get(),
                batchesProcessed.get(),
                errorsEncountered.get(),
                batchesProcessed.get() > 0 ? (double) totalProcessed.get() / batchesProcessed.get() : 0
        );
    }
} 