package com.firesentinel.alarmsystem.controller;

import com.firesentinel.alarmsystem.cep.CEPEngine;
import com.firesentinel.alarmsystem.model.AlarmRule;
import com.firesentinel.alarmsystem.service.DeduplicationService;
import com.firesentinel.alarmsystem.service.RuleEngineService;
import com.firesentinel.dataprocessing.model.SensorData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller for the alarm system.
 * Provides endpoints for managing rules and thresholds.
 */
@RestController
@RequestMapping("/api/alarm-system")
@RequiredArgsConstructor
@Slf4j
public class AlarmSystemController {

    private final RuleEngineService ruleEngineService;
    private final CEPEngine cepEngine;
    private final DeduplicationService deduplicationService;
    
    /**
     * Gets all rules.
     *
     * @return A map of rule IDs to rules
     */
    @GetMapping("/rules")
    public ResponseEntity<Map<String, AlarmRule>> getAllRules() {
        return ResponseEntity.ok(ruleEngineService.getAllRules());
    }
    
    /**
     * Gets a rule by ID.
     *
     * @param ruleId The rule ID
     * @return The rule
     */
    @GetMapping("/rules/{ruleId}")
    public ResponseEntity<AlarmRule> getRule(@PathVariable String ruleId) {
        AlarmRule rule = ruleEngineService.getRule(ruleId);
        if (rule != null) {
            return ResponseEntity.ok(rule);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Creates a new rule.
     *
     * @param rule The rule to create
     * @return The created rule ID
     */
    @PostMapping("/rules")
    public ResponseEntity<Map<String, String>> createRule(@RequestBody AlarmRule rule) {
        String ruleId = ruleEngineService.createRule(rule);
        
        // Deploy the rule to the CEP engine
        cepEngine.deployRule(ruleId, rule);
        
        Map<String, String> response = new HashMap<>();
        response.put("id", ruleId);
        response.put("status", "created");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Updates a rule.
     *
     * @param ruleId The rule ID
     * @param rule The updated rule
     * @return The updated rule ID
     */
    @PutMapping("/rules/{ruleId}")
    public ResponseEntity<Map<String, String>> updateRule(@PathVariable String ruleId, @RequestBody AlarmRule rule) {
        String updatedRuleId = ruleEngineService.updateRule(ruleId, rule);
        
        // Update the rule in the CEP engine
        cepEngine.updateRule(ruleId, rule);
        
        Map<String, String> response = new HashMap<>();
        response.put("id", updatedRuleId);
        response.put("status", "updated");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Deletes a rule.
     *
     * @param ruleId The rule ID
     * @return A success message
     */
    @DeleteMapping("/rules/{ruleId}")
    public ResponseEntity<Map<String, String>> deleteRule(@PathVariable String ruleId) {
        ruleEngineService.deleteRule(ruleId);
        
        // Find and undeploy the rule from the CEP engine
        // This is handled by the CEP engine internally
        
        Map<String, String> response = new HashMap<>();
        response.put("id", ruleId);
        response.put("status", "deleted");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Updates a threshold value.
     *
     * @param deviceId The device ID
     * @param sensorType The sensor type
     * @param threshold The new threshold value
     * @return A success message
     */
    @PutMapping("/thresholds/{deviceId}/{sensorType}")
    public ResponseEntity<Map<String, Object>> updateThreshold(
            @PathVariable String deviceId,
            @PathVariable String sensorType,
            @RequestParam double threshold) {
        
        boolean updated = ruleEngineService.updateThreshold(deviceId, sensorType, threshold);
        
        // Reload all rules in the CEP engine
        cepEngine.loadAllRules();
        
        Map<String, Object> response = new HashMap<>();
        response.put("deviceId", deviceId);
        response.put("sensorType", sensorType);
        response.put("threshold", threshold);
        response.put("status", updated ? "updated" : "failed");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Gets a threshold value.
     *
     * @param deviceId The device ID
     * @param sensorType The sensor type
     * @return The threshold value
     */
    @GetMapping("/thresholds/{deviceId}/{sensorType}")
    public ResponseEntity<Map<String, Object>> getThreshold(
            @PathVariable String deviceId,
            @PathVariable String sensorType) {
        
        Double threshold = ruleEngineService.getThreshold(deviceId, sensorType);
        
        if (threshold != null) {
            Map<String, Object> response = new HashMap<>();
            response.put("deviceId", deviceId);
            response.put("sensorType", sensorType);
            response.put("threshold", threshold);
            
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.notFound().build();
        }
    }
    
    /**
     * Processes a sensor data event.
     *
     * @param sensorData The sensor data event
     * @return A success message
     */
    @PostMapping("/process")
    public ResponseEntity<Map<String, String>> processSensorData(@RequestBody SensorData sensorData) {
        // Process the sensor data in the CEP engine
        cepEngine.processSensorData(sensorData);
        
        Map<String, String> response = new HashMap<>();
        response.put("status", "processed");
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Gets deduplication statistics.
     *
     * @return Deduplication statistics
     */
    @GetMapping("/stats/deduplication")
    public ResponseEntity<Map<String, Object>> getDeduplicationStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalEvents", deduplicationService.getTotalEventCount());
        stats.put("deduplicationRate", deduplicationService.getDeduplicationRate());
        
        return ResponseEntity.ok(stats);
    }
} 