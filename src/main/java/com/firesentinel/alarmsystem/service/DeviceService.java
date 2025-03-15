package com.firesentinel.alarmsystem.service;

import com.firesentinel.alarmsystem.model.Device;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for managing devices in the FireSentinel system.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceService {
    
    private final SimpMessagingTemplate webSocketTemplate;
    
    // In-memory store of devices (in a real application, this would be a database)
    private final Map<String, Device> devices = new ConcurrentHashMap<>();
    
    /**
     * Initializes the service with some sample devices.
     */
    public void initSampleDevices() {
        if (devices.isEmpty()) {
            // Create sample devices for demonstration
            createSampleDevices();
        }
    }
    
    /**
     * Gets all devices.
     *
     * @return A list of all devices
     */
    public List<Device> getAllDevices() {
        return new ArrayList<>(devices.values());
    }
    
    /**
     * Gets a device by ID.
     *
     * @param id The device ID
     * @return The device, or empty if not found
     */
    public Optional<Device> getDeviceById(String id) {
        return Optional.ofNullable(devices.get(id));
    }
    
    /**
     * Gets devices by floor ID.
     *
     * @param floorId The floor ID
     * @return A list of devices on the specified floor
     */
    public List<Device> getDevicesByFloor(String floorId) {
        return devices.values().stream()
                .filter(device -> floorId.equals(device.getFloorId()))
                .collect(Collectors.toList());
    }
    
    /**
     * Gets devices by zone ID.
     *
     * @param zoneId The zone ID
     * @return A list of devices in the specified zone
     */
    public List<Device> getDevicesByZone(String zoneId) {
        return devices.values().stream()
                .filter(device -> zoneId.equals(device.getZoneId()))
                .collect(Collectors.toList());
    }
    
    /**
     * Gets active devices.
     *
     * @return A list of active devices
     */
    public List<Device> getActiveDevices() {
        return devices.values().stream()
                .filter(Device::isActive)
                .collect(Collectors.toList());
    }
    
    /**
     * Updates a device.
     *
     * @param device The device to update
     * @return The updated device
     */
    public Device updateDevice(Device device) {
        device.setLastUpdated(Instant.now());
        devices.put(device.getId(), device);
        
        // Notify clients of the device update
        webSocketTemplate.convertAndSend("/topic/device/updates", device);
        
        log.debug("Updated device: {}", device.getId());
        return device;
    }
    
    /**
     * Updates the status of a device.
     *
     * @param deviceId The device ID
     * @param active Whether the device is active
     * @return The updated device, or empty if not found
     */
    public Optional<Device> updateDeviceStatus(String deviceId, boolean active) {
        return getDeviceById(deviceId).map(device -> {
            device.setActive(active);
            device.setLastUpdated(Instant.now());
            
            // Notify clients of the device update
            webSocketTemplate.convertAndSend("/topic/device/updates", device);
            
            log.debug("Updated device status: {} -> {}", deviceId, active);
            return device;
        });
    }
    
    /**
     * Creates sample devices for demonstration.
     */
    private void createSampleDevices() {
        // Create devices for floor 1
        createDevicesForFloor("floor-1", 1, 10);
        
        // Create devices for floor 2
        createDevicesForFloor("floor-2", 2, 15);
        
        // Create devices for floor 3
        createDevicesForFloor("floor-3", 3, 8);
        
        log.info("Created {} sample devices", devices.size());
    }
    
    /**
     * Creates devices for a floor.
     *
     * @param floorId The floor ID
     * @param floorNumber The floor number
     * @param count The number of devices to create
     */
    private void createDevicesForFloor(String floorId, int floorNumber, int count) {
        String[] deviceTypes = {"smoke", "temperature", "fire", "motion"};
        String[] zones = {"north", "south", "east", "west", "central"};
        
        for (int i = 0; i < count; i++) {
            String deviceId = "device-" + floorId + "-" + i;
            String deviceType = deviceTypes[i % deviceTypes.length];
            String zoneName = zones[i % zones.length];
            String zoneId = floorId + "-" + zoneName;
            
            // Generate random position on the floor
            double x = (Math.random() * 40) - 20;
            double z = (Math.random() * 30) - 15;
            
            Device device = Device.builder()
                    .id(deviceId)
                    .name(deviceType.substring(0, 1).toUpperCase() + deviceType.substring(1) + " Sensor " + i)
                    .type(deviceType)
                    .location(Device.DeviceLocation.builder()
                            .x(x)
                            .y(floorNumber * 5) // 5 units per floor
                            .z(z)
                            .build())
                    .active(Math.random() > 0.2) // 80% chance of being active
                    .lastUpdated(Instant.now())
                    .floorId(floorId)
                    .zoneId(zoneId)
                    .build();
            
            devices.put(deviceId, device);
        }
    }
} 