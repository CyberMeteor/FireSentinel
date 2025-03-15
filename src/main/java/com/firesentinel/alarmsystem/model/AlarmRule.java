package com.firesentinel.alarmsystem.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Model class for alarm rules.
 * These rules define the conditions for triggering alarms based on sensor data.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmRule {

    private String id;
    
    private String name;
    
    private String deviceId;
    
    private String sensorType;
    
    private String operator; // >, >=, <, <=, ==, !=
    
    private double threshold;
    
    private int timeWindowSeconds; // Time window for the rule (0 for no window)
    
    private String severity; // HIGH, MEDIUM, LOW
    
    private String alarmType; // FIRE, SMOKE, GAS, etc.
    
    private String buildingId;
    
    private String floorId;
    
    private String roomId;
    
    private String zoneId;
    
    private boolean enabled;
    
    private String metadata; // Additional metadata as JSON
} 