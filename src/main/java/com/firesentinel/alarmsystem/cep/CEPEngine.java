package com.firesentinel.alarmsystem.cep;

import com.espertech.esper.common.client.EPCompiled;
import com.espertech.esper.common.client.EventBean;
import com.espertech.esper.common.client.configuration.Configuration;
import com.espertech.esper.compiler.client.CompilerArguments;
import com.espertech.esper.compiler.client.EPCompileException;
import com.espertech.esper.compiler.client.EPCompiler;
import com.espertech.esper.compiler.client.EPCompilerProvider;
import com.espertech.esper.runtime.client.*;
import com.firesentinel.alarmsystem.model.AlarmRule;
import com.firesentinel.alarmsystem.service.RuleEngineService;
import com.firesentinel.dataprocessing.model.SensorData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Complex Event Processing (CEP) engine for analyzing sensor data streams.
 * Uses Esper for event processing and pattern detection.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CEPEngine {

    private final RuleEngineService ruleEngineService;
    
    private EPRuntime runtime;
    private final Map<String, EPDeployment> deployments = new ConcurrentHashMap<>();
    private final Map<String, String> deploymentIdToRuleId = new ConcurrentHashMap<>();
    
    /**
     * Initializes the CEP engine.
     */
    @PostConstruct
    public void init() {
        log.info("Initializing CEP Engine");
        
        // Create and configure the Esper runtime
        Configuration configuration = new Configuration();
        configuration.getCommon().addEventType(SensorData.class);
        
        // Create the Esper runtime
        runtime = EPRuntimeProvider.getDefaultRuntime(configuration);
        
        // Load all rules from the rule engine
        loadAllRules();
        
        log.info("CEP Engine initialized with {} rules", deployments.size());
    }
    
    /**
     * Loads all rules from the rule engine.
     */
    public void loadAllRules() {
        // Get all rules from the rule engine
        Map<String, AlarmRule> rules = ruleEngineService.getAllRules();
        
        // Deploy each rule
        for (Map.Entry<String, AlarmRule> entry : rules.entrySet()) {
            deployRule(entry.getKey(), entry.getValue());
        }
    }
    
    /**
     * Deploys a rule to the CEP engine.
     *
     * @param ruleId The rule ID
     * @param rule The rule to deploy
     * @return The deployment ID
     */
    public String deployRule(String ruleId, AlarmRule rule) {
        try {
            // Create the EPL statement
            String epl = createEPLFromRule(rule);
            
            // Compile the EPL
            EPCompiler compiler = EPCompilerProvider.getCompiler();
            CompilerArguments args = new CompilerArguments(runtime.getConfigurationDeepCopy());
            EPCompiled compiled = compiler.compile(epl, args);
            
            // Deploy the compiled EPL
            EPDeployment deployment = runtime.getDeploymentService().deploy(compiled);
            String deploymentId = deployment.getDeploymentId();
            
            // Store the deployment
            deployments.put(deploymentId, deployment);
            deploymentIdToRuleId.put(deploymentId, ruleId);
            
            // Add a listener to the statement
            for (EPStatement statement : deployment.getStatements()) {
                statement.addListener((newEvents, oldEvents, statement1, runtime) -> {
                    if (newEvents != null) {
                        for (EventBean event : newEvents) {
                            handleAlarmEvent(rule, event.getUnderlying());
                        }
                    }
                });
            }
            
            log.info("Deployed rule: {} with deployment ID: {}", ruleId, deploymentId);
            return deploymentId;
            
        } catch (EPCompileException | EPDeployException e) {
            log.error("Failed to deploy rule: {}", ruleId, e);
            return null;
        }
    }
    
    /**
     * Undeploys a rule from the CEP engine.
     *
     * @param deploymentId The deployment ID
     */
    public void undeployRule(String deploymentId) {
        try {
            runtime.getDeploymentService().undeploy(deploymentId);
            deployments.remove(deploymentId);
            deploymentIdToRuleId.remove(deploymentId);
            log.info("Undeployed rule with deployment ID: {}", deploymentId);
        } catch (Exception e) {
            log.error("Failed to undeploy rule: {}", deploymentId, e);
        }
    }
    
    /**
     * Updates a rule in the CEP engine.
     *
     * @param ruleId The rule ID
     * @param rule The updated rule
     * @return The new deployment ID
     */
    public String updateRule(String ruleId, AlarmRule rule) {
        // Find the deployment ID for the rule
        String deploymentId = null;
        for (Map.Entry<String, String> entry : deploymentIdToRuleId.entrySet()) {
            if (entry.getValue().equals(ruleId)) {
                deploymentId = entry.getKey();
                break;
            }
        }
        
        // Undeploy the old rule if it exists
        if (deploymentId != null) {
            undeployRule(deploymentId);
        }
        
        // Deploy the updated rule
        return deployRule(ruleId, rule);
    }
    
    /**
     * Processes a sensor data event.
     *
     * @param sensorData The sensor data event
     */
    public void processSensorData(SensorData sensorData) {
        try {
            runtime.getEventService().sendEventBean(sensorData, SensorData.class.getSimpleName());
            log.debug("Processed sensor data: {}", sensorData);
        } catch (Exception e) {
            log.error("Failed to process sensor data: {}", sensorData, e);
        }
    }
    
    /**
     * Creates an EPL statement from a rule.
     *
     * @param rule The rule
     * @return The EPL statement
     */
    private String createEPLFromRule(AlarmRule rule) {
        StringBuilder epl = new StringBuilder();
        
        // Create the select clause
        epl.append("@Name('").append(rule.getName()).append("') ");
        epl.append("select * from SensorData ");
        
        // Create the where clause
        epl.append("where deviceId = '").append(rule.getDeviceId()).append("' ");
        epl.append("and sensorType = '").append(rule.getSensorType()).append("' ");
        
        // Add the threshold condition
        switch (rule.getOperator()) {
            case ">" -> epl.append("and value > ").append(rule.getThreshold()).append(" ");
            case ">=" -> epl.append("and value >= ").append(rule.getThreshold()).append(" ");
            case "<" -> epl.append("and value < ").append(rule.getThreshold()).append(" ");
            case "<=" -> epl.append("and value <= ").append(rule.getThreshold()).append(" ");
            case "==" -> epl.append("and value = ").append(rule.getThreshold()).append(" ");
            case "!=" -> epl.append("and value != ").append(rule.getThreshold()).append(" ");
            default -> throw new IllegalArgumentException("Invalid operator: " + rule.getOperator());
        }
        
        // Add time window if specified
        if (rule.getTimeWindowSeconds() > 0) {
            epl.append("output first every ").append(rule.getTimeWindowSeconds()).append(" seconds ");
        }
        
        return epl.toString();
    }
    
    /**
     * Handles an alarm event.
     *
     * @param rule The rule that triggered the alarm
     * @param event The event that triggered the alarm
     */
    private void handleAlarmEvent(AlarmRule rule, Object event) {
        if (event instanceof SensorData sensorData) {
            log.info("Alarm triggered: {} for device: {}, sensor: {}, value: {}, threshold: {}",
                    rule.getName(), sensorData.getDeviceId(), sensorData.getSensorType(),
                    sensorData.getValue(), rule.getThreshold());
            
            // Notify the rule engine of the alarm
            ruleEngineService.handleAlarmEvent(rule, sensorData);
        }
    }
    
    /**
     * Destroys the CEP engine.
     */
    @PreDestroy
    public void destroy() {
        if (runtime != null) {
            runtime.destroy();
        }
    }
} 