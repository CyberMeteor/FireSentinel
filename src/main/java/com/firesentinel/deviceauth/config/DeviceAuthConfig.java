package com.firesentinel.deviceauth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Configuration class for device authentication.
 * Sets up the necessary components for device authentication and authorization.
 */
@Configuration
public class DeviceAuthConfig {

    /**
     * Creates a password encoder for secure storage of device credentials.
     * 
     * @return The BCrypt password encoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
} 