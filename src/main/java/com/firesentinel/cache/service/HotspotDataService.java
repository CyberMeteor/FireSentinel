package com.firesentinel.cache.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Service for managing hotspot data with strong consistency guarantees.
 * Uses Redisson locks to ensure data consistency in a distributed environment.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HotspotDataService {

    private final RedissonClient redissonClient;
    
    @Value("${cache.lock.wait-time-ms:5000}")
    private long lockWaitTimeMs;
    
    @Value("${cache.lock.lease-time-ms:10000}")
    private long lockLeaseTimeMs;
    
    private static final String DEVICE_COUNTER_MAP = "device:counters";
    private static final String LOCK_PREFIX = "lock:device:";
    
    /**
     * Increments a counter for a device with strong consistency guarantees.
     * Uses a distributed lock to ensure that only one thread can increment the counter at a time.
     *
     * @param deviceId The device ID
     * @param counterName The counter name
     * @param incrementBy The amount to increment by
     * @return The new counter value
     */
    public long incrementCounter(String deviceId, String counterName, long incrementBy) {
        String lockKey = LOCK_PREFIX + deviceId + ":" + counterName;
        String counterKey = deviceId + ":" + counterName;
        
        return executeWithLock(lockKey, () -> {
            // Get the distributed map for device counters
            RMap<String, Long> counterMap = redissonClient.getMap(DEVICE_COUNTER_MAP);
            
            // Get the current counter value
            Long currentValue = counterMap.getOrDefault(counterKey, 0L);
            
            // Increment the counter
            long newValue = currentValue + incrementBy;
            counterMap.put(counterKey, newValue);
            
            log.debug("Incremented counter {} for device {} by {} to {}", 
                    counterName, deviceId, incrementBy, newValue);
            
            return newValue;
        });
    }
    
    /**
     * Gets a counter value for a device.
     * No lock is needed for read operations as they are atomic in Redis.
     *
     * @param deviceId The device ID
     * @param counterName The counter name
     * @return The counter value
     */
    public long getCounter(String deviceId, String counterName) {
        String counterKey = deviceId + ":" + counterName;
        RMap<String, Long> counterMap = redissonClient.getMap(DEVICE_COUNTER_MAP);
        return counterMap.getOrDefault(counterKey, 0L);
    }
    
    /**
     * Updates a counter for a device with strong consistency guarantees.
     * Uses a distributed lock to ensure that only one thread can update the counter at a time.
     *
     * @param deviceId The device ID
     * @param counterName The counter name
     * @param newValue The new counter value
     */
    public void updateCounter(String deviceId, String counterName, long newValue) {
        String lockKey = LOCK_PREFIX + deviceId + ":" + counterName;
        
        executeWithLock(lockKey, () -> {
            // Get the distributed map for device counters
            RMap<String, Long> counterMap = redissonClient.getMap(DEVICE_COUNTER_MAP);
            
            // Update the counter
            counterMap.put(deviceId + ":" + counterName, newValue);
            
            log.debug("Updated counter {} for device {} to {}", 
                    counterName, deviceId, newValue);
            
            return null;
        });
    }
    
    /**
     * Executes a function with a distributed lock.
     * If the lock cannot be acquired within the wait time, falls back to a default value.
     *
     * @param lockKey The lock key
     * @param supplier The function to execute
     * @param <T> The return type
     * @return The result of the function
     */
    private <T> T executeWithLock(String lockKey, Supplier<T> supplier) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        
        try {
            // Try to acquire the lock
            locked = lock.tryLock(lockWaitTimeMs, lockLeaseTimeMs, TimeUnit.MILLISECONDS);
            
            if (locked) {
                // If lock acquired, execute the function
                return supplier.get();
            } else {
                // If lock not acquired, log a warning and return null
                log.warn("Failed to acquire lock for key: {}", lockKey);
                throw new RuntimeException("Failed to acquire lock for key: " + lockKey);
            }
        } catch (InterruptedException e) {
            log.error("Lock acquisition interrupted for key: {}", lockKey, e);
            Thread.currentThread().interrupt();
            throw new RuntimeException("Lock acquisition interrupted for key: " + lockKey, e);
        } finally {
            // Release the lock if it was acquired
            if (locked) {
                lock.unlock();
            }
        }
    }
    
    /**
     * Executes a function with a distributed lock, with a fallback value if the lock cannot be acquired.
     *
     * @param lockKey The lock key
     * @param supplier The function to execute
     * @param fallback The fallback value
     * @param <T> The return type
     * @return The result of the function or the fallback value
     */
    public <T> T executeWithLockAndFallback(String lockKey, Supplier<T> supplier, T fallback) {
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        
        try {
            // Try to acquire the lock
            locked = lock.tryLock(lockWaitTimeMs, lockLeaseTimeMs, TimeUnit.MILLISECONDS);
            
            if (locked) {
                // If lock acquired, execute the function
                return supplier.get();
            } else {
                // If lock not acquired, log a warning and return the fallback value
                log.warn("Failed to acquire lock for key: {}, using fallback value", lockKey);
                return fallback;
            }
        } catch (InterruptedException e) {
            log.error("Lock acquisition interrupted for key: {}, using fallback value", lockKey, e);
            Thread.currentThread().interrupt();
            return fallback;
        } finally {
            // Release the lock if it was acquired
            if (locked) {
                lock.unlock();
            }
        }
    }
} 