package com.firesentinel.alarmsystem.service;

import com.firesentinel.alarmsystem.model.AlarmEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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

    /**
     * Distributes an alarm event to all notification channels.
     *
     * @param alarmEvent The alarm event to distribute
     */
    public void distributeAlarm(AlarmEvent alarmEvent) {
        log.debug("Distributing alarm event: {}", alarmEvent.getId());
        
        try {
            // Store the alarm event in the history
            alarmHistoryService.storeAlarmEvent(alarmEvent);
            
            // Send WebSocket notification
            alarmNotificationService.sendAlarmNotification(alarmEvent);
            
            // Send MQTT notification
            mqttAlarmService.sendAlarmNotification(alarmEvent);
            
            // Push the alarm update through the data sync service
            dataSyncService.pushAlarmUpdate(alarmEvent);
            
            log.info("Alarm event distributed successfully: {}", alarmEvent.getId());
        } catch (Exception e) {
            log.error("Failed to distribute alarm event: {}", e.getMessage(), e);
        }
    }
} 