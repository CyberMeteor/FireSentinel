package com.firesentinel.alarmsystem.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firesentinel.alarmsystem.model.AlarmRule;
import com.firesentinel.dataprocessing.model.AlarmEvent;
import com.firesentinel.dataprocessing.model.SensorData;
import com.firesentinel.dataprocessing.service.AlarmEventProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing alarm rules and thresholds.
 * Uses Redis for fast storage and retrieval of rules and thresholds.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RuleEngineService {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    private final AlarmEventProducerService alarmEventProducerService;
    private final DeduplicationService deduplicationService;
    private final NotificationService notificationService;
    
    // Local cache of rules for faster access
    private final Map<String, AlarmRule> rulesCache = new ConcurrentHashMap<>();
    
    // Redis key prefixes
    private static final String RULE_KEY_PREFIX = "alarm:rule:";
    private static final String THRESHOLD_KEY_PREFIX = "alarm:threshold:";
    
    /**
     * Initializes the rule engine.
     */
    @PostConstruct
    public void init() {
        log.info("Initializing Rule Engine");
        
        // Load all rules from Redis
        loadAllRulesFromRedis();
        
        log.info("Rule Engine initialized with {} rules", rulesCache.size());
    }
    
    /**
     * Loads all rules from Redis.
     */
    private void loadAllRulesFromRedis() {
        try {
            // Get all rule keys
            Set<String> keys = redisTemplate.keys(RULE_KEY_PREFIX + "*");
            
            if (keys != null) {
                for (String key : keys) {
                    String ruleJson = redisTemplate.opsForValue().get(key);
                    if (ruleJson != null) {
                        AlarmRule rule = objectMapper.readValue(ruleJson, AlarmRule.class);
                        String ruleId = key.substring(RULE_KEY_PREFIX.length());
                        rulesCache.put(ruleId, rule);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to load rules from Redis", e);
        }
    }
    
    /**
     * Gets all rules.
     *
     * @return A map of rule IDs to rules
     */
    public Map<String, AlarmRule> getAllRules() {
        return new HashMap<>(rulesCache);
    }
    
    /**
     * Gets a rule by ID.
     *
     * @param ruleId The rule ID
     * @return The rule, or null if not found
     */
    public AlarmRule getRule(String ruleId) {
        return rulesCache.get(ruleId);
    }
    
    /**
     * Creates a new rule.
     *
     * @param rule The rule to create
     * @return The rule ID
     */
    public String createRule(AlarmRule rule) {
        String ruleId = UUID.randomUUID().toString();
        return saveRule(ruleId, rule);
    }
    
    /**
     * Updates a rule.
     *
     * @param ruleId The rule ID
     * @param rule The updated rule
     * @return The rule ID
     */
    public String updateRule(String ruleId, AlarmRule rule) {
        return saveRule(ruleId, rule);
    }
    
    /**
     * Saves a rule to Redis and the local cache.
     *
     * @param ruleId The rule ID
     * @param rule The rule to save
     * @return The rule ID
     */
    private String saveRule(String ruleId, AlarmRule rule) {
        try {
            // Save to Redis
            String ruleJson = objectMapper.writeValueAsString(rule);
            redisTemplate.opsForValue().set(RULE_KEY_PREFIX + ruleId, ruleJson);
            
            // Save to local cache
            rulesCache.put(ruleId, rule);
            
            // Save the threshold separately for faster access
            redisTemplate.opsForValue().set(
                    THRESHOLD_KEY_PREFIX + rule.getDeviceId() + ":" + rule.getSensorType(),
                    String.valueOf(rule.getThreshold()));
            
            log.info("Saved rule: {} with ID: {}", rule.getName(), ruleId);
            return ruleId;
            
        } catch (JsonProcessingException e) {
            log.error("Failed to save rule: {}", ruleId, e);
            return null;
        }
    }
    
    /**
     * Deletes a rule.
     *
     * @param ruleId The rule ID
     */
    public void deleteRule(String ruleId) {
        // Get the rule
        AlarmRule rule = rulesCache.get(ruleId);
        
        if (rule != null) {
            // Remove from Redis
            redisTemplate.delete(RULE_KEY_PREFIX + ruleId);
            
            // Remove from local cache
            rulesCache.remove(ruleId);
            
            log.info("Deleted rule: {} with ID: {}", rule.getName(), ruleId);
        }
    }
    
    /**
     * Updates a threshold value.
     * This is optimized for fast updates (<200ms).
     *
     * @param deviceId The device ID
     * @param sensorType The sensor type
     * @param threshold The new threshold value
     * @return True if the threshold was updated, false otherwise
     */
    public boolean updateThreshold(String deviceId, String sensorType, double threshold) {
        try {
            // Update the threshold in Redis
            String key = THRESHOLD_KEY_PREFIX + deviceId + ":" + sensorType;
            redisTemplate.opsForValue().set(key, String.valueOf(threshold));
            
            // Update the threshold in all matching rules in the local cache
            for (Map.Entry<String, AlarmRule> entry : rulesCache.entrySet()) {
                AlarmRule rule = entry.getValue();
                if (rule.getDeviceId().equals(deviceId) && rule.getSensorType().equals(sensorType)) {
                    rule.setThreshold(threshold);
                    
                    // Update the rule in Redis
                    saveRule(entry.getKey(), rule);
                }
            }
            
            log.info("Updated threshold for device: {}, sensor: {} to {}", deviceId, sensorType, threshold);
            return true;
            
        } catch (Exception e) {
            log.error("Failed to update threshold for device: {}, sensor: {}", deviceId, sensorType, e);
            return false;
        }
    }
    
    /**
     * Gets a threshold value.
     *
     * @param deviceId The device ID
     * @param sensorType The sensor type
     * @return The threshold value, or null if not found
     */
    public Double getThreshold(String deviceId, String sensorType) {
        try {
            // Get the threshold from Redis
            String key = THRESHOLD_KEY_PREFIX + deviceId + ":" + sensorType;
            String thresholdStr = redisTemplate.opsForValue().get(key);
            
            if (thresholdStr != null) {
                return Double.parseDouble(thresholdStr);
            }
            
            return null;
            
        } catch (Exception e) {
            log.error("Failed to get threshold for device: {}, sensor: {}", deviceId, sensorType, e);
            return null;
        }
    }
    
    /**
     * Handles an alarm event.
     *
     * @param rule The rule that triggered the alarm
     * @param sensorData The sensor data that triggered the alarm
     */
    public void handleAlarmEvent(AlarmRule rule, SensorData sensorData) {
        // Check if this is a duplicate alarm
        String eventKey = rule.getId() + ":" + sensorData.getDeviceId() + ":" + sensorData.getSensorType();
        
        if (!deduplicationService.isNewEvent(eventKey)) {
            log.debug("Duplicate alarm event detected, skipping: {}", eventKey);
            return;
        }
        
        // Create and send the alarm event
        alarmEventProducerService.createAndSendAlarmEvent(
                sensorData,
                rule.getAlarmType(),
                rule.getSeverity(),
                rule.getBuildingId(),
                rule.getFloorId(),
                rule.getRoomId(),
                rule.getZoneId(),
                "Triggered by rule: " + rule.getName(),
                rule.getMetadata()
        );
        
        // Send notification
        notificationService.sendAlarmNotification(rule, sensorData);
        
        log.info("Handled alarm event for rule: {}, device: {}, sensor: {}, value: {}",
                rule.getName(), sensorData.getDeviceId(), sensorData.getSensorType(), sensorData.getValue());
    }
} 