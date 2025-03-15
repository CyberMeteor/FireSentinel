package com.firesentinel.alarmsystem.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Model class representing an alarm event.
 * Generated when sensor data indicates a potential fire or hazardous condition.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmEvent {

    private String id;
    private String deviceId;
    private Instant timestamp;
    private AlarmSeverity severity;
    private String alarmType;
    private String message;
    private Double value;
    private String unit;
    
    // Location information
    private Double locationX;
    private Double locationY;
    private Double locationZ;
    private String buildingId;
    private String floorId;
    private String roomId;
    private String zoneId;
    
    // Status information
    private boolean acknowledged;
    private Instant acknowledgedAt;
    private String acknowledgedBy;
    private boolean resolved;
    private Instant resolvedAt;
    private String resolvedBy;
    
    // Additional information
    private String notes;
    private String metadata;
} 