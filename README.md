# FireSentinel: Shopping Center Intelligent Fire Protection System

<div align="center">
  <img src="img/logo.jpg" alt="FireSentinel Logo" width="200">
</div>

## Project Overview

FireSentinel is a **high-performance**, **high-concurrency** fire protection and monitoring system specifically designed for large shopping centers and commercial complexes. The system provides comprehensive real-time monitoring, early detection, and rapid response capabilities ven under heavy operational loads to ensure the safety of thousands of visitors and staff.

Built on a modern, resilient architecture, FireSentinel is capable of:
- Supporting 5,000+ concurrent device connections across multiple zones and floors
- Processing sensor data in real-time with sub-second latency
- Providing intelligent alarm distribution with multi-channel notifications
- Maintaining historical data for compliance, analysis, and continuous improvement
- Ensuring system reliability through fault tolerance and resilience patterns

### Core Technologies

- **Backend**: Java 17, Spring Boot 3.2
- **Real-time Processing**: Netty, Kafka, Esper CEP
- **Storage**: TimescaleDB (PostgreSQL), Redis
- **Communication**: WebSockets, MQTT
- **Observability**: Micrometer, Prometheus, Grafana, OpenTelemetry
- **Resilience**: Resilience4j (Circuit Breakers, Bulkheads, Retries)

## Key Features

### Device Management & Authentication
- **Secure Device Onboarding**: Automated device registration with unique identifiers
- **OAuth2.0 Authentication**: Token-based security for all device communications
- **Device Metadata Management**: Comprehensive device information including location, type, and maintenance history
- **Health Monitoring**: Continuous device health checks and automatic alerts for offline devices

### Multi-Level Caching Architecture
- **Redis Cluster**: Distributed caching for high availability and performance
- **Bloom Filters**: Efficient duplicate detection to prevent alarm flooding
- **Time-Window Caching**: Optimized for time-series data with automatic expiration
- **Write-Behind Caching**: Ensures no data loss during database unavailability

### Real-Time Data Pipeline
- **Netty-Based Socket Server**: High-performance, non-blocking I/O for device connections
- **Kafka Streaming**: Scalable message processing with topic partitioning
- **Complex Event Processing**: Esper CEP engine for pattern detection and correlation
- **Backpressure Handling**: Adaptive rate limiting to manage traffic spikes

### Emergency Alarm System
- **Multi-Level Alarm Classification**: HIGH, MEDIUM, and LOW severity categorization
- **Intelligent Alarm Correlation**: Reduces false positives through pattern recognition
- **Multi-Channel Notifications**: Simultaneous alerts via WebSockets, MQTT, and SMS
- **Alarm History**: Complete audit trail with search and filtering capabilities
- **Resilient Alarm Distribution**: Circuit breakers and fallback mechanisms ensure critical alerts are never lost

### Data Storage & Analytics
- **TimescaleDB**: Optimized time-series storage for sensor data
- **Automatic Data Retention Policies**: Configurable data lifecycle management
- **Historical Analysis**: Trend identification and pattern recognition
- **Compliance Reporting**: Automated generation of safety compliance reports

### Visualization & Monitoring
- **Real-Time Dashboards**: Grafana integration for live system monitoring
- **Interactive Floor Maps**: Visual representation of device status and alarms
- **Performance Metrics**: Comprehensive system health and performance statistics
- **Distributed Tracing**: End-to-end request tracking with OpenTelemetry and Jaeger

### Resilience & Fault Tolerance
- **Circuit Breakers**: Prevent cascading failures across system components
- **Bulkheads**: Isolate failures to maintain overall system stability
- **Retry Mechanisms**: Automatic recovery from transient failures
- **Fallback Strategies**: Graceful degradation during component unavailability

