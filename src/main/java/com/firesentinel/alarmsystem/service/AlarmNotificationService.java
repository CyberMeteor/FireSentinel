package com.firesentinel.alarmsystem.service;

import com.firesentinel.alarmsystem.model.AlarmEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Service for sending alarm notifications through WebSocket.
 * This service handles the distribution of alarm events to connected clients.
 */
@Service
public class AlarmNotificationService {
    private static final Logger logger = LoggerFactory.getLogger(AlarmNotificationService.class);
    
    private final SimpMessagingTemplate messagingTemplate;
    
    @Value("${websocket.topic.prefix:/topic/alarm/}")
    private String topicPrefix;
    
    /**
     * Constructs a new AlarmNotificationService with the required dependencies.
     * 
     * @param messagingTemplate The SimpMessagingTemplate for sending WebSocket messages
     */
    public AlarmNotificationService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }
    
    /**
     * Sends an alarm event notification to all connected clients.
     * The notification is sent to both a general topic and a severity-specific topic.
     * 
     * @param alarmEvent The alarm event to send
     */
    public void sendAlarmNotification(AlarmEvent alarmEvent) {
        try {
            // Send to the general alarm topic
            messagingTemplate.convertAndSend(topicPrefix + "all", alarmEvent);
            
            // Send to the severity-specific topic
            String severityTopic = topicPrefix + alarmEvent.getSeverity().toString().toLowerCase();
            messagingTemplate.convertAndSend(severityTopic, alarmEvent);
            
            logger.info("Sent alarm notification: {} to topics: {} and {}", 
                    alarmEvent.getId(), 
                    topicPrefix + "all", 
                    severityTopic);
        } catch (Exception e) {
            logger.error("Failed to send alarm notification: {}", e.getMessage(), e);
        }
    }
} 