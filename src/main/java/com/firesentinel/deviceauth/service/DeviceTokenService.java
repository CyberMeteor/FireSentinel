package com.firesentinel.deviceauth.service;

import com.firesentinel.deviceauth.model.Device;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Service for managing device authentication tokens.
 * Handles token issuance, validation, and refresh.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeviceTokenService {

    private final RegisteredClientRepository clientRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final DeviceService deviceService;

    private static final String TOKEN_PREFIX = "device:token:";
    private static final String DEVICE_PREFIX = "device:info:";
    private static final Duration TOKEN_VALIDITY = Duration.ofMinutes(5);
    private static final Duration REFRESH_TOKEN_VALIDITY = Duration.ofDays(1);

    /**
     * Issues a new token for a device.
     *
     * @param deviceId The unique identifier of the device
     * @param apiKey The API key of the device for authentication
     * @return A map containing the access token and refresh token
     */
    public Map<String, String> issueToken(String deviceId, String apiKey) {
        // Validate device credentials
        Device device = deviceService.findByDeviceIdAndApiKey(deviceId, apiKey);
        if (device == null || !device.isEnabled()) {
            throw new IllegalArgumentException("Invalid device credentials or device is disabled");
        }

        // Generate tokens
        String accessToken = generateToken();
        String refreshToken = generateToken();
        Instant now = Instant.now();
        
        // Store token information in Redis
        Map<String, Object> tokenInfo = new HashMap<>();
        tokenInfo.put("deviceId", deviceId);
        tokenInfo.put("issuedAt", now.toString());
        tokenInfo.put("expiresAt", now.plus(TOKEN_VALIDITY).toString());
        tokenInfo.put("refreshToken", refreshToken);
        
        redisTemplate.opsForValue().set(TOKEN_PREFIX + accessToken, tokenInfo, TOKEN_VALIDITY.getSeconds(), TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(TOKEN_PREFIX + "refresh:" + refreshToken, deviceId, REFRESH_TOKEN_VALIDITY.getSeconds(), TimeUnit.SECONDS);
        
        // Update device status
        updateDeviceStatus(device, true);
        
        // Return tokens to client
        Map<String, String> tokens = new HashMap<>();
        tokens.put("access_token", accessToken);
        tokens.put("refresh_token", refreshToken);
        tokens.put("expires_in", String.valueOf(TOKEN_VALIDITY.getSeconds()));
        tokens.put("token_type", "Bearer");
        
        log.debug("Issued new token for device: {}", deviceId);
        return tokens;
    }
    
    /**
     * Validates an access token.
     *
     * @param token The access token to validate
     * @return The device ID if the token is valid, null otherwise
     */
    public String validateToken(String token) {
        Object tokenInfo = redisTemplate.opsForValue().get(TOKEN_PREFIX + token);
        if (tokenInfo == null) {
            return null;
        }
        
        @SuppressWarnings("unchecked")
        Map<String, Object> tokenData = (Map<String, Object>) tokenInfo;
        String deviceId = (String) tokenData.get("deviceId");
        
        // Update last seen timestamp
        Device device = deviceService.findByDeviceId(deviceId);
        if (device != null) {
            updateDeviceStatus(device, true);
        }
        
        return deviceId;
    }
    
    /**
     * Refreshes an access token using a refresh token.
     *
     * @param refreshToken The refresh token
     * @return A new access token if the refresh token is valid
     */
    public Map<String, String> refreshToken(String refreshToken) {
        String deviceId = (String) redisTemplate.opsForValue().get(TOKEN_PREFIX + "refresh:" + refreshToken);
        if (deviceId == null) {
            throw new IllegalArgumentException("Invalid refresh token");
        }
        
        // Delete the old refresh token
        redisTemplate.delete(TOKEN_PREFIX + "refresh:" + refreshToken);
        
        // Generate new tokens
        String newAccessToken = generateToken();
        String newRefreshToken = generateToken();
        Instant now = Instant.now();
        
        // Store new token information
        Map<String, Object> tokenInfo = new HashMap<>();
        tokenInfo.put("deviceId", deviceId);
        tokenInfo.put("issuedAt", now.toString());
        tokenInfo.put("expiresAt", now.plus(TOKEN_VALIDITY).toString());
        tokenInfo.put("refreshToken", newRefreshToken);
        
        redisTemplate.opsForValue().set(TOKEN_PREFIX + newAccessToken, tokenInfo, TOKEN_VALIDITY.getSeconds(), TimeUnit.SECONDS);
        redisTemplate.opsForValue().set(TOKEN_PREFIX + "refresh:" + newRefreshToken, deviceId, REFRESH_TOKEN_VALIDITY.getSeconds(), TimeUnit.SECONDS);
        
        // Update device status
        Device device = deviceService.findByDeviceId(deviceId);
        if (device != null) {
            updateDeviceStatus(device, true);
        }
        
        // Return new tokens
        Map<String, String> tokens = new HashMap<>();
        tokens.put("access_token", newAccessToken);
        tokens.put("refresh_token", newRefreshToken);
        tokens.put("expires_in", String.valueOf(TOKEN_VALIDITY.getSeconds()));
        tokens.put("token_type", "Bearer");
        
        log.debug("Refreshed token for device: {}", deviceId);
        return tokens;
    }
    
    /**
     * Revokes all tokens for a device.
     *
     * @param deviceId The device ID
     */
    public void revokeTokens(String deviceId) {
        // In a real implementation, we would need to find all tokens for this device
        // and delete them from Redis. For simplicity, we're just updating the device status.
        Device device = deviceService.findByDeviceId(deviceId);
        if (device != null) {
            updateDeviceStatus(device, false);
        }
    }
    
    /**
     * Updates the device status in Redis.
     *
     * @param device The device
     * @param connected Whether the device is connected
     */
    private void updateDeviceStatus(Device device, boolean connected) {
        Map<String, Object> deviceStatus = new HashMap<>();
        deviceStatus.put("connected", connected);
        deviceStatus.put("lastHeartbeat", Instant.now().toString());
        deviceStatus.put("deviceId", device.getDeviceId());
        deviceStatus.put("name", device.getName());
        deviceStatus.put("type", device.getType());
        
        redisTemplate.opsForValue().set(DEVICE_PREFIX + device.getDeviceId(), deviceStatus);
        
        // Update the device in the database
        device.setLastConnectionDate(java.time.LocalDateTime.now());
        deviceService.save(device);
    }
    
    /**
     * Generates a random token.
     *
     * @return A random UUID string
     */
    private String generateToken() {
        return UUID.randomUUID().toString();
    }
} 