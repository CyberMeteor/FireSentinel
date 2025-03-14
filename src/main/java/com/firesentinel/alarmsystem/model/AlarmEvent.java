package com.firesentinel.alarmsystem.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

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
    private LocalDateTime timestamp;
    private AlarmSeverity severity;
    private String alarmType;
    private String description;
    private String location;
    private Double latitude;
    private Double longitude;
    private String buildingId;
    private String floorId;
    private String roomId;
    private boolean acknowledged;
    private LocalDateTime acknowledgedAt;
    private String acknowledgedBy;
    private boolean resolved;
    private LocalDateTime resolvedAt;
    private String resolvedBy;
    private String metadata;

    /**
     * Enum representing the severity levels of an alarm.
     */
    public enum AlarmSeverity {
        INFO,
        WARNING,
        MINOR,
        MAJOR,
        CRITICAL
    }
} 