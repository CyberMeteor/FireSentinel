package com.firesentinel.dataprocessing.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/**
 * Entity class for sensor data.
 * This is stored in a TimescaleDB hypertable.
 */
@Entity
@Table(name = "sensor_data", indexes = {
    @Index(name = "idx_sensor_data_device_id", columnList = "device_id"),
    @Index(name = "idx_sensor_data_timestamp", columnList = "timestamp"),
    @Index(name = "idx_sensor_data_sensor_type", columnList = "sensor_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensorData {

    @Id
    private Long id; // Snowflake ID
    
    @Column(name = "device_id", nullable = false)
    private String deviceId;
    
    @Column(name = "sensor_type", nullable = false)
    private String sensorType;
    
    @Column(name = "value", nullable = false)
    private Double value;
    
    @Column(name = "unit", nullable = false)
    private String unit;
    
    @Column(name = "timestamp", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private Instant timestamp;
    
    @Column(name = "location_x")
    private Double locationX;
    
    @Column(name = "location_y")
    private Double locationY;
    
    @Column(name = "location_z")
    private Double locationZ;
    
    @Column(name = "metadata", columnDefinition = "jsonb")
    private String metadata;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
} 