## System Architecture

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Fire Sensors   │     │  Smoke Sensors  │     │ Motion Sensors  │
└────────┬────────┘     └────────┬────────┘     └────────┬────────┘
         │                       │                       │
         └───────────────┬───────┴───────────────┬───────┘
                         │                       │
                ┌────────▼────────┐     ┌────────▼────────┐
                │   Netty Server  │     │  MQTT Broker    │
                └────────┬────────┘     └────────┬────────┘
                         │                       │
                         └───────────┬───────────┘
                                     │
                           ┌─────────▼─────────┐
                           │  Authentication   │
                           │     Service       │
                           └─────────┬─────────┘
                                     │
                           ┌─────────▼─────────┐
                           │   Kafka Streams   │
                           └─────────┬─────────┘
                                     │
                 ┌───────────────────┼───────────────────┐
                 │                   │                   │
        ┌────────▼────────┐ ┌────────▼────────┐ ┌────────▼────────┐
        │   Esper CEP     │ │  Redis Cache    │ │  Device Mgmt    │
        │     Engine      │ │                 │ │    Service      │
        └────────┬────────┘ └────────┬────────┘ └────────┬────────┘
                 │                   │                   │
                 └───────────────────┼───────────────────┘
                                     │
                           ┌─────────▼─────────┐
                           │ Alarm Distribution│
                           │     Service       │
                           └─────────┬─────────┘
                                     │
         ┌────────────────────┬──────┴──────┬────────────────────┐
         │                    │             │                    │
┌────────▼────────┐  ┌────────▼────────┐   ┌▼────────────────┐  ┌▼────────────────┐
│  WebSocket      │  │  MQTT           │   │ TimescaleDB     │  │ Notification    │
│  Notifications  │  │  Publisher      │   │ (Historical)    │  │ Service (SMS)   │
└─────────────────┘  └─────────────────┘   └─────────────────┘  └─────────────────┘
         │                    │                    │                    │
         └────────────────────┴────────────────────┴────────────────────┘
                                     │
                           ┌─────────▼─────────┐
                           │  Monitoring &     │
                           │  Observability    │
                           └───────────────────┘
```

### Data Flow

1. **Data Ingestion**: Sensors connect to the system via Netty server (TCP/IP) or MQTT broker
2. **Authentication**: All devices are authenticated using OAuth2.0 tokens
3. **Stream Processing**: Sensor data flows through Kafka for reliable, scalable processing
4. **Real-time Analysis**: The Esper CEP engine analyzes data streams for patterns and anomalies
5. **Alarm Generation**: When conditions match predefined patterns, alarms are generated
6. **Alarm Distribution**: Alarms are distributed through multiple channels (WebSocket, MQTT, SMS)
7. **Data Storage**: All sensor data and alarms are stored in TimescaleDB for historical analysis
8. **Caching**: Redis provides caching for frequently accessed data and temporary storage during outages
9. **Monitoring**: All system components report metrics to Prometheus and traces to Jaeger

### Concurrency Model

FireSentinel employs a reactive, non-blocking concurrency model throughout the system:
- Netty's event loop handles thousands of concurrent connections efficiently
- Kafka partitioning enables parallel processing of sensor data streams
- CompletableFuture and reactive programming patterns ensure non-blocking operations
- Thread pools are carefully sized and monitored to prevent resource exhaustion
- Bulkheads isolate critical system components to maintain stability under load

## Getting Started

### Prerequisites

- Java 17 or higher
- Docker and Docker Compose
- Maven 3.8+
- Git

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/CyberMeteor/FireSentinel
   cd firesentinel
   ```

2. Build the application:
   ```bash
   mvn clean package
   ```

3. Start the required infrastructure using Docker Compose:
   ```bash
   docker-compose up -d
   ```

4. Start the monitoring stack:
   ```bash
   docker-compose -f docker-compose-monitoring.yml up -d
   ```

5. Run the application:
   ```bash
   java -jar target/fire-sentinel-0.0.1-SNAPSHOT.jar
   ```

### Configuration

The application can be configured through the `application.properties` file or environment variables:

```properties
# Server configuration
server.port=8080
server.servlet.context-path=/firesentinel

# Kafka configuration
spring.kafka.bootstrap-servers=localhost:9092
spring.kafka.consumer.group-id=sensor-data-consumer

# TimescaleDB configuration
spring.datasource.url=jdbc:postgresql://localhost:5432/firesentinel
spring.datasource.username=postgres
spring.datasource.password=postgres

# Redis configuration
spring.redis.host=localhost
spring.redis.port=6379

# Alarm system configuration
alarm.deduplication.enabled=true
alarm.deduplication.window-seconds=300
alarm.history.retention-days=30
```


