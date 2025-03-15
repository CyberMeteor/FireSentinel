package com.firesentinel.cache.controller;

import com.firesentinel.cache.service.DeviceBloomFilterService;
import com.firesentinel.cache.service.DeviceCacheService;
import com.firesentinel.cache.service.HotspotDataService;
import com.firesentinel.deviceauth.model.Device;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * Controller for demonstrating the cache services.
 */
@RestController
@RequestMapping("/api/cache")
@RequiredArgsConstructor
@Slf4j
public class CacheController {

    private final DeviceCacheService deviceCacheService;
    private final DeviceBloomFilterService bloomFilterService;
    private final HotspotDataService hotspotDataService;
    
    /**
     * Gets a device by its ID, using the cache.
     *
     * @param deviceId The device ID
     * @return The device if found
     */
    @GetMapping("/device/{deviceId}")
    public ResponseEntity<Device> getDevice(@PathVariable String deviceId) {
        log.info("Getting device: {}", deviceId);
        Optional<Device> device = deviceCacheService.getDeviceById(deviceId);
        return device.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Gets cache statistics.
     *
     * @return The cache statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, String>> getCacheStats() {
        log.info("Getting cache statistics");
        return ResponseEntity.ok(Map.of(
                "cacheStats", deviceCacheService.getCacheStatistics(),
                "bloomFilterStats", bloomFilterService.getStatistics()
        ));
    }
    
    /**
     * Increments a counter for a device.
     *
     * @param deviceId The device ID
     * @param counterName The counter name
     * @param incrementBy The amount to increment by
     * @return The new counter value
     */
    @PostMapping("/device/{deviceId}/counter/{counterName}/increment")
    public ResponseEntity<Map<String, Long>> incrementCounter(
            @PathVariable String deviceId,
            @PathVariable String counterName,
            @RequestParam(defaultValue = "1") long incrementBy) {
        
        log.info("Incrementing counter {} for device {} by {}", counterName, deviceId, incrementBy);
        
        // Check if the device exists first
        if (!bloomFilterService.mightExist(deviceId)) {
            return ResponseEntity.notFound().build();
        }
        
        // Increment the counter with strong consistency
        long newValue = hotspotDataService.incrementCounter(deviceId, counterName, incrementBy);
        
        return ResponseEntity.ok(Map.of(
                "deviceId", Long.parseLong(deviceId),
                "counterName", (long) counterName.hashCode(),
                "value", newValue
        ));
    }
    
    /**
     * Gets a counter value for a device.
     *
     * @param deviceId The device ID
     * @param counterName The counter name
     * @return The counter value
     */
    @GetMapping("/device/{deviceId}/counter/{counterName}")
    public ResponseEntity<Map<String, Long>> getCounter(
            @PathVariable String deviceId,
            @PathVariable String counterName) {
        
        log.info("Getting counter {} for device {}", counterName, deviceId);
        
        // Check if the device exists first
        if (!bloomFilterService.mightExist(deviceId)) {
            return ResponseEntity.notFound().build();
        }
        
        // Get the counter value
        long value = hotspotDataService.getCounter(deviceId, counterName);
        
        return ResponseEntity.ok(Map.of(
                "deviceId", Long.parseLong(deviceId),
                "counterName", (long) counterName.hashCode(),
                "value", value
        ));
    }
    
    /**
     * Updates a counter for a device.
     *
     * @param deviceId The device ID
     * @param counterName The counter name
     * @param newValue The new counter value
     * @return A success response
     */
    @PutMapping("/device/{deviceId}/counter/{counterName}")
    public ResponseEntity<Map<String, String>> updateCounter(
            @PathVariable String deviceId,
            @PathVariable String counterName,
            @RequestParam long newValue) {
        
        log.info("Updating counter {} for device {} to {}", counterName, deviceId, newValue);
        
        // Check if the device exists first
        if (!bloomFilterService.mightExist(deviceId)) {
            return ResponseEntity.notFound().build();
        }
        
        // Update the counter with strong consistency
        hotspotDataService.updateCounter(deviceId, counterName, newValue);
        
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Counter updated successfully"
        ));
    }
    
    /**
     * Evicts a device from the cache.
     *
     * @param deviceId The device ID
     * @return A success response
     */
    @DeleteMapping("/device/{deviceId}")
    public ResponseEntity<Map<String, String>> evictDevice(@PathVariable String deviceId) {
        log.info("Evicting device from cache: {}", deviceId);
        deviceCacheService.evictDevice(deviceId);
        return ResponseEntity.ok(Map.of(
                "status", "success",
                "message", "Device evicted from cache"
        ));
    }
} 