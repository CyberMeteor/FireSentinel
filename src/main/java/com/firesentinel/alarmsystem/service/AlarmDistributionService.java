package com.firesentinel.alarmsystem.service;

import com.firesentinel.alarmsystem.model.AlarmEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Service for distributing alarm events to various notification channels.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlarmDistributionService {

    private final AlarmNotificationService alarmNotificationService;
    private final MqttAlarmService mqttAlarmService;
    private final AlarmHistoryService alarmHistoryService;
    private final DataSyncService dataSyncService;
    
    // Metrics
    private final Counter alarmEventsCounter;
    private final Counter alarmEventsBySeverityCounter;
    private final Timer alarmProcessingTimer;
    private final MeterRegistry meterRegistry;

    /**
     * Distributes an alarm event to all notification channels.
     *
     * @param alarmEvent The alarm event to distribute
     */
    public void distributeAlarm(AlarmEvent alarmEvent) {
        log.debug("Distributing alarm event: {}", alarmEvent.getId());
        
        // Start timing the alarm processing
        Timer.Sample sample = Timer.start(meterRegistry);
        
        try {
            // Store the alarm event in the history
            alarmHistoryService.storeAlarmEvent(alarmEvent);
            
            // Send WebSocket notification
            alarmNotificationService.sendAlarmNotification(alarmEvent);
            
            // Send MQTT notification
            mqttAlarmService.sendAlarmNotification(alarmEvent);
            
            // Push the alarm update through the data sync service
            dataSyncService.pushAlarmUpdate(alarmEvent);
            
            // Increment metrics
            alarmEventsCounter.increment();
            
            // Increment severity-specific counter using tags
            meterRegistry.counter("firesentinel.alarms.by.severity", 
                    Tags.of(Tag.of("severity", alarmEvent.getSeverity().toString())))
                    .increment();
            
            // Record device-specific metrics using tags
            meterRegistry.counter("firesentinel.alarms.by.device", 
                    Tags.of(
                            Tag.of("deviceId", alarmEvent.getDeviceId()),
                            Tag.of("severity", alarmEvent.getSeverity().toString()),
                            Tag.of("type", alarmEvent.getType())
                    ))
                    .increment();
            
            // Record distribution success
            meterRegistry.counter("firesentinel.alarms.distribution.success").increment();
            
            log.info("Alarm event distributed successfully: {}", alarmEvent.getId());
        } catch (Exception e) {
            // Record distribution failures with the exception type as a tag
            meterRegistry.counter("firesentinel.alarms.distribution.failures",
                    Tags.of(Tag.of("reason", e.getClass().getSimpleName())))
                    .increment();
            
            log.error("Failed to distribute alarm event: {}", e.getMessage(), e);
        } finally {
            // Stop timing and record the duration with tags
            sample.stop(meterRegistry.timer("firesentinel.alarms.processing.time", 
                    Tags.of(
                            Tag.of("severity", alarmEvent.getSeverity().toString()),
                            Tag.of("type", alarmEvent.getType())
                    )));
        }
    }
} 