## Usage and Examples

### Device Connection

Devices connect to the system using either TCP/IP (Netty) or MQTT:

#### TCP Connection Example

```java
// Client-side Java example
Socket socket = new Socket("firesentinel.example.com", 8081);
PrintWriter out = new PrintWriter(socket.getOutputStream(), true);

// Authenticate
out.println("{\"type\":\"auth\",\"deviceId\":\"DEVICE001\",\"token\":\"eyJhbGciOiJIUzI1...\"}");

// Send sensor data
out.println("{\"type\":\"data\",\"deviceId\":\"DEVICE001\",\"sensorType\":\"temperature\",\"value\":24.5,\"timestamp\":1625097600000}");
```

#### MQTT Connection Example

```bash
# Using mosquitto_pub
mosquitto_pub -h firesentinel.example.com -p 1883 -t "firesentinel/device/DEVICE001/data" \
  -m '{"sensorType":"smoke","value":0.05,"timestamp":1625097600000}' \
  -u "DEVICE001" -P "device_password"
```

### API Examples

#### Device Registration

```bash
curl -X POST https://firesentinel.example.com/firesentinel/api/devices \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  -d '{
    "id": "DEVICE001",
    "name": "Smoke Detector - Food Court Zone 3",
    "type": "smoke",
    "floorId": "1",
    "zoneId": "food-court-3",
    "location": {"x": 120.5, "y": 78.3}
  }'
```

#### Retrieving Alarm History

```bash
curl -X GET https://firesentinel.example.com/firesentinel/api/alarm-history/recent?count=10 \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```

#### Triggering a Test Alarm

```bash
curl -X POST https://firesentinel.example.com/firesentinel/api/test/alarm \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  -d '{
    "deviceId": "DEVICE001",
    "type": "SMOKE_DETECTED",
    "severity": "HIGH",
    "message": "Test alarm - please ignore"
  }'
```

### Viewing Dashboards

1. Access the Grafana dashboard at `http://localhost:3000` (default credentials: admin/admin)
2. Navigate to the "FireSentinel" dashboard to view system metrics, device status, and alarm history
3. For distributed tracing, access Jaeger UI at `http://localhost:16686`

## Performance Highlights

FireSentinel is designed for high performance and scalability:

- **Connection Handling**: Supports 5,000+ concurrent device connections with Netty's non-blocking I/O
- **Message Processing**: Processes 10,000+ messages per second with sub-100ms latency
- **Alarm Distribution**: Delivers alarms to all notification channels within 500ms
- **Caching Strategy**: Multi-level caching reduces database load by up to 90%
- **Resilience**: Maintains operation during component failures with circuit breakers and fallbacks
- **Data Storage**: Efficiently stores and queries billions of data points using TimescaleDB's hypertables

## Monitoring & Observability

FireSentinel provides comprehensive monitoring and observability:

### Metrics

All system components expose metrics via Micrometer, which are collected by Prometheus and visualized in Grafana:

- **System Metrics**: CPU, memory, disk usage, JVM statistics
- **Application Metrics**: Message throughput, alarm counts, response times
- **Business Metrics**: Alarms by severity, device status, zone activity

### Distributed Tracing

OpenTelemetry integration provides end-to-end distributed tracing:

- Trace device connections through the entire system
- Identify bottlenecks in the processing pipeline
- Debug complex issues across multiple components

### Logging

Structured logging with correlation IDs enables tracking requests across components:

- Log aggregation with ELK stack (optional)
- Log correlation with trace IDs
- Configurable log levels for different components

## Contributing

We welcome contributions to FireSentinel! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

Please ensure your code follows our coding standards and includes appropriate tests.



## Future Enhancements

- **AI-Based Fire Risk Prediction**: Machine learning models to predict fire risks based on historical data
- **Edge Computing Integration**: Push more intelligence to edge devices for faster response
- **Mobile Application**: Companion app for security personnel with push notifications
- **Advanced Analytics**: Deeper insights into system performance and fire safety patterns
- **Integration with Building Management Systems**: Connect with HVAC, access control, and other building systems
- **Multi-Tenancy Support**: Enable management of multiple shopping centers from a single instance