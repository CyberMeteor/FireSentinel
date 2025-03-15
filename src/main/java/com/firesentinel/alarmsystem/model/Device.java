package com.firesentinel.alarmsystem.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Represents a device in the FireSentinel system.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Device {
    
    /**
     * The unique identifier of the device.
     */
    private String id;
    
    /**
     * The name of the device.
     */
    private String name;
    
    /**
     * The type of the device (e.g., smoke detector, temperature sensor).
     */
    private String type;
    
    /**
     * The location of the device.
     */
    private DeviceLocation location;
    
    /**
     * Whether the device is active.
     */
    private boolean active;
    
    /**
     * The last time the device was updated.
     */
    private Instant lastUpdated;
    
    /**
     * The floor ID where the device is located.
     */
    private String floorId;
    
    /**
     * The zone ID where the device is located.
     */
    private String zoneId;
    
    /**
     * Represents the location of a device in 3D space.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeviceLocation {
        private double x;
        private double y;
        private double z;
    }
} 