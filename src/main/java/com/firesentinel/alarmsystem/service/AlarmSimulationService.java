package com.firesentinel.alarmsystem.service;

import com.firesentinel.alarmsystem.model.AlarmEvent;
import com.firesentinel.alarmsystem.model.AlarmSeverity;
import com.firesentinel.alarmsystem.model.Device;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.annotations.WithSpan;
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
    private final Tracer tracer;
    
    private final Random random = new Random();
    
    // Define attribute keys
    private static final AttributeKey<Long> DEVICES_COUNT = AttributeKey.longKey("devices.count");
    private static final AttributeKey<String> SIMULATION_STATUS = AttributeKey.stringKey("simulation.status");
    private static final AttributeKey<String> SELECTED_DEVICE_ID = AttributeKey.stringKey("selected.device.id");
    private static final AttributeKey<String> SELECTED_DEVICE_TYPE = AttributeKey.stringKey("selected.device.type");
    private static final AttributeKey<String> ALARM_ID = AttributeKey.stringKey("alarm.id");
    private static final AttributeKey<String> ALARM_SEVERITY = AttributeKey.stringKey("alarm.severity");
    private static final AttributeKey<String> ALARM_TYPE = AttributeKey.stringKey("alarm.type");
    private static final AttributeKey<String> DEVICE_TYPE = AttributeKey.stringKey("device.type");
    private static final AttributeKey<String> POSSIBLE_ALARM_TYPES = AttributeKey.stringKey("possible.alarm.types");
    private static final AttributeKey<String> SELECTED_ALARM_TYPE = AttributeKey.stringKey("selected.alarm.type");
    private static final AttributeKey<String> SELECTED_ALARM_SEVERITY = AttributeKey.stringKey("selected.alarm.severity");
    private static final AttributeKey<String> GENERATED_ALARM_ID = AttributeKey.stringKey("generated.alarm.id");
    private static final AttributeKey<String> DEVICE_ID = AttributeKey.stringKey("device.id");
    private static final AttributeKey<String> DEVICE_NAME = AttributeKey.stringKey("device.name");
    private static final AttributeKey<String> DEVICE_ZONE = AttributeKey.stringKey("device.zone");
    private static final AttributeKey<String> GENERATED_MESSAGE = AttributeKey.stringKey("generated.message");
    
    /**
     * Simulates a random alarm event every 10 seconds.
     */
    @Scheduled(fixedRate = 10000)
    @WithSpan("simulateRandomAlarm")
    public void simulateRandomAlarm() {
        Span span = tracer.spanBuilder("simulateRandomAlarm").startSpan();
        try (Scope scope = span.makeCurrent()) {
            // Get all devices
            List<Device> devices = deviceService.getAllDevices();
            span.setAttribute(DEVICES_COUNT, (long) devices.size());
            
            // If no devices, return
            if (devices.isEmpty()) {
                span.setAttribute(SIMULATION_STATUS, "skipped");
                return;
            }
            
            // Select a random device
            Device device = devices.get(random.nextInt(devices.size()));
            span.setAttribute(SELECTED_DEVICE_ID, device.getId());
            span.setAttribute(SELECTED_DEVICE_TYPE, device.getType());
            
            // Generate a random alarm event
            AlarmEvent alarmEvent = generateRandomAlarmEvent(device);
            span.setAttribute(ALARM_ID, alarmEvent.getId());
            span.setAttribute(ALARM_SEVERITY, alarmEvent.getSeverity().toString());
            span.setAttribute(ALARM_TYPE, alarmEvent.getType());
            
            // Distribute the alarm event
            alarmDistributionService.distributeAlarm(alarmEvent);
            
            span.setAttribute(SIMULATION_STATUS, "completed");
            log.info("Simulated alarm event: {} for device: {}", alarmEvent.getType(), device.getId());
        } finally {
            span.end();
        }
    }
    
    /**
     * Generates a random alarm event for a device.
     *
     * @param device The device to generate an alarm for
     * @return The generated alarm event
     */
    @WithSpan("generateRandomAlarmEvent")
    private AlarmEvent generateRandomAlarmEvent(Device device) {
        Span span = tracer.spanBuilder("generateRandomAlarmEvent").startSpan();
        try (Scope scope = span.makeCurrent()) {
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
            
            span.setAttribute(DEVICE_TYPE, device.getType());
            span.setAttribute(POSSIBLE_ALARM_TYPES, String.join(",", alarmTypes));
            
            // Select a random alarm type
            String alarmType = alarmTypes[random.nextInt(alarmTypes.length)];
            span.setAttribute(SELECTED_ALARM_TYPE, alarmType);
            
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
            
            span.setAttribute(SELECTED_ALARM_SEVERITY, severity.toString());
            
            // Generate a message based on the alarm type
            String message = generateAlarmMessage(alarmType, device);
            
            // Create the alarm event
            AlarmEvent alarmEvent = AlarmEvent.builder()
                    .id(UUID.randomUUID().toString())
                    .timestamp(Instant.now())
                    .deviceId(device.getId())
                    .type(alarmType)
                    .severity(severity)
                    .message(message)
                    .location(device.getFloorId() + " - " + device.getZoneId())
                    .build();
            
            span.setAttribute(GENERATED_ALARM_ID, alarmEvent.getId());
            return alarmEvent;
        } finally {
            span.end();
        }
    }
    
    /**
     * Generates an alarm message based on the alarm type and device.
     *
     * @param alarmType The type of alarm
     * @param device The device that triggered the alarm
     * @return The generated alarm message
     */
    @WithSpan("generateAlarmMessage")
    private String generateAlarmMessage(String alarmType, Device device) {
        Span span = tracer.spanBuilder("generateAlarmMessage").startSpan();
        try (Scope scope = span.makeCurrent()) {
            span.setAttribute(ALARM_TYPE, alarmType);
            span.setAttribute(DEVICE_ID, device.getId());
            span.setAttribute(DEVICE_NAME, device.getName());
            span.setAttribute(DEVICE_ZONE, device.getZoneId());
            
            String message;
            switch (alarmType) {
                case "SMOKE_DETECTED":
                    message = "Smoke detected by " + device.getName() + " in " + device.getZoneId();
                    break;
                case "SMOKE_CLEARED":
                    message = "Smoke cleared at " + device.getName() + " in " + device.getZoneId();
                    break;
                case "HIGH_TEMPERATURE":
                    message = "High temperature detected by " + device.getName() + " in " + device.getZoneId();
                    break;
                case "TEMPERATURE_NORMAL":
                    message = "Temperature returned to normal at " + device.getName() + " in " + device.getZoneId();
                    break;
                case "FIRE_DETECTED":
                    message = "Fire detected by " + device.getName() + " in " + device.getZoneId();
                    break;
                case "FIRE_CLEARED":
                    message = "Fire cleared at " + device.getName() + " in " + device.getZoneId();
                    break;
                case "MOTION_DETECTED":
                    message = "Motion detected by " + device.getName() + " in " + device.getZoneId();
                    break;
                case "MOTION_CLEARED":
                    message = "Motion cleared at " + device.getName() + " in " + device.getZoneId();
                    break;
                default:
                    message = "Alarm " + alarmType + " triggered by " + device.getName() + " in " + device.getZoneId();
                    break;
            }
            
            span.setAttribute(GENERATED_MESSAGE, message);
            return message;
        } finally {
            span.end();
        }
    }
} 