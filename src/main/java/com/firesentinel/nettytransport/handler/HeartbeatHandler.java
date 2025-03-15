package com.firesentinel.nettytransport.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firesentinel.deviceauth.service.DeviceService;
import com.firesentinel.nettytransport.manager.ChannelManager;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Netty handler for device heartbeats.
 * Handles heartbeat messages and idle connection detection.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ChannelHandler.Sharable
public class HeartbeatHandler extends ChannelInboundHandlerAdapter {

    private final ObjectMapper objectMapper;
    private final ChannelManager channelManager;
    private final RedisTemplate<String, Object> redisTemplate;
    private final DeviceService deviceService;

    private static final String DEVICE_STATUS_PREFIX = "device:status:";
    private static final int HEARTBEAT_EXPIRY_SECONDS = 30; // 30 seconds

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        String content = (String) msg;
        
        try {
            JsonNode json = objectMapper.readTree(content);
            
            // Check if this is a heartbeat message
            if (json.has("type") && "heartbeat".equals(json.get("type").asText())) {
                handleHeartbeat(ctx, json);
                return;
            }
            
            // Pass the message to the next handler
            ctx.fireChannelRead(msg);
        } catch (Exception e) {
            log.error("Error processing heartbeat: {}", e.getMessage());
            ctx.fireChannelRead(msg);
        }
    }

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        if (evt instanceof IdleStateEvent) {
            IdleStateEvent event = (IdleStateEvent) evt;
            if (event.state() == IdleState.READER_IDLE) {
                // No data received for the configured idle time (10 seconds)
                String deviceId = ctx.channel().attr(DeviceAuthHandler.DEVICE_ID).get();
                if (deviceId != null) {
                    log.info("Closing idle connection for device: {}", deviceId);
                    updateDeviceStatus(deviceId, false);
                    channelManager.removeChannel(deviceId);
                }
                ctx.close();
                return;
            }
        }
        
        ctx.fireUserEventTriggered(evt);
    }

    /**
     * Handles a heartbeat message.
     *
     * @param ctx The channel handler context
     * @param json The heartbeat message
     */
    private void handleHeartbeat(ChannelHandlerContext ctx, JsonNode json) {
        try {
            String deviceId = ctx.channel().attr(DeviceAuthHandler.DEVICE_ID).get();
            if (deviceId != null) {
                // Update device status in Redis
                updateDeviceStatus(deviceId, true);
                
                // Send heartbeat response
                String response = objectMapper.writeValueAsString(
                        Map.of("type", "heartbeat_response", "timestamp", Instant.now().toString()));
                ctx.writeAndFlush(response);
                
                log.debug("Heartbeat received from device: {}", deviceId);
            }
        } catch (Exception e) {
            log.error("Error handling heartbeat: {}", e.getMessage());
        }
    }

    /**
     * Updates the device status in Redis.
     *
     * @param deviceId The device ID
     * @param connected Whether the device is connected
     */
    private void updateDeviceStatus(String deviceId, boolean connected) {
        Map<String, Object> status = Map.of(
            "deviceId", deviceId,
            "connected", connected,
            "lastHeartbeat", Instant.now().toString()
        );
        
        redisTemplate.opsForValue().set(
            DEVICE_STATUS_PREFIX + deviceId, 
            status, 
            HEARTBEAT_EXPIRY_SECONDS, 
            TimeUnit.SECONDS
        );
    }
} 