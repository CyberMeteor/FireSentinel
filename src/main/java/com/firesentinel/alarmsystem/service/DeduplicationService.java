package com.firesentinel.alarmsystem.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Service for deduplicating alarm events using Redis HyperLogLog.
 * HyperLogLog is a probabilistic data structure that estimates the cardinality of a set.
 * It's used here to track unique event IDs and reduce redundant alerts.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeduplicationService {

    private final RedisTemplate<String, String> redisTemplate;
    
    @Value("${alarm.deduplication.window-seconds:300}")
    private int deduplicationWindowSeconds; // Default to 5 minutes
    
    @Value("${alarm.deduplication.enabled:true}")
    private boolean deduplicationEnabled;
    
    // Redis key prefixes
    private static final String HLL_KEY_PREFIX = "alarm:hll:";
    private static final String LAST_SEEN_KEY_PREFIX = "alarm:last-seen:";
    
    /**
     * Checks if an event is new (not a duplicate).
     *
     * @param eventKey The event key
     * @return True if the event is new, false if it's a duplicate
     */
    public boolean isNewEvent(String eventKey) {
        if (!deduplicationEnabled) {
            return true;
        }
        
        try {
            // Get the current timestamp
            long now = Instant.now().getEpochSecond();
            
            // Check if the event was seen recently
            String lastSeenKey = LAST_SEEN_KEY_PREFIX + eventKey;
            String lastSeenStr = redisTemplate.opsForValue().get(lastSeenKey);
            
            if (lastSeenStr != null) {
                long lastSeen = Long.parseLong(lastSeenStr);
                if (now - lastSeen < deduplicationWindowSeconds) {
                    // Event was seen recently, it's a duplicate
                    return false;
                }
            }
            
            // Update the last seen timestamp
            redisTemplate.opsForValue().set(lastSeenKey, String.valueOf(now), deduplicationWindowSeconds, TimeUnit.SECONDS);
            
            // Add the event to the HyperLogLog
            String hllKey = HLL_KEY_PREFIX + eventKey.split(":")[0]; // Use the rule ID as the HLL key
            redisTemplate.opsForHyperLogLog().add(hllKey, eventKey + ":" + now);
            
            // Set expiration on the HLL key if it doesn't exist
            if (Boolean.TRUE.equals(redisTemplate.expire(hllKey, deduplicationWindowSeconds, TimeUnit.SECONDS))) {
                log.debug("Created new HyperLogLog for rule: {}", eventKey.split(":")[0]);
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Failed to check if event is new: {}", eventKey, e);
            // In case of error, assume it's a new event
            return true;
        }
    }
    
    /**
     * Gets the approximate count of unique events for a rule.
     *
     * @param ruleId The rule ID
     * @return The approximate count of unique events
     */
    public long getUniqueEventCount(String ruleId) {
        try {
            String hllKey = HLL_KEY_PREFIX + ruleId;
            Long count = redisTemplate.opsForHyperLogLog().size(hllKey);
            return count != null ? count : 0;
        } catch (Exception e) {
            log.error("Failed to get unique event count for rule: {}", ruleId, e);
            return 0;
        }
    }
    
    /**
     * Gets the total count of events processed.
     *
     * @return The total count of events processed
     */
    public long getTotalEventCount() {
        try {
            // Get all HLL keys
            Set<String> keys = redisTemplate.keys(HLL_KEY_PREFIX + "*");
            
            if (keys == null || keys.isEmpty()) {
                return 0;
            }
            
            // Convert the set to an array
            String[] keysArray = keys.toArray(new String[0]);
            
            // Merge all HLLs and get the count
            Long count = redisTemplate.opsForHyperLogLog().union("alarm:hll:temp", keysArray);
            
            // Delete the temporary key
            redisTemplate.delete("alarm:hll:temp");
            
            return count != null ? count : 0;
            
        } catch (Exception e) {
            log.error("Failed to get total event count", e);
            return 0;
        }
    }
    
    /**
     * Gets the deduplication rate.
     *
     * @return The deduplication rate (percentage of events that were deduplicated)
     */
    public double getDeduplicationRate() {
        try {
            // Get the total count of events processed
            long totalEvents = getTotalEventCount();
            
            if (totalEvents == 0) {
                return 0.0;
            }
            
            // Get the count of last seen events
            Set<String> keys = redisTemplate.keys(LAST_SEEN_KEY_PREFIX + "*");
            long lastSeenEvents = keys != null ? keys.size() : 0;
            
            // Calculate the deduplication rate
            return (double) (totalEvents - lastSeenEvents) / totalEvents * 100.0;
            
        } catch (Exception e) {
            log.error("Failed to get deduplication rate", e);
            return 0.0;
        }
    }
} 