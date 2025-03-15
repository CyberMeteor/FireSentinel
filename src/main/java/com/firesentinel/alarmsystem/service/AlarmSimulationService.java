package com.firesentinel.alarmsystem.service;

import com.firesentinel.alarmsystem.model.AlarmEvent;
import com.firesentinel.alarmsystem.model.AlarmSeverity;
import com.firesentinel.alarmsystem.model.Device;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Service for simulating alarm events for testing purposes.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlarmSimulationService {
    
    private final DeviceService deviceService;
    private final AlarmDistributionService alarmDistributionService;
    
    private final Random random = new Random();
    
    /**
     * Simulates a random alarm event every 10 seconds.
     */
    @Scheduled(fixedRate = 10000)
    public void simulateRandomAlarm() {
        // Get all devices
        List<Device> devices = deviceService.getAllDevices();
        
        // If no devices, return
        if (devices.isEmpty()) {
            return;
        }
        
        // Select a random device
        Device device = devices.get(random.nextInt(devices.size()));
        
        // Generate a random alarm event
        AlarmEvent alarmEvent = generateRandomAlarmEvent(device);
        
        // Distribute the alarm event
        alarmDistributionService.distributeAlarm(alarmEvent);
        
        log.info("Simulated alarm event: {} for device: {}", alarmEvent.getType(), device.getId());
    }
    
    /**
     * Generates a random alarm event for a device.
     *
     * @param device The device to generate an alarm for
     * @return The generated alarm event
     */
    private AlarmEvent generateRandomAlarmEvent(Device device) {
        // Define possible alarm types based on device type
        String[] alarmTypes;
        
        switch (device.getType()) {
            case "smoke":
                alarmTypes = new String[]{"SMOKE_DETECTED", "SMOKE_CLEARED"};
                break;
            case "temperature":
                alarmTypes = new String[]{"HIGH_TEMPERATURE", "TEMPERATURE_NORMAL"};
                break;
            case "fire":
                alarmTypes = new String[]{"FIRE_DETECTED", "FIRE_CLEARED"};
                break;
            case "motion":
                alarmTypes = new String[]{"MOTION_DETECTED", "MOTION_CLEARED"};
                break;
            default:
                alarmTypes = new String[]{"DEVICE_ALARM", "DEVICE_NORMAL"};
                break;
        }
        
        // Select a random alarm type
        String alarmType = alarmTypes[random.nextInt(alarmTypes.length)];
        
        // Determine severity based on alarm type
        AlarmSeverity severity;
        if (alarmType.contains("DETECTED") || alarmType.contains("HIGH")) {
            // Higher chance of MEDIUM severity
            int severityRoll = random.nextInt(10);
            if (severityRoll < 2) {
                severity = AlarmSeverity.HIGH;
            } else if (severityRoll < 7) {
                severity = AlarmSeverity.MEDIUM;
            } else {
                severity = AlarmSeverity.LOW;
            }
        } else {
            // Cleared alarms are always LOW severity
            severity = AlarmSeverity.LOW;
        }
        
        // Generate a message based on the alarm type
        String message = generateAlarmMessage(alarmType, device);
        
        // Create the alarm event
        return AlarmEvent.builder()
                .id(UUID.randomUUID().toString())
                .timestamp(Instant.now())
                .deviceId(device.getId())
                .type(alarmType)
                .severity(severity)
                .message(message)
                .location(device.getFloorId() + " - " + device.getZoneId())
                .build();
    }
    
    /**
     * Generates an alarm message based on the alarm type and device.
     *
     * @param alarmType The type of alarm
     * @param device The device that triggered the alarm
     * @return The generated alarm message
     */
    private String generateAlarmMessage(String alarmType, Device device) {
        switch (alarmType) {
            case "SMOKE_DETECTED":
                return "Smoke detected by " + device.getName() + " in " + device.getZoneId();
            case "SMOKE_CLEARED":
                return "Smoke cleared at " + device.getName() + " in " + device.getZoneId();
            case "HIGH_TEMPERATURE":
                return "High temperature detected by " + device.getName() + " in " + device.getZoneId();
            case "TEMPERATURE_NORMAL":
                return "Temperature returned to normal at " + device.getName() + " in " + device.getZoneId();
            case "FIRE_DETECTED":
                return "Fire detected by " + device.getName() + " in " + device.getZoneId();
            case "FIRE_CLEARED":
                return "Fire cleared at " + device.getName() + " in " + device.getZoneId();
            case "MOTION_DETECTED":
                return "Motion detected by " + device.getName() + " in " + device.getZoneId();
            case "MOTION_CLEARED":
                return "Motion cleared at " + device.getName() + " in " + device.getZoneId();
            default:
                return "Alarm " + alarmType + " triggered by " + device.getName() + " in " + device.getZoneId();
        }
    }
} 