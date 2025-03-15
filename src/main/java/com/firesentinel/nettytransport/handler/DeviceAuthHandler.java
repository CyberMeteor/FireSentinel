package com.firesentinel.nettytransport.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firesentinel.deviceauth.service.DeviceTokenService;
import com.firesentinel.nettytransport.manager.ChannelManager;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.AttributeKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Netty handler for device authentication.
 * Validates device tokens and maintains a mapping of deviceId -> channel.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ChannelHandler.Sharable
public class DeviceAuthHandler extends ChannelInboundHandlerAdapter {

    private final DeviceTokenService deviceTokenService;
    private final ChannelManager channelManager;
    private final ObjectMapper objectMapper;

    // Attribute key for storing the device ID in the channel
    public static final AttributeKey<String> DEVICE_ID = AttributeKey.valueOf("deviceId");
    // Attribute key for storing the authentication status in the channel
    public static final AttributeKey<Boolean> AUTHENTICATED = AttributeKey.valueOf("authenticated");

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        String content = (String) msg;
        
        try {
            JsonNode json = objectMapper.readTree(content);
            
            // Check if this is an authentication message
            if (json.has("type") && "auth".equals(json.get("type").asText())) {
                handleAuthentication(ctx, json);
                return;
            }
            
            // Check if the device is authenticated
            if (!isAuthenticated(ctx)) {
                log.warn("Received message from unauthenticated device, closing connection");
                ctx.close();
                return;
            }
            
            // Pass the message to the next handler
            ctx.fireChannelRead(msg);
        } catch (Exception e) {
            log.error("Error processing message: {}", e.getMessage());
            ctx.close();
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        // Remove the channel from the channel manager when the connection is closed
        String deviceId = ctx.channel().attr(DEVICE_ID).get();
        if (deviceId != null) {
            channelManager.removeChannel(deviceId);
            log.info("Device disconnected: {}", deviceId);
        }
        
        ctx.fireChannelInactive();
    }

    /**
     * Handles device authentication.
     *
     * @param ctx The channel handler context
     * @param json The authentication message
     */
    private void handleAuthentication(ChannelHandlerContext ctx, JsonNode json) {
        try {
            String token = json.get("token").asText();
            String deviceId = deviceTokenService.validateToken(token);
            
            if (deviceId != null) {
                // Store the device ID in the channel attributes
                ctx.channel().attr(DEVICE_ID).set(deviceId);
                ctx.channel().attr(AUTHENTICATED).set(true);
                
                // Register the channel in the channel manager
                channelManager.addChannel(deviceId, ctx.channel());
                
                log.info("Device authenticated: {}", deviceId);
                
                // Send authentication success response
                String response = objectMapper.writeValueAsString(
                        Map.of("type", "auth_response", "status", "success"));
                ctx.writeAndFlush(response);
            } else {
                // Invalid token
                log.warn("Authentication failed: Invalid token");
                String response = objectMapper.writeValueAsString(
                        Map.of("type", "auth_response", "status", "failure", "reason", "Invalid token"));
                ctx.writeAndFlush(response);
                ctx.close();
            }
        } catch (Exception e) {
            log.error("Authentication error: {}", e.getMessage());
            ctx.close();
        }
    }

    /**
     * Checks if the channel is authenticated.
     *
     * @param ctx The channel handler context
     * @return true if the channel is authenticated, false otherwise
     */
    private boolean isAuthenticated(ChannelHandlerContext ctx) {
        Boolean authenticated = ctx.channel().attr(AUTHENTICATED).get();
        return authenticated != null && authenticated;
    }
} 