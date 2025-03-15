package com.firesentinel.alarmsystem.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Model class for alarm notifications.
 * These notifications are sent via WebSocket and MQTT when an alarm is triggered.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlarmNotification {

    private String id;
    
    private Instant timestamp;
    
    private String ruleId;
    
    private String ruleName;
    
    private String deviceId;
    
    private String sensorType;
    
    private Double value;
    
    private Double threshold;
    
    private String operator;
    
    private String alarmType;
    
    private String severity;
    
    private String buildingId;
    
    private String floorId;
    
    private String roomId;
    
    private String zoneId;
    
    private Double locationX;
    
    private Double locationY;
    
    private Double locationZ;
} 