package com.firesentinel.dataprocessing.repository;

import com.firesentinel.dataprocessing.model.SensorData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;

/**
 * Repository for sensor data.
 */
@Repository
public interface SensorDataRepository extends JpaRepository<SensorData, Long> {

    /**
     * Finds sensor data by device ID.
     *
     * @param deviceId The device ID
     * @return A list of sensor data
     */
    List<SensorData> findByDeviceIdOrderByTimestampDesc(String deviceId);
    
    /**
     * Finds sensor data by device ID and sensor type.
     *
     * @param deviceId The device ID
     * @param sensorType The sensor type
     * @return A list of sensor data
     */
    List<SensorData> findByDeviceIdAndSensorTypeOrderByTimestampDesc(String deviceId, String sensorType);
    
    /**
     * Finds sensor data by device ID and time range.
     *
     * @param deviceId The device ID
     * @param startTime The start time
     * @param endTime The end time
     * @return A list of sensor data
     */
    List<SensorData> findByDeviceIdAndTimestampBetweenOrderByTimestampDesc(
            String deviceId, Instant startTime, Instant endTime);
    
    /**
     * Gets the average value for a device and sensor type over a time range.
     *
     * @param deviceId The device ID
     * @param sensorType The sensor type
     * @param startTime The start time
     * @param endTime The end time
     * @return The average value
     */
    @Query("SELECT AVG(s.value) FROM SensorData s WHERE s.deviceId = :deviceId " +
           "AND s.sensorType = :sensorType AND s.timestamp BETWEEN :startTime AND :endTime")
    Double getAverageValue(
            @Param("deviceId") String deviceId,
            @Param("sensorType") String sensorType,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);
    
    /**
     * Gets the maximum value for a device and sensor type over a time range.
     *
     * @param deviceId The device ID
     * @param sensorType The sensor type
     * @param startTime The start time
     * @param endTime The end time
     * @return The maximum value
     */
    @Query("SELECT MAX(s.value) FROM SensorData s WHERE s.deviceId = :deviceId " +
           "AND s.sensorType = :sensorType AND s.timestamp BETWEEN :startTime AND :endTime")
    Double getMaxValue(
            @Param("deviceId") String deviceId,
            @Param("sensorType") String sensorType,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);
    
    /**
     * Gets the minimum value for a device and sensor type over a time range.
     *
     * @param deviceId The device ID
     * @param sensorType The sensor type
     * @param startTime The start time
     * @param endTime The end time
     * @return The minimum value
     */
    @Query("SELECT MIN(s.value) FROM SensorData s WHERE s.deviceId = :deviceId " +
           "AND s.sensorType = :sensorType AND s.timestamp BETWEEN :startTime AND :endTime")
    Double getMinValue(
            @Param("deviceId") String deviceId,
            @Param("sensorType") String sensorType,
            @Param("startTime") Instant startTime,
            @Param("endTime") Instant endTime);
} 