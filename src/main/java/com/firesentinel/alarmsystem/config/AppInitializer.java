package com.firesentinel.alarmsystem.config;

import com.firesentinel.alarmsystem.service.DeviceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Initializes application components on startup.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AppInitializer implements ApplicationRunner {
    
    private final DeviceService deviceService;
    
    @Override
    public void run(ApplicationArguments args) {
        log.info("Initializing FireSentinel application...");
        
        // Initialize sample devices
        deviceService.initSampleDevices();
        
        log.info("FireSentinel application initialized successfully");
    }
} 