package com.firesentinel.alarmsystem.config;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.outbound.MqttPahoMessageHandler;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

import java.util.UUID;

/**
 * Configuration for MQTT communication in the FireSentinel alarm system.
 * This class sets up the MQTT client and message handlers for publishing
 * alarm notifications to MQTT topics.
 */
@Configuration
public class MqttConfig {

    @Value("${mqtt.broker.url:tcp://localhost:1883}")
    private String brokerUrl;

    @Value("${mqtt.client.id:firesentinel-}")
    private String clientId;

    @Value("${mqtt.topic.prefix:firesentinel/alarm/}")
    private String topicPrefix;

    /**
     * Creates a unique client ID for the MQTT connection.
     * 
     * @return A unique client ID
     */
    private String getClientId() {
        return clientId + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * Creates the MQTT client factory with connection options.
     * 
     * @return The configured MQTT client factory
     */
    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions options = new MqttConnectOptions();
        
        options.setServerURIs(new String[] { brokerUrl });
        options.setCleanSession(true);
        options.setAutomaticReconnect(true);
        options.setConnectionTimeout(30);
        options.setKeepAliveInterval(60);
        
        factory.setConnectionOptions(options);
        return factory;
    }

    /**
     * Creates the outbound message channel for MQTT messages.
     * 
     * @return The message channel
     */
    @Bean
    public MessageChannel mqttOutboundChannel() {
        return new DirectChannel();
    }

    /**
     * Creates the message handler for outbound MQTT messages.
     * 
     * @return The message handler
     */
    @Bean
    @ServiceActivator(inputChannel = "mqttOutboundChannel")
    public MessageHandler mqttOutbound() {
        MqttPahoMessageHandler messageHandler = new MqttPahoMessageHandler(getClientId(), mqttClientFactory());
        messageHandler.setAsync(true);
        messageHandler.setDefaultTopic(topicPrefix + "all");
        return messageHandler;
    }
} 