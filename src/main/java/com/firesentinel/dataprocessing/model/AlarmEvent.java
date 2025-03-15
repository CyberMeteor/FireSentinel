package com.firesentinel.dataprocessing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Model class for alarm events.
 * These events are published to Kafka when sensor data indicates a potential fire.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmEvent {

    private Long id; // Snowflake ID
    
    private String deviceId;
    
    private String alarmType; // FIRE, SMOKE, GAS, etc.
    
    private String severity; // HIGH, MEDIUM, LOW
    
    private Double value; // The sensor value that triggered the alarm
    
    private String unit; // The unit of measurement
    
    private Instant timestamp; // When the alarm was triggered
    
    private Double locationX; // X coordinate
    
    private Double locationY; // Y coordinate
    
    private Double locationZ; // Z coordinate
    
    private String buildingId; // Building identifier
    
    private String floorId; // Floor identifier
    
    private String roomId; // Room identifier
    
    private String zoneId; // Zone identifier
    
    private Boolean acknowledged; // Whether the alarm has been acknowledged
    
    private Instant acknowledgedAt; // When the alarm was acknowledged
    
    private String acknowledgedBy; // Who acknowledged the alarm
    
    private Boolean resolved; // Whether the alarm has been resolved
    
    private Instant resolvedAt; // When the alarm was resolved
    
    private String resolvedBy; // Who resolved the alarm
    
    private String notes; // Additional notes
    
    private String metadata; // Additional metadata as JSON
} 