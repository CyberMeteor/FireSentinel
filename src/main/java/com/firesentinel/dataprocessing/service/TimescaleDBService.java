package com.firesentinel.dataprocessing.service;

import com.firesentinel.dataprocessing.model.SensorData;
import com.firesentinel.dataprocessing.repository.SensorDataRepository;
import com.firesentinel.dataprocessing.util.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Service for TimescaleDB operations.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TimescaleDBService {

    private final SensorDataRepository sensorDataRepository;
    private final JdbcTemplate jdbcTemplate;
    private final SnowflakeIdGenerator snowflakeIdGenerator;
    
    @Value("${timescaledb.chunk-interval:86400000}")
    private long chunkIntervalMs; // Default to 1 day in milliseconds
    
    /**
     * Initializes the TimescaleDB hypertable.
     */
    @PostConstruct
    public void initHypertable() {
        try {
            // Check if the extension is installed
            jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS timescaledb CASCADE");
            
            // Check if the table is already a hypertable
            List<Map<String, Object>> result = jdbcTemplate.queryForList(
                    "SELECT * FROM timescaledb_information.hypertables WHERE hypertable_name = 'sensor_data'");
            
            if (result.isEmpty()) {
                // Convert the table to a hypertable
                jdbcTemplate.execute(
                        "SELECT create_hypertable('sensor_data', 'timestamp', chunk_time_interval => " + chunkIntervalMs + ")");
                log.info("Created hypertable for sensor_data with chunk interval of {} ms", chunkIntervalMs);
                
                // Create additional indexes for better query performance
                jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_sensor_data_device_sensor_time " +
                        "ON sensor_data (device_id, sensor_type, timestamp DESC)");
                
                // Create a compression policy (optional)
                jdbcTemplate.execute("ALTER TABLE sensor_data SET (timescaledb.compress = true)");
                jdbcTemplate.execute("SELECT add_compression_policy('sensor_data', INTERVAL '7 days')");
                
                log.info("Configured compression policy for sensor_data");
            } else {
                log.info("Hypertable for sensor_data already exists");
            }
        } catch (Exception e) {
            log.error("Failed to initialize TimescaleDB hypertable", e);
        }
    }
    
    /**
     * Saves sensor data to the hypertable.
     *
     * @param deviceId The device ID
     * @param deviceTypeId The device type ID
     * @param sensorType The sensor type
     * @param value The sensor value
     * @param unit The unit of measurement
     * @param timestamp The timestamp
     * @param locationX The X coordinate
     * @param locationY The Y coordinate
     * @param locationZ The Z coordinate
     * @param metadata Additional metadata
     * @return The saved sensor data
     */
    @Transactional
    public SensorData saveSensorData(
            String deviceId,
            int deviceTypeId,
            String sensorType,
            double value,
            String unit,
            Instant timestamp,
            Double locationX,
            Double locationY,
            Double locationZ,
            String metadata) {
        
        // Generate a Snowflake ID
        long id = snowflakeIdGenerator.generateId(deviceTypeId);
        
        // Create the sensor data entity
        SensorData sensorData = SensorData.builder()
                .id(id)
                .deviceId(deviceId)
                .sensorType(sensorType)
                .value(value)
                .unit(unit)
                .timestamp(timestamp)
                .locationX(locationX)
                .locationY(locationY)
                .locationZ(locationZ)
                .metadata(metadata)
                .build();
        
        // Save to the database
        return sensorDataRepository.save(sensorData);
    }
    
    /**
     * Saves sensor data to the hypertable using JDBC batch insert.
     * This is more efficient for bulk inserts.
     *
     * @param sensorDataList The list of sensor data to save
     */
    @Transactional
    public void saveSensorDataBatch(List<SensorData> sensorDataList) {
        if (sensorDataList.isEmpty()) {
            return;
        }
        
        // Use JDBC batch insert for better performance
        jdbcTemplate.batchUpdate(
                "INSERT INTO sensor_data (id, device_id, sensor_type, value, unit, timestamp, " +
                "location_x, location_y, location_z, metadata, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, NOW())",
                sensorDataList,
                sensorDataList.size(),
                (ps, sensorData) -> {
                    ps.setLong(1, sensorData.getId());
                    ps.setString(2, sensorData.getDeviceId());
                    ps.setString(3, sensorData.getSensorType());
                    ps.setDouble(4, sensorData.getValue());
                    ps.setString(5, sensorData.getUnit());
                    ps.setObject(6, sensorData.getTimestamp());
                    ps.setObject(7, sensorData.getLocationX());
                    ps.setObject(8, sensorData.getLocationY());
                    ps.setObject(9, sensorData.getLocationZ());
                    ps.setString(10, sensorData.getMetadata());
                }
        );
        
        log.debug("Saved {} sensor data records in batch", sensorDataList.size());
    }
    
    /**
     * Batch inserts sensor data to the hypertable.
     * Alias for saveSensorDataBatch for consistency with consumer service.
     *
     * @param sensorDataList The list of sensor data to insert
     */
    @Transactional
    public void batchInsertSensorData(List<SensorData> sensorDataList) {
        saveSensorDataBatch(sensorDataList);
    }
    
    /**
     * Gets sensor data for a device.
     *
     * @param deviceId The device ID
     * @return A list of sensor data
     */
    public List<SensorData> getSensorDataForDevice(String deviceId) {
        return sensorDataRepository.findByDeviceIdOrderByTimestampDesc(deviceId);
    }
    
    /**
     * Gets sensor data for a device and sensor type.
     *
     * @param deviceId The device ID
     * @param sensorType The sensor type
     * @return A list of sensor data
     */
    public List<SensorData> getSensorDataForDeviceAndType(String deviceId, String sensorType) {
        return sensorDataRepository.findByDeviceIdAndSensorTypeOrderByTimestampDesc(deviceId, sensorType);
    }
    
    /**
     * Gets sensor data for a device within a time range.
     *
     * @param deviceId The device ID
     * @param startTime The start time
     * @param endTime The end time
     * @return A list of sensor data
     */
    public List<SensorData> getSensorDataForDeviceInTimeRange(
            String deviceId, Instant startTime, Instant endTime) {
        return sensorDataRepository.findByDeviceIdAndTimestampBetweenOrderByTimestampDesc(
                deviceId, startTime, endTime);
    }
    
    /**
     * Gets the average value for a device and sensor type over a time range.
     *
     * @param deviceId The device ID
     * @param sensorType The sensor type
     * @param startTime The start time
     * @param endTime The end time
     * @return The average value
     */
    public Double getAverageValue(String deviceId, String sensorType, Instant startTime, Instant endTime) {
        return sensorDataRepository.getAverageValue(deviceId, sensorType, startTime, endTime);
    }
    
    /**
     * Gets the maximum value for a device and sensor type over a time range.
     *
     * @param deviceId The device ID
     * @param sensorType The sensor type
     * @param startTime The start time
     * @param endTime The end time
     * @return The maximum value
     */
    public Double getMaxValue(String deviceId, String sensorType, Instant startTime, Instant endTime) {
        return sensorDataRepository.getMaxValue(deviceId, sensorType, startTime, endTime);
    }
    
    /**
     * Gets the minimum value for a device and sensor type over a time range.
     *
     * @param deviceId The device ID
     * @param sensorType The sensor type
     * @param startTime The start time
     * @param endTime The end time
     * @return The minimum value
     */
    public Double getMinValue(String deviceId, String sensorType, Instant startTime, Instant endTime) {
        return sensorDataRepository.getMinValue(deviceId, sensorType, startTime, endTime);
    }
    
    /**
     * Executes a continuous aggregate query.
     * This is a TimescaleDB-specific feature for efficient aggregation of time-series data.
     *
     * @param deviceId The device ID
     * @param sensorType The sensor type
     * @param interval The interval (e.g., '1 hour', '1 day')
     * @param startTime The start time
     * @param endTime The end time
     * @return The aggregated data
     */
    public List<Map<String, Object>> getAggregatedData(
            String deviceId, String sensorType, String interval, Instant startTime, Instant endTime) {
        
        String sql = "SELECT time_bucket(?, timestamp) AS bucket, " +
                "AVG(value) AS avg_value, " +
                "MIN(value) AS min_value, " +
                "MAX(value) AS max_value, " +
                "COUNT(*) AS sample_count " +
                "FROM sensor_data " +
                "WHERE device_id = ? AND sensor_type = ? AND timestamp BETWEEN ? AND ? " +
                "GROUP BY bucket " +
                "ORDER BY bucket";
        
        return jdbcTemplate.queryForList(sql, interval, deviceId, sensorType, startTime, endTime);
    }
} 