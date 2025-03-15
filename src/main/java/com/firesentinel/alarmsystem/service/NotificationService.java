package com.firesentinel.alarmsystem.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firesentinel.alarmsystem.model.AlarmNotification;
import com.firesentinel.alarmsystem.model.AlarmRule;
import com.firesentinel.dataprocessing.model.SensorData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service for sending alarm notifications via WebSocket and MQTT.
 * Uses dual-channel notifications for redundancy and reliability.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final SimpMessagingTemplate webSocketTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${mqtt.broker.url:tcp://localhost:1883}")
    private String mqttBrokerUrl;
    
    @Value("${mqtt.client.id:firesentinel-}")
    private String mqttClientIdPrefix;
    
    @Value("${mqtt.topic.prefix:firesentinel/alarm/}")
    private String mqttTopicPrefix;
    
    @Value("${websocket.topic.prefix:/topic/alarm/}")
    private String webSocketTopicPrefix;
    
    private MqttClient mqttClient;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    
    /**
     * Initializes the notification service.
     */
    @PostConstruct
    public void init() {
        try {
            // Initialize MQTT client
            String clientId = mqttClientIdPrefix + UUID.randomUUID();
            mqttClient = new MqttClient(mqttBrokerUrl, clientId);
            
            // Set up connection options
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            
            // Connect to the broker
            mqttClient.connect(options);
            
            log.info("Connected to MQTT broker: {}", mqttBrokerUrl);
            
        } catch (MqttException e) {
            log.error("Failed to connect to MQTT broker: {}", mqttBrokerUrl, e);
        }
    }
    
    /**
     * Sends an alarm notification via WebSocket and MQTT.
     *
     * @param rule The rule that triggered the alarm
     * @param sensorData The sensor data that triggered the alarm
     */
    public void sendAlarmNotification(AlarmRule rule, SensorData sensorData) {
        try {
            // Create the notification
            AlarmNotification notification = createNotification(rule, sensorData);
            
            // Convert to JSON
            String notificationJson = objectMapper.writeValueAsString(notification);
            
            // Send via WebSocket
            sendWebSocketNotification(notification.getSeverity(), notificationJson);
            
            // Send via MQTT
            sendMqttNotification(notification.getSeverity(), notificationJson);
            
            log.info("Sent alarm notification: {}", notification.getId());
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize notification", e);
        }
    }
    
    /**
     * Creates an alarm notification.
     *
     * @param rule The rule that triggered the alarm
     * @param sensorData The sensor data that triggered the alarm
     * @return The alarm notification
     */
    private AlarmNotification createNotification(AlarmRule rule, SensorData sensorData) {
        return AlarmNotification.builder()
                .id(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .ruleId(rule.getId())
                .ruleName(rule.getName())
                .deviceId(sensorData.getDeviceId())
                .sensorType(sensorData.getSensorType())
                .value(sensorData.getValue())
                .threshold(rule.getThreshold())
                .operator(rule.getOperator())
                .alarmType(rule.getAlarmType())
                .severity(rule.getSeverity())
                .buildingId(rule.getBuildingId())
                .floorId(rule.getFloorId())
                .roomId(rule.getRoomId())
                .zoneId(rule.getZoneId())
                .locationX(sensorData.getLocationX())
                .locationY(sensorData.getLocationY())
                .locationZ(sensorData.getLocationZ())
                .build();
    }
    
    /**
     * Sends a notification via WebSocket.
     *
     * @param severity The severity of the alarm
     * @param notificationJson The notification as JSON
     */
    private void sendWebSocketNotification(String severity, String notificationJson) {
        executorService.submit(() -> {
            try {
                // Send to the general topic
                webSocketTemplate.convertAndSend(webSocketTopicPrefix + "all", notificationJson);
                
                // Send to the severity-specific topic
                webSocketTemplate.convertAndSend(webSocketTopicPrefix + severity.toLowerCase(), notificationJson);
                
                log.debug("Sent WebSocket notification to topics: {}, {}", 
                        webSocketTopicPrefix + "all", webSocketTopicPrefix + severity.toLowerCase());
                
            } catch (Exception e) {
                log.error("Failed to send WebSocket notification", e);
            }
        });
    }
    
    /**
     * Sends a notification via MQTT.
     *
     * @param severity The severity of the alarm
     * @param notificationJson The notification as JSON
     */
    private void sendMqttNotification(String severity, String notificationJson) {
        executorService.submit(() -> {
            try {
                if (mqttClient == null || !mqttClient.isConnected()) {
                    log.warn("MQTT client is not connected, reconnecting...");
                    reconnectMqtt();
                }
                
                if (mqttClient != null && mqttClient.isConnected()) {
                    // Create the message
                    MqttMessage message = new MqttMessage(notificationJson.getBytes());
                    message.setQos(1); // At least once delivery
                    message.setRetained(false);
                    
                    // Send to the general topic
                    mqttClient.publish(mqttTopicPrefix + "all", message);
                    
                    // Send to the severity-specific topic
                    mqttClient.publish(mqttTopicPrefix + severity.toLowerCase(), message);
                    
                    log.debug("Sent MQTT notification to topics: {}, {}", 
                            mqttTopicPrefix + "all", mqttTopicPrefix + severity.toLowerCase());
                } else {
                    log.error("Failed to send MQTT notification: client is not connected");
                }
                
            } catch (MqttException e) {
                log.error("Failed to send MQTT notification", e);
            }
        });
    }
    
    /**
     * Reconnects to the MQTT broker.
     */
    private void reconnectMqtt() {
        try {
            if (mqttClient != null) {
                mqttClient.close();
            }
            
            // Initialize MQTT client
            String clientId = mqttClientIdPrefix + UUID.randomUUID();
            mqttClient = new MqttClient(mqttBrokerUrl, clientId);
            
            // Set up connection options
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            options.setConnectionTimeout(10);
            
            // Connect to the broker
            mqttClient.connect(options);
            
            log.info("Reconnected to MQTT broker: {}", mqttBrokerUrl);
            
        } catch (MqttException e) {
            log.error("Failed to reconnect to MQTT broker: {}", mqttBrokerUrl, e);
        }
    }
    
    /**
     * Destroys the notification service.
     */
    @PreDestroy
    public void destroy() {
        try {
            if (mqttClient != null && mqttClient.isConnected()) {
                mqttClient.disconnect();
                mqttClient.close();
            }
            
            executorService.shutdown();
            
        } catch (MqttException e) {
            log.error("Failed to disconnect from MQTT broker", e);
        }
    }
} 