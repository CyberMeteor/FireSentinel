package com.firesentinel.alarmsystem.controller;

import com.firesentinel.alarmsystem.model.Device;
import com.firesentinel.alarmsystem.service.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for device operations.
 */
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@Slf4j
public class DeviceController {
    
    private final DeviceService deviceService;
    
    /**
     * Gets all devices.
     *
     * @return A response containing all devices
     */
    @GetMapping
    public ResponseEntity<List<Device>> getAllDevices() {
        log.debug("Received request for all devices");
        
        // Initialize sample devices if needed
        deviceService.initSampleDevices();
        
        List<Device> devices = deviceService.getAllDevices();
        return ResponseEntity.ok(devices);
    }
    
    /**
     * Gets a device by ID.
     *
     * @param id The device ID
     * @return A response containing the device, or 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<Device> getDeviceById(@PathVariable String id) {
        log.debug("Received request for device: {}", id);
        
        return deviceService.getDeviceById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Gets devices by floor ID.
     *
     * @param floorId The floor ID
     * @return A response containing the devices on the specified floor
     */
    @GetMapping("/floor/{floorId}")
    public ResponseEntity<List<Device>> getDevicesByFloor(@PathVariable String floorId) {
        log.debug("Received request for devices on floor: {}", floorId);
        
        List<Device> devices = deviceService.getDevicesByFloor(floorId);
        return ResponseEntity.ok(devices);
    }
    
    /**
     * Gets devices by zone ID.
     *
     * @param zoneId The zone ID
     * @return A response containing the devices in the specified zone
     */
    @GetMapping("/zone/{zoneId}")
    public ResponseEntity<List<Device>> getDevicesByZone(@PathVariable String zoneId) {
        log.debug("Received request for devices in zone: {}", zoneId);
        
        List<Device> devices = deviceService.getDevicesByZone(zoneId);
        return ResponseEntity.ok(devices);
    }
    
    /**
     * Gets active devices.
     *
     * @return A response containing the active devices
     */
    @GetMapping("/active")
    public ResponseEntity<List<Device>> getActiveDevices() {
        log.debug("Received request for active devices");
        
        List<Device> devices = deviceService.getActiveDevices();
        return ResponseEntity.ok(devices);
    }
    
    /**
     * Updates a device.
     *
     * @param id The device ID
     * @param device The device to update
     * @return A response containing the updated device, or 404 if not found
     */
    @PutMapping("/{id}")
    public ResponseEntity<Device> updateDevice(@PathVariable String id, @RequestBody Device device) {
        log.debug("Received request to update device: {}", id);
        
        // Ensure the ID in the path matches the ID in the body
        if (!id.equals(device.getId())) {
            return ResponseEntity.badRequest().build();
        }
        
        // Check if the device exists
        if (deviceService.getDeviceById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        Device updatedDevice = deviceService.updateDevice(device);
        return ResponseEntity.ok(updatedDevice);
    }
    
    /**
     * Updates the status of a device.
     *
     * @param id The device ID
     * @param active Whether the device is active
     * @return A response containing the updated device, or 404 if not found
     */
    @PatchMapping("/{id}/status")
    public ResponseEntity<Device> updateDeviceStatus(
            @PathVariable String id,
            @RequestParam boolean active) {
        
        log.debug("Received request to update device status: {} -> {}", id, active);
        
        return deviceService.updateDeviceStatus(id, active)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
} 