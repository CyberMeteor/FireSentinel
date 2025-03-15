package com.firesentinel.deviceauth.controller;

import com.firesentinel.deviceauth.model.Device;
import com.firesentinel.deviceauth.service.DeviceService;
import com.firesentinel.deviceauth.service.DeviceTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for device authentication endpoints.
 * Handles token issuance, validation, and refresh.
 */
@RestController
@RequestMapping("/api/device/auth")
@RequiredArgsConstructor
@Slf4j
public class DeviceAuthController {

    private final DeviceTokenService deviceTokenService;
    private final DeviceService deviceService;

    /**
     * Issues a token for a device.
     *
     * @param request The authentication request containing device ID and API key
     * @return A response containing the access token and refresh token
     */
    @PostMapping("/token")
    public ResponseEntity<Map<String, String>> issueToken(@RequestBody Map<String, String> request) {
        String deviceId = request.get("deviceId");
        String apiKey = request.get("apiKey");
        
        if (deviceId == null || apiKey == null) {
            return ResponseEntity.badRequest().build();
        }
        
        try {
            Map<String, String> tokens = deviceTokenService.issueToken(deviceId, apiKey);
            return ResponseEntity.ok(tokens);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to issue token: {}", e.getMessage());
            return ResponseEntity.status(401).build();
        }
    }
    
    /**
     * Refreshes a token for a device.
     *
     * @param request The refresh request containing the refresh token
     * @return A response containing the new access token and refresh token
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refreshToken(@RequestBody Map<String, String> request) {
        String refreshToken = request.get("refresh_token");
        
        if (refreshToken == null) {
            return ResponseEntity.badRequest().build();
        }
        
        try {
            Map<String, String> tokens = deviceTokenService.refreshToken(refreshToken);
            return ResponseEntity.ok(tokens);
        } catch (IllegalArgumentException e) {
            log.warn("Failed to refresh token: {}", e.getMessage());
            return ResponseEntity.status(401).build();
        }
    }
    
    /**
     * Validates a token.
     *
     * @param token The token to validate
     * @return A response indicating whether the token is valid
     */
    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validateToken(@RequestParam String token) {
        String deviceId = deviceTokenService.validateToken(token);
        
        if (deviceId == null) {
            return ResponseEntity.status(401).build();
        }
        
        Device device = deviceService.findByDeviceId(deviceId);
        if (device == null || !device.isEnabled()) {
            return ResponseEntity.status(401).build();
        }
        
        return ResponseEntity.ok(Map.of(
            "valid", true,
            "deviceId", deviceId,
            "deviceName", device.getName(),
            "deviceType", device.getType()
        ));
    }
    
    /**
     * Revokes all tokens for a device.
     *
     * @param deviceId The device ID
     * @return A response indicating success
     */
    @PostMapping("/revoke")
    public ResponseEntity<Void> revokeTokens(@RequestParam String deviceId) {
        deviceTokenService.revokeTokens(deviceId);
        return ResponseEntity.ok().build();
    }
} 