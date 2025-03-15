package com.firesentinel.cache.service;

import com.firesentinel.deviceauth.model.Device;
import com.firesentinel.deviceauth.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Service that combines the multi-level cache with the Bloom Filter for device queries.
 * This service provides an efficient way to query devices, using the Bloom Filter to quickly
 * filter out invalid device IDs before checking the cache or database.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceCacheService {

    private final DeviceRepository deviceRepository;
    private final MultiLevelCacheService cacheService;
    private final DeviceBloomFilterService bloomFilterService;
    
    private static final String DEVICE_CACHE_PREFIX = "device:";
    
    /**
     * Gets a device by its ID, using the Bloom Filter and multi-level cache.
     * 
     * 1. First checks the Bloom Filter to see if the device ID might exist
     * 2. If it might exist, checks the cache (Caffeine -> Redis)
     * 3. If not in cache, loads from the database
     *
     * @param deviceId The device ID
     * @return An Optional containing the device if found
     */
    public Optional<Device> getDeviceById(String deviceId) {
        // First check the Bloom Filter
        if (!bloomFilterService.mightExist(deviceId)) {
            // If the Bloom Filter says the device definitely doesn't exist, return empty
            log.debug("Device ID {} definitely does not exist (Bloom Filter)", deviceId);
            return Optional.empty();
        }
        
        // If the device might exist, check the cache or load from database
        String cacheKey = DEVICE_CACHE_PREFIX + deviceId;
        Device device = cacheService.get(cacheKey, key -> {
            // This lambda is only executed on cache miss
            log.debug("Loading device {} from database", deviceId);
            return deviceRepository.findByDeviceId(deviceId).orElse(null);
        });
        
        return Optional.ofNullable(device);
    }
    
    /**
     * Saves a device, updating both the cache and Bloom Filter.
     *
     * @param device The device to save
     * @return The saved device
     */
    public Device saveDevice(Device device) {
        // Save to database
        Device savedDevice = deviceRepository.save(device);
        
        // Update cache
        String cacheKey = DEVICE_CACHE_PREFIX + savedDevice.getDeviceId();
        cacheService.put(cacheKey, savedDevice);
        
        // Update Bloom Filter
        bloomFilterService.addDeviceId(savedDevice.getDeviceId());
        
        return savedDevice;
    }
    
    /**
     * Evicts a device from the cache.
     * Note that we can't remove from the Bloom Filter, as it doesn't support removal.
     *
     * @param deviceId The device ID to evict
     */
    public void evictDevice(String deviceId) {
        String cacheKey = DEVICE_CACHE_PREFIX + deviceId;
        cacheService.evict(cacheKey);
        log.debug("Evicted device {} from cache", deviceId);
    }
    
    /**
     * Gets cache statistics.
     *
     * @return A string with cache statistics
     */
    public String getCacheStatistics() {
        return "Cache stats: " + cacheService.getCacheStats() + 
               ", Bloom Filter stats: " + bloomFilterService.getStatistics();
    }
} 