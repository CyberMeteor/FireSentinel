package com.firesentinel.cache.service;

import com.firesentinel.deviceauth.model.Device;
import com.firesentinel.deviceauth.repository.DeviceRepository;
import com.google.common.hash.BloomFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service that uses a Bloom Filter to efficiently check for valid device IDs.
 * The Bloom Filter is used to quickly determine if a device ID is definitely not in the database,
 * avoiding unnecessary database queries for invalid device IDs.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceBloomFilterService {

    private final DeviceRepository deviceRepository;
    private final BloomFilter<String> deviceBloomFilter;
    
    // Statistics counters
    private final AtomicInteger totalQueries = new AtomicInteger(0);
    private final AtomicInteger filteredQueries = new AtomicInteger(0);
    
    /**
     * Initializes the Bloom Filter with all existing device IDs.
     */
    @PostConstruct
    public void initializeBloomFilter() {
        log.info("Initializing device Bloom Filter...");
        List<Device> allDevices = deviceRepository.findAll();
        
        for (Device device : allDevices) {
            deviceBloomFilter.put(device.getDeviceId());
        }
        
        log.info("Bloom Filter initialized with {} device IDs", allDevices.size());
    }
    
    /**
     * Refreshes the Bloom Filter periodically to account for new devices.
     * This is scheduled to run every hour.
     */
    @Scheduled(fixedRateString = "${cache.bloom-filter.refresh-interval-ms:3600000}")
    public void refreshBloomFilter() {
        log.info("Refreshing device Bloom Filter...");
        
        // In a production environment, you might want to use a more efficient approach
        // such as tracking new devices since the last refresh, or using a time-based filter
        initializeBloomFilter();
        
        // Reset statistics
        totalQueries.set(0);
        filteredQueries.set(0);
    }
    
    /**
     * Adds a device ID to the Bloom Filter.
     *
     * @param deviceId The device ID to add
     */
    public void addDeviceId(String deviceId) {
        deviceBloomFilter.put(deviceId);
        log.debug("Added device ID to Bloom Filter: {}", deviceId);
    }
    
    /**
     * Checks if a device ID might exist in the database.
     * This is a probabilistic check - false positives are possible, but false negatives are not.
     * In other words, if this returns false, the device ID definitely does not exist in the database.
     *
     * @param deviceId The device ID to check
     * @return true if the device ID might exist, false if it definitely does not exist
     */
    public boolean mightExist(String deviceId) {
        totalQueries.incrementAndGet();
        
        boolean mightExist = deviceBloomFilter.mightContain(deviceId);
        if (!mightExist) {
            filteredQueries.incrementAndGet();
            log.debug("Bloom Filter rejected device ID: {}", deviceId);
        }
        
        return mightExist;
    }
    
    /**
     * Gets the current filter rate (percentage of queries filtered out).
     *
     * @return The filter rate as a percentage
     */
    public double getFilterRate() {
        int total = totalQueries.get();
        if (total == 0) {
            return 0.0;
        }
        
        return (double) filteredQueries.get() / total * 100.0;
    }
    
    /**
     * Gets statistics about the Bloom Filter usage.
     *
     * @return A string with statistics
     */
    public String getStatistics() {
        return String.format(
                "Total queries: %d, Filtered queries: %d, Filter rate: %.2f%%",
                totalQueries.get(),
                filteredQueries.get(),
                getFilterRate()
        );
    }
} 