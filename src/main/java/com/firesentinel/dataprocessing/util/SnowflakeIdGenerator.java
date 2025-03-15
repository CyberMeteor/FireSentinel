package com.firesentinel.dataprocessing.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.time.Instant;
import java.util.Enumeration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Utility class for generating Snowflake IDs.
 * 
 * Snowflake IDs are 64-bit unique identifiers composed of:
 * - 41 bits for timestamp (milliseconds since epoch)
 * - 10 bits for machine ID (configurable, defaults to last 10 bits of MAC address)
 * - 5 bits for device type ID (allows for 32 different device types)
 * - 8 bits for sequence number (allows for 256 IDs per millisecond per machine per device type)
 */
@Component
@Slf4j
public class SnowflakeIdGenerator {

    // Constants for bit shifting
    private static final long EPOCH = 1672531200000L; // 2023-01-01 00:00:00 UTC
    private static final long TIMESTAMP_BITS = 41L;
    private static final long MACHINE_ID_BITS = 10L;
    private static final long DEVICE_TYPE_BITS = 5L;
    private static final long SEQUENCE_BITS = 8L;
    
    // Maximum values
    private static final long MAX_MACHINE_ID = (1L << MACHINE_ID_BITS) - 1;
    private static final long MAX_DEVICE_TYPE_ID = (1L << DEVICE_TYPE_BITS) - 1;
    private static final long MAX_SEQUENCE = (1L << SEQUENCE_BITS) - 1;
    
    // Bit shift amounts
    private static final long MACHINE_ID_SHIFT = DEVICE_TYPE_BITS + SEQUENCE_BITS;
    private static final long TIMESTAMP_SHIFT = MACHINE_ID_BITS + DEVICE_TYPE_BITS + SEQUENCE_BITS;
    private static final long DEVICE_TYPE_SHIFT = SEQUENCE_BITS;
    
    @Value("${snowflake.machine-id:-1}")
    private long machineId;
    
    private final AtomicLong lastTimestamp = new AtomicLong(-1L);
    private final AtomicLong sequence = new AtomicLong(0L);
    
    /**
     * Initializes the Snowflake ID generator.
     * If no machine ID is configured, tries to derive one from the MAC address.
     */
    @PostConstruct
    public void init() {
        if (machineId < 0) {
            machineId = deriveMachineId();
        }
        
        machineId = machineId & MAX_MACHINE_ID;
        log.info("Initialized Snowflake ID generator with machine ID: {}", machineId);
    }
    
    /**
     * Generates a Snowflake ID for a device.
     *
     * @param deviceTypeId The device type ID (0-31)
     * @return A unique Snowflake ID
     */
    public synchronized long generateId(int deviceTypeId) {
        if (deviceTypeId < 0 || deviceTypeId > MAX_DEVICE_TYPE_ID) {
            throw new IllegalArgumentException("Device type ID must be between 0 and " + MAX_DEVICE_TYPE_ID);
        }
        
        long currentTimestamp = getCurrentTimestamp();
        
        // If clock moved backwards, throw an exception
        if (currentTimestamp < lastTimestamp.get()) {
            throw new RuntimeException(String.format("Clock moved backwards. Refusing to generate ID for %d milliseconds",
                    lastTimestamp.get() - currentTimestamp));
        }
        
        // If same timestamp, increment sequence
        if (currentTimestamp == lastTimestamp.get()) {
            sequence.set((sequence.get() + 1) & MAX_SEQUENCE);
            // If sequence overflows, wait for next millisecond
            if (sequence.get() == 0) {
                currentTimestamp = waitForNextMillis(currentTimestamp);
            }
        } else {
            // Reset sequence for new millisecond
            sequence.set(0L);
        }
        
        lastTimestamp.set(currentTimestamp);
        
        // Compose the Snowflake ID
        return ((currentTimestamp - EPOCH) << TIMESTAMP_SHIFT) |
               (machineId << MACHINE_ID_SHIFT) |
               (deviceTypeId << DEVICE_TYPE_SHIFT) |
               sequence.get();
    }
    
    /**
     * Extracts the timestamp from a Snowflake ID.
     *
     * @param id The Snowflake ID
     * @return The timestamp in milliseconds since epoch
     */
    public long extractTimestamp(long id) {
        return ((id >> TIMESTAMP_SHIFT) + EPOCH);
    }
    
    /**
     * Extracts the machine ID from a Snowflake ID.
     *
     * @param id The Snowflake ID
     * @return The machine ID
     */
    public long extractMachineId(long id) {
        return (id >> MACHINE_ID_SHIFT) & MAX_MACHINE_ID;
    }
    
    /**
     * Extracts the device type ID from a Snowflake ID.
     *
     * @param id The Snowflake ID
     * @return The device type ID
     */
    public int extractDeviceTypeId(long id) {
        return (int) ((id >> DEVICE_TYPE_SHIFT) & MAX_DEVICE_TYPE_ID);
    }
    
    /**
     * Extracts the sequence number from a Snowflake ID.
     *
     * @param id The Snowflake ID
     * @return The sequence number
     */
    public long extractSequence(long id) {
        return id & MAX_SEQUENCE;
    }
    
    /**
     * Gets the current timestamp in milliseconds.
     *
     * @return The current timestamp
     */
    private long getCurrentTimestamp() {
        return Instant.now().toEpochMilli();
    }
    
    /**
     * Waits until the next millisecond.
     *
     * @param lastTimestamp The last timestamp
     * @return The next timestamp
     */
    private long waitForNextMillis(long lastTimestamp) {
        long timestamp = getCurrentTimestamp();
        while (timestamp <= lastTimestamp) {
            timestamp = getCurrentTimestamp();
        }
        return timestamp;
    }
    
    /**
     * Derives a machine ID from the MAC address.
     *
     * @return A machine ID
     */
    private long deriveMachineId() {
        try {
            Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();
            while (networkInterfaces.hasMoreElements()) {
                NetworkInterface networkInterface = networkInterfaces.nextElement();
                byte[] mac = networkInterface.getHardwareAddress();
                if (mac != null) {
                    // Use the last 10 bits of the MAC address
                    long id = ((mac[4] & 0xFF) << 8) | (mac[5] & 0xFF);
                    return id & MAX_MACHINE_ID;
                }
            }
        } catch (SocketException e) {
            log.warn("Failed to derive machine ID from MAC address", e);
        }
        
        // Fallback to a random number
        return (long) (Math.random() * MAX_MACHINE_ID);
    }
} 