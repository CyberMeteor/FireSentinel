package com.firesentinel.alarmsystem.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firesentinel.alarmsystem.model.AlarmEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.messaging.MessageChannel;

/**
 * Service for sending alarm notifications through MQTT.
 * This service handles the distribution of alarm events to MQTT topics.
 */
@Service
public class MqttAlarmService {
    private static final Logger logger = LoggerFactory.getLogger(MqttAlarmService.class);
    
    private final MessageChannel mqttOutboundChannel;
    private final ObjectMapper objectMapper;
    
    @Value("${mqtt.topic.prefix:firesentinel/alarm/}")
    private String topicPrefix;
    
    /**
     * Constructs a new MqttAlarmService with the required dependencies.
     * 
     * @param mqttOutboundChannel The message channel for outbound MQTT messages
     * @param objectMapper The object mapper for JSON serialization
     */
    public MqttAlarmService(MessageChannel mqttOutboundChannel, ObjectMapper objectMapper) {
        this.mqttOutboundChannel = mqttOutboundChannel;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Sends an alarm event notification to MQTT topics.
     * The notification is sent to both a general topic and a severity-specific topic.
     * 
     * @param alarmEvent The alarm event to send
     */
    public void sendAlarmNotification(AlarmEvent alarmEvent) {
        try {
            String payload = objectMapper.writeValueAsString(alarmEvent);
            
            // Send to the general alarm topic
            sendToTopic(payload, topicPrefix + "all");
            
            // Send to the severity-specific topic
            String severityTopic = topicPrefix + alarmEvent.getSeverity().toString().toLowerCase();
            sendToTopic(payload, severityTopic);
            
            logger.info("Sent alarm notification via MQTT: {} to topics: {} and {}", 
                    alarmEvent.getId(), 
                    topicPrefix + "all", 
                    severityTopic);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize alarm event: {}", e.getMessage(), e);
        } catch (Exception e) {
            logger.error("Failed to send alarm notification via MQTT: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Sends a message to a specific MQTT topic.
     * 
     * @param payload The message payload
     * @param topic The MQTT topic
     */
    private void sendToTopic(String payload, String topic) {
        Message<String> message = MessageBuilder
                .withPayload(payload)
                .setHeader(MqttHeaders.TOPIC, topic)
                .build();
        
        mqttOutboundChannel.send(message);
    }
} 