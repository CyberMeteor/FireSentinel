package com.firesentinel.dataprocessing.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Service for fire suppression operations.
 * Uses Redis Lua scripts for atomic execution.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FireSuppressionService {

    private final RedisTemplate<String, Object> redisTemplate;
    
    private RedisScript<Boolean> activateSuppressionScript;
    private RedisScript<Map<Object, Object>> getDeviceStatusScript;
    private RedisScript<Long> incrementSuppressionCounterScript;
    
    /**
     * Initializes the Redis Lua scripts.
     */
    @PostConstruct
    public void init() {
        try {
            // Load the Lua scripts from resources
            String activateSuppressionLua = loadScriptFromResource("scripts/activate_suppression.lua");
            String getDeviceStatusLua = loadScriptFromResource("scripts/get_device_status.lua");
            String incrementSuppressionCounterLua = loadScriptFromResource("scripts/increment_suppression_counter.lua");
            
            // Create the Redis scripts
            activateSuppressionScript = new DefaultRedisScript<>(activateSuppressionLua, Boolean.class);
            getDeviceStatusScript = new DefaultRedisScript<>(getDeviceStatusLua, (Class<Map<Object, Object>>)(Class<?>) Map.class);
            incrementSuppressionCounterScript = new DefaultRedisScript<>(incrementSuppressionCounterLua, Long.class);
            
            log.info("Initialized Redis Lua scripts for fire suppression");
        } catch (IOException e) {
            log.error("Failed to load Redis Lua scripts", e);
        }
    }
    
    /**
     * Activates fire suppression for a device.
     * This is an atomic operation that:
     * 1. Checks if the device is enabled and connected
     * 2. Checks if suppression is already active
     * 3. Activates suppression if conditions are met
     * 4. Updates counters and logs
     *
     * @param deviceId The device ID
     * @param zoneId The zone ID
     * @param suppressionType The suppression type (e.g., "water", "foam", "gas")
     * @param intensity The suppression intensity (0-100)
     * @return true if suppression was activated, false otherwise
     */
    public boolean activateSuppression(String deviceId, String zoneId, String suppressionType, int intensity) {
        // Execute the Lua script
        Boolean result = redisTemplate.execute(
                activateSuppressionScript,
                Collections.singletonList("device:" + deviceId),
                zoneId,
                suppressionType,
                intensity,
                Instant.now().toEpochMilli()
        );
        
        if (Boolean.TRUE.equals(result)) {
            log.info("Activated {} suppression for device {} in zone {} with intensity {}",
                    suppressionType, deviceId, zoneId, intensity);
        } else {
            log.warn("Failed to activate suppression for device {}", deviceId);
        }
        
        return Boolean.TRUE.equals(result);
    }
    
    /**
     * Gets the status of a device.
     *
     * @param deviceId The device ID
     * @return The device status
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getDeviceStatus(String deviceId) {
        Map<Object, Object> result = redisTemplate.execute(
                getDeviceStatusScript,
                Collections.singletonList("device:" + deviceId)
        );
        
        // Convert the result to a Map<String, Object>
        return result != null ? result.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        entry -> entry.getKey().toString(),
                        Map.Entry::getValue
                )) : Collections.emptyMap();
    }
    
    /**
     * Increments the suppression counter for a device.
     *
     * @param deviceId The device ID
     * @param suppressionType The suppression type
     * @return The new counter value
     */
    public long incrementSuppressionCounter(String deviceId, String suppressionType) {
        Long result = redisTemplate.execute(
                incrementSuppressionCounterScript,
                Collections.singletonList("device:" + deviceId + ":counters"),
                suppressionType,
                Instant.now().toEpochMilli()
        );
        
        return result != null ? result : 0;
    }
    
    /**
     * Loads a Lua script from a resource file.
     *
     * @param resourcePath The resource path
     * @return The script content
     * @throws IOException If the script cannot be loaded
     */
    private String loadScriptFromResource(String resourcePath) throws IOException {
        ClassPathResource resource = new ClassPathResource(resourcePath);
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }
} 