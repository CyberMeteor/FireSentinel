package com.firesentinel.alarmsystem.service;

import com.firesentinel.alarmsystem.model.AlarmEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Service for distributing alarm notifications through multiple channels.
 * This service acts as a facade for different notification mechanisms like WebSocket and MQTT.
 */
@Service
public class AlarmDistributionService {
    private static final Logger logger = LoggerFactory.getLogger(AlarmDistributionService.class);
    
    private final AlarmNotificationService webSocketService;
    private final MqttAlarmService mqttService;
    
    /**
     * Constructs a new AlarmDistributionService with the required dependencies.
     * 
     * @param webSocketService The WebSocket notification service
     * @param mqttService The MQTT notification service
     */
    public AlarmDistributionService(AlarmNotificationService webSocketService, MqttAlarmService mqttService) {
        this.webSocketService = webSocketService;
        this.mqttService = mqttService;
    }
    
    /**
     * Distributes an alarm event to all configured notification channels.
     * Currently supports WebSocket and MQTT.
     * 
     * @param alarmEvent The alarm event to distribute
     */
    public void distributeAlarm(AlarmEvent alarmEvent) {
        try {
            // Send via WebSocket
            webSocketService.sendAlarmNotification(alarmEvent);
            
            // Send via MQTT
            mqttService.sendAlarmNotification(alarmEvent);
            
            logger.info("Distributed alarm notification: {} via all channels", alarmEvent.getId());
        } catch (Exception e) {
            logger.error("Failed to distribute alarm notification: {}", e.getMessage(), e);
        }
    }
} 