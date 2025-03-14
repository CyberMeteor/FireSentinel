package com.firesentinel.alarmsystem.config;

import com.espertech.esper.common.client.configuration.Configuration;
import com.espertech.esper.runtime.client.EPRuntime;
import com.espertech.esper.runtime.client.EPRuntimeProvider;
import org.springframework.context.annotation.Bean;

/**
 * Configuration class for the alarm system and Complex Event Processing (CEP) engine.
 * Sets up the Esper CEP engine for real-time event processing and alarm detection.
 */
@org.springframework.context.annotation.Configuration
public class AlarmConfig {

    /**
     * Configures and creates the Esper runtime for Complex Event Processing.
     * 
     * @return The configured Esper runtime
     */
    @Bean
    public EPRuntime esperRuntime() {
        Configuration configuration = new Configuration();
        
        // Register event types for the CEP engine
        configuration.getCommon().addEventType("SensorDataEvent", 
                "com.firesentinel.dataprocessing.model.SensorDataEvent");
        configuration.getCommon().addEventType("AlarmEvent", 
                "com.firesentinel.alarmsystem.model.AlarmEvent");
        
        // Create and return the Esper runtime
        return EPRuntimeProvider.getDefaultRuntime(configuration);
    }
} 