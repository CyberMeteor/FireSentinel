# FireSentinel Real-Time Data Pipeline

This project implements a high-performance real-time data pipeline for the FireSentinel system, designed to process sensor data from fire detection devices and trigger appropriate responses.

## Architecture Overview

The real-time data pipeline consists of the following components:

1. **Snowflake ID Generation**: Unique ID generation for distributed systems
2. **TimescaleDB Integration**: Time-series database for efficient storage and querying of sensor data
3. **Atomic Execution with Redis and Lua**: Atomic operations for fire suppression actions
4. **Kafka for Backpressure**: Message queue for handling high throughput and implementing backpressure

## Key Components

### Snowflake ID Generator

The `SnowflakeIdGenerator` class provides unique, time-ordered IDs for distributed systems. These IDs are used to identify sensor data and alarm events.

### TimescaleDB Service

The `TimescaleDBService` handles operations related to the TimescaleDB hypertable, including:
- Creating and managing the hypertable
- Saving sensor data
- Batch inserting sensor data
- Querying sensor data with various filters
- Aggregating sensor data over time intervals

### Fire Suppression Service

The `FireSuppressionService` uses Redis Lua scripts for atomic execution of fire suppression operations:
- Activating fire suppression
- Getting device status
- Incrementing suppression counters

### Kafka Integration

The data pipeline uses Kafka for reliable message processing with backpressure handling:
- `SensorDataProducerService`: Sends sensor data to Kafka
- `SensorDataConsumerService`: Consumes sensor data from Kafka with backpressure handling
- `AlarmEventProducerService`: Sends alarm events to Kafka
- `AlarmEventConsumerService`: Consumes alarm events from Kafka and triggers appropriate responses

## API Endpoints

### Sensor Data API

- `POST /api/sensor-data`: Send sensor data to Kafka
- `POST /api/sensor-data/batch`: Send multiple sensor data records to Kafka
- `GET /api/sensor-data/stats`: Get statistics about the Kafka producer and consumer
- `GET /api/sensor-data/device/{deviceId}`: Get sensor data for a device
- `GET /api/sensor-data/device/{deviceId}/type/{sensorType}`: Get sensor data for a device and sensor type
- `GET /api/sensor-data/device/{deviceId}/range`: Get sensor data for a device within a time range
- `GET /api/sensor-data/device/{deviceId}/type/{sensorType}/aggregate`: Get aggregated data for a device and sensor type

### Alarm Events API

- `POST /api/alarms`: Send an alarm event to Kafka
- `POST /api/alarms/from-sensor`: Create and send an alarm event based on sensor data
- `GET /api/alarms`: Get all active alarms
- `GET /api/alarms/device/{deviceId}`: Get active alarms for a device
- `POST /api/alarms/{alarmId}/acknowledge`: Acknowledge an alarm
- `POST /api/alarms/{alarmId}/resolve`: Resolve an alarm
- `GET /api/alarms/stats`: Get statistics about the alarm event producer and consumer

## Setup and Configuration

### Prerequisites

- Java 17 or higher
- TimescaleDB (PostgreSQL with TimescaleDB extension)
- Redis
- Kafka

### Configuration

The application can be configured using the `application.properties` file:

```properties
# Kafka configuration
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=sensor-data-consumer
spring.kafka.consumer.alarm-group-id=alarm-events-consumer
spring.kafka.consumer.backpressure-group-id=backpressure-consumer
spring.kafka.topics.sensor-data=sensor-data
spring.kafka.topics.alarm-events=alarm-events

# TimescaleDB configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/firesentinel
spring.datasource.username=postgres
spring.datasource.password=postgres

# Redis configuration
spring.redis.host=localhost
spring.redis.port=6379

# Snowflake ID configuration
snowflake.datacenter-id=1
snowflake.worker-id=1
```

## Performance Considerations

The data pipeline is designed for high performance and reliability:

1. **Backpressure Handling**: Uses Kafka consumer groups with different concurrency settings to handle high load situations
2. **Batch Processing**: Supports batch insertion of sensor data for better performance
3. **TimescaleDB Optimization**: Uses hypertables and compression policies for efficient time-series data storage
4. **Atomic Operations**: Uses Redis Lua scripts for atomic operations
5. **Concurrent Processing**: Uses concurrent processing for sensor data and alarm events

## Monitoring and Statistics

The system provides statistics endpoints for monitoring:

- `GET /api/sensor-data/stats`: Statistics about sensor data processing
- `GET /api/alarms/stats`: Statistics about alarm event processing

These endpoints provide information about message counts, success rates, and processing times. 