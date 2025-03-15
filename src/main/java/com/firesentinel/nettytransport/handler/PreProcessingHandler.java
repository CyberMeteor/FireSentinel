package com.firesentinel.nettytransport.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Netty handler for pre-processing device data.
 * Filters out invalid or trivial data to reduce unnecessary data transfer.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ChannelHandler.Sharable
public class PreProcessingHandler extends ChannelInboundHandlerAdapter {

    private final ObjectMapper objectMapper;
    
    // Counters for monitoring
    private final AtomicInteger totalPackets = new AtomicInteger(0);
    private final AtomicInteger filteredPackets = new AtomicInteger(0);
    
    // Thresholds for filtering
    private static final double TEMPERATURE_CHANGE_THRESHOLD = 0.5; // degrees
    private static final double HUMIDITY_CHANGE_THRESHOLD = 1.0; // percent
    private static final double SMOKE_LEVEL_THRESHOLD = 5.0; // ppm
    private static final double CO_LEVEL_THRESHOLD = 5.0; // ppm
    
    // Last values for each device and sensor type
    private final java.util.Map<String, java.util.Map<String, Double>> lastValues = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        String content = (String) msg;
        
        try {
            JsonNode json = objectMapper.readTree(content);
            
            // Only process data messages
            if (json.has("type") && "data".equals(json.get("type").asText())) {
                boolean shouldProcess = preprocessData(ctx, json);
                
                if (shouldProcess) {
                    // Pass the message to the next handler
                    ctx.fireChannelRead(content);
                } else {
                    // Log that we're filtering this packet
                    log.debug("Filtered trivial data packet");
                }
            } else {
                // Pass non-data messages to the next handler
                ctx.fireChannelRead(msg);
            }
        } catch (Exception e) {
            log.error("Error pre-processing data: {}", e.getMessage());
            ctx.fireChannelRead(msg);
        }
    }

    /**
     * Pre-processes device data to filter out trivial or invalid data.
     *
     * @param ctx The channel handler context
     * @param json The data message
     * @return true if the data should be processed, false if it should be filtered out
     */
    private boolean preprocessData(ChannelHandlerContext ctx, JsonNode json) {
        try {
            String deviceId = ctx.channel().attr(DeviceAuthHandler.DEVICE_ID).get();
            if (deviceId == null) {
                return false;
            }
            
            totalPackets.incrementAndGet();
            
            // Check for invalid data
            if (!isValidData(json)) {
                filteredPackets.incrementAndGet();
                return false;
            }
            
            // Check for trivial changes
            if (isTrivialChange(deviceId, json)) {
                filteredPackets.incrementAndGet();
                return false;
            }
            
            // Add metadata to the message
            if (json instanceof ObjectNode) {
                ObjectNode objectNode = (ObjectNode) json;
                objectNode.put("preprocessed", true);
                objectNode.put("preprocessedAt", System.currentTimeMillis());
            }
            
            // Log filtering statistics periodically
            if (totalPackets.get() % 100 == 0) {
                double filterRate = (double) filteredPackets.get() / totalPackets.get() * 100;
                log.info("Pre-processing filter rate: {}% ({}/{} packets filtered)", 
                        String.format("%.2f", filterRate), 
                        filteredPackets.get(), 
                        totalPackets.get());
            }
            
            return true;
        } catch (Exception e) {
            log.error("Error in pre-processing: {}", e.getMessage());
            return true; // In case of error, let the data through
        }
    }

    /**
     * Checks if the data is valid.
     *
     * @param json The data message
     * @return true if the data is valid, false otherwise
     */
    private boolean isValidData(JsonNode json) {
        // Check if required fields are present
        if (!json.has("readings") || !json.get("readings").isArray()) {
            return false;
        }
        
        // Check each reading
        for (JsonNode reading : json.get("readings")) {
            if (!reading.has("type") || !reading.has("value")) {
                return false;
            }
            
            // Check for invalid values based on sensor type
            String type = reading.get("type").asText();
            double value = reading.get("value").asDouble();
            
            switch (type) {
                case "temperature":
                    // Valid temperature range: -50°C to 100°C
                    if (value < -50 || value > 100) {
                        return false;
                    }
                    break;
                case "humidity":
                    // Valid humidity range: 0% to 100%
                    if (value < 0 || value > 100) {
                        return false;
                    }
                    break;
                case "smoke":
                    // Valid smoke level: non-negative
                    if (value < 0) {
                        return false;
                    }
                    break;
                case "co":
                    // Valid CO level: non-negative
                    if (value < 0) {
                        return false;
                    }
                    break;
                default:
                    // Unknown sensor type
                    return false;
            }
        }
        
        return true;
    }

    /**
     * Checks if the data represents a trivial change.
     *
     * @param deviceId The device ID
     * @param json The data message
     * @return true if the change is trivial, false otherwise
     */
    private boolean isTrivialChange(String deviceId, JsonNode json) {
        boolean allTrivial = true;
        
        // Get or create the device's last values map
        java.util.Map<String, Double> deviceLastValues = lastValues.computeIfAbsent(
                deviceId, k -> new java.util.concurrent.ConcurrentHashMap<>());
        
        // Check each reading
        for (JsonNode reading : json.get("readings")) {
            String type = reading.get("type").asText();
            double value = reading.get("value").asDouble();
            
            // Get the last value for this sensor type
            Double lastValue = deviceLastValues.get(type);
            if (lastValue == null) {
                // First reading for this sensor type, not trivial
                deviceLastValues.put(type, value);
                allTrivial = false;
                continue;
            }
            
            // Check if the change is significant based on sensor type
            boolean isTrivial = false;
            switch (type) {
                case "temperature":
                    isTrivial = Math.abs(value - lastValue) < TEMPERATURE_CHANGE_THRESHOLD;
                    break;
                case "humidity":
                    isTrivial = Math.abs(value - lastValue) < HUMIDITY_CHANGE_THRESHOLD;
                    break;
                case "smoke":
                    // For smoke, any value above threshold is not trivial
                    isTrivial = value < SMOKE_LEVEL_THRESHOLD && lastValue < SMOKE_LEVEL_THRESHOLD;
                    break;
                case "co":
                    // For CO, any value above threshold is not trivial
                    isTrivial = value < CO_LEVEL_THRESHOLD && lastValue < CO_LEVEL_THRESHOLD;
                    break;
                default:
                    isTrivial = true;
            }
            
            // Update the last value
            deviceLastValues.put(type, value);
            
            // If any reading is not trivial, the whole message is not trivial
            if (!isTrivial) {
                allTrivial = false;
            }
        }
        
        return allTrivial;
    }
} 