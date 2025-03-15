package com.firesentinel.dataprocessing.config;

import com.firesentinel.dataprocessing.model.AlarmEvent;
import com.firesentinel.dataprocessing.model.SensorData;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.*;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration class for Kafka.
 */
@Configuration
@EnableKafka
@RequiredArgsConstructor
@Slf4j
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Value("${spring.kafka.consumer.group-id}")
    private String groupId;
    
    @Value("${spring.kafka.consumer.alarm-group-id:alarm-consumer}")
    private String alarmGroupId;
    
    @Value("${spring.kafka.consumer.backpressure-group-id:backpressure-consumer}")
    private String backpressureGroupId;
    
    @Value("${spring.kafka.topics.sensor-data}")
    private String sensorDataTopic;
    
    @Value("${spring.kafka.topics.alarm-events}")
    private String alarmEventsTopic;
    
    @Value("${spring.kafka.partitions:10}")
    private int partitions;
    
    @Value("${spring.kafka.replication-factor:1}")
    private short replicationFactor;
    
    /**
     * Creates a Kafka admin client.
     *
     * @return The Kafka admin client factory
     */
    @Bean
    public KafkaAdmin kafkaAdmin() {
        Map<String, Object> configs = new HashMap<>();
        configs.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        return new KafkaAdmin(configs);
    }
    
    /**
     * Creates the sensor data topic.
     *
     * @return The sensor data topic
     */
    @Bean
    public NewTopic sensorDataTopic() {
        return TopicBuilder.name(sensorDataTopic)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }
    
    /**
     * Creates the alarm events topic.
     *
     * @return The alarm events topic
     */
    @Bean
    public NewTopic alarmEventsTopic() {
        return TopicBuilder.name(alarmEventsTopic)
                .partitions(partitions)
                .replicas(replicationFactor)
                .build();
    }
    
    /**
     * Creates a Kafka producer factory for sensor data.
     *
     * @return The Kafka producer factory
     */
    @Bean
    public ProducerFactory<String, SensorData> sensorDataProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        return new DefaultKafkaProducerFactory<>(configProps);
    }
    
    /**
     * Creates a Kafka producer factory for alarm events.
     *
     * @return The Kafka producer factory
     */
    @Bean
    public ProducerFactory<String, AlarmEvent> alarmEventProducerFactory() {
        Map<String, Object> configProps = new HashMap<>();
        configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        configProps.put(ProducerConfig.ACKS_CONFIG, "all");
        configProps.put(ProducerConfig.RETRIES_CONFIG, 3);
        configProps.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        configProps.put(ProducerConfig.LINGER_MS_CONFIG, 10);
        configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);
        return new DefaultKafkaProducerFactory<>(configProps);
    }
    
    /**
     * Creates a Kafka template for sensor data.
     *
     * @return The Kafka template
     */
    @Bean
    public KafkaTemplate<String, SensorData> sensorDataKafkaTemplate() {
        return new KafkaTemplate<>(sensorDataProducerFactory());
    }
    
    /**
     * Creates a Kafka template for alarm events.
     *
     * @return The Kafka template
     */
    @Bean
    public KafkaTemplate<String, AlarmEvent> alarmEventKafkaTemplate() {
        return new KafkaTemplate<>(alarmEventProducerFactory());
    }
    
    /**
     * Creates a Kafka consumer factory for sensor data.
     *
     * @return The Kafka consumer factory
     */
    @Bean
    public ConsumerFactory<String, SensorData> sensorDataConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 500);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.firesentinel.dataprocessing.model");
        return new DefaultKafkaConsumerFactory<>(props);
    }
    
    /**
     * Creates a Kafka consumer factory for alarm events.
     *
     * @return The Kafka consumer factory
     */
    @Bean
    public ConsumerFactory<String, AlarmEvent> alarmEventConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, alarmGroupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.firesentinel.dataprocessing.model");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, AlarmEvent.class);
        return new DefaultKafkaConsumerFactory<>(props);
    }
    
    /**
     * Creates a Kafka listener container factory for sensor data.
     *
     * @return The Kafka listener container factory
     */
    @Bean
    public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, SensorData>> 
            kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, SensorData> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(sensorDataConsumerFactory());
        factory.setConcurrency(5); // Number of consumer threads
        factory.setBatchListener(false); // Disable batch listening for regular consumer
        factory.getContainerProperties().setPollTimeout(3000);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
    
    /**
     * Creates a Kafka listener container factory for alarm events.
     *
     * @return The Kafka listener container factory
     */
    @Bean
    public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, AlarmEvent>> 
            alarmEventKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, AlarmEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(alarmEventConsumerFactory());
        factory.setConcurrency(3); // Fewer threads for alarm events
        factory.setBatchListener(false); // Disable batch listening for alarm events
        factory.getContainerProperties().setPollTimeout(3000);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        return factory;
    }
    
    /**
     * Creates a Kafka listener container factory with backpressure for sensor data.
     *
     * @return The Kafka listener container factory with backpressure
     */
    @Bean
    public KafkaListenerContainerFactory<ConcurrentMessageListenerContainer<String, SensorData>> 
            backpressureKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, SensorData> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        
        // Set a smaller batch size for backpressure
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, backpressureGroupId);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100);
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.firesentinel.dataprocessing.model");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, SensorData.class);
        
        // Create a new consumer factory with the updated properties
        ConsumerFactory<String, SensorData> backpressureConsumerFactory = 
                new DefaultKafkaConsumerFactory<>(props);
        
        factory.setConsumerFactory(backpressureConsumerFactory);
        factory.setConcurrency(3); // Fewer threads for backpressure
        factory.setBatchListener(true); // Enable batch listening for backpressure
        factory.getContainerProperties().setPollTimeout(1000);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        
        return factory;
    }
} 