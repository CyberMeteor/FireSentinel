# FireSentinel

FireSentinel is a comprehensive fire protection and monitoring system built with Spring Boot. It provides real-time monitoring, alerting, and visualization capabilities for fire detection and prevention.

## Project Structure

The project is organized into the following modules:

- **deviceauth**: Device authentication and authorization
- **nettytransport**: Netty server configurations and interceptors
- **cache**: Multi-level caching with Bloom Filter
- **dataprocessing**: Real-time data handling and Kafka integration
- **alarmsystem**: Complex Event Processing (CEP) engine and rule engine
- **visualization**: WebSocket and visualization components
- **config**: System properties and configuration

## Technologies Used

- **Spring Boot**: Core framework
- **Spring Security**: Authentication and authorization with OAuth2
- **Netty**: High-performance network application framework
- **Redis**: Distributed caching and data storage
- **Kafka**: Message streaming and event processing
- **TimescaleDB**: Time-series database for sensor data
- **Esper**: Complex Event Processing engine
- **Caffeine**: Local caching
- **Guava**: Bloom Filter implementation
- **Redisson**: Advanced Redis client
- **WebSocket**: Real-time communication

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6 or higher
- Redis server
- Kafka server
- PostgreSQL with TimescaleDB extension

### Configuration

The application can be configured through the `application.yml` file. Key configuration properties include:

- Redis connection details
- Kafka bootstrap servers
- TimescaleDB / PostgreSQL connection
- Netty server port
- OAuth2 client credentials

### Building the Project

```bash
mvn clean install
```

### Running the Application

```bash
mvn spring-boot:run
```

## Features

- Device authentication and authorization
- Real-time data processing with Netty and Kafka
- Multi-level caching with Bloom Filter
- Complex Event Processing for alarm detection
- WebSocket-based real-time visualization
- Time-series data storage with TimescaleDB

## License

This project is licensed under the MIT License - see the LICENSE file for details. 