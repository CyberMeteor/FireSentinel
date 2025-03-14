package com.firesentinel.dataprocessing.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Model class representing a sensor data event.
 * Contains data from various sensors like temperature, smoke, CO, etc.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SensorDataEvent {

    private String deviceId;
    private LocalDateTime timestamp;
    private String sensorType;
    private Double value;
    private String unit;
    private String location;
    private Double latitude;
    private Double longitude;
    private String buildingId;
    private String floorId;
    private String roomId;
    private String metadata;
} 