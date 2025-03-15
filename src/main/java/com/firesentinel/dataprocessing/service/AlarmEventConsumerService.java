package com.firesentinel.dataprocessing.service;

import com.firesentinel.alarmsystem.model.AlarmSeverity;
import com.firesentinel.alarmsystem.service.AlarmDistributionService;
import com.firesentinel.dataprocessing.model.AlarmEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for consuming alarm events from Kafka.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AlarmEventConsumerService {

    private final FireSuppressionService fireSuppressionService;
    private final AlarmDistributionService alarmDistributionService;
    
    // In-memory storage for active alarms (in a real system, this would be in a database)
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<AlarmEvent>> activeAlarmsByDevice = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<AlarmEvent> activeAlarms = new CopyOnWriteArrayList<>();
    
    // Statistics counters
    private final AtomicLong totalProcessed = new AtomicLong(0);
    private final AtomicLong highSeverityAlarms = new AtomicLong(0);
    private final AtomicLong mediumSeverityAlarms = new AtomicLong(0);
    private final AtomicLong lowSeverityAlarms = new AtomicLong(0);
    private final AtomicLong suppressionActivations = new AtomicLong(0);
    private final AtomicLong errorsEncountered = new AtomicLong(0);
    
    /**
     * Consumes alarm events from Kafka.
     *
     * @param alarmEvent The alarm event payload
     * @param partition The Kafka partition
     * @param offset The Kafka offset
     * @param timestamp The Kafka timestamp
     */
    @KafkaListener(
            topics = "${spring.kafka.topics.alarm-events}",
            groupId = "${spring.kafka.consumer.alarm-group-id}",
            containerFactory = "alarmEventKafkaListenerContainerFactory"
    )
    public void consumeAlarmEvent(
            @Payload AlarmEvent alarmEvent,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        try {
            log.info("Received alarm event: deviceId={}, alarmType={}, severity={}, partition={}, offset={}",
                    alarmEvent.getDeviceId(), alarmEvent.getAlarmType(), alarmEvent.getSeverity(), 
                    partition, offset);
            
            // Process the alarm event
            processAlarmEvent(alarmEvent);
            
            // Update statistics
            totalProcessed.incrementAndGet();
            
            // Update severity counters
            if ("HIGH".equalsIgnoreCase(alarmEvent.getSeverity())) {
                highSeverityAlarms.incrementAndGet();
            } else if ("MEDIUM".equalsIgnoreCase(alarmEvent.getSeverity())) {
                mediumSeverityAlarms.incrementAndGet();
            } else if ("LOW".equalsIgnoreCase(alarmEvent.getSeverity())) {
                lowSeverityAlarms.incrementAndGet();
            }
            
        } catch (Exception e) {
            errorsEncountered.incrementAndGet();
            log.error("Error processing alarm event: deviceId={}, alarmType={}, severity={}",
                    alarmEvent.getDeviceId(), alarmEvent.getAlarmType(), alarmEvent.getSeverity(), e);
        }
    }
    
    /**
     * Processes an alarm event.
     * This includes storing the alarm, activating fire suppression for high severity alarms,
     * and notifying relevant systems.
     *
     * @param alarmEvent The alarm event to process
     */
    private void processAlarmEvent(AlarmEvent alarmEvent) {
        // Store the alarm in memory
        activeAlarms.add(alarmEvent);
        
        // Store by device ID
        activeAlarmsByDevice.computeIfAbsent(alarmEvent.getDeviceId(), 
                k -> new CopyOnWriteArrayList<>()).add(alarmEvent);
        
        // For high severity fire alarms, activate fire suppression
        if ("HIGH".equalsIgnoreCase(alarmEvent.getSeverity()) && 
                "FIRE".equalsIgnoreCase(alarmEvent.getAlarmType())) {
            
            try {
                // Determine the suppression type based on the zone or room
                String suppressionType = determineSuppression(alarmEvent);
                
                // Activate fire suppression
                boolean activated = fireSuppressionService.activateSuppression(
                        alarmEvent.getDeviceId(), 
                        alarmEvent.getZoneId(), 
                        suppressionType, 
                        100); // Use maximum intensity for fire alarms
                
                if (activated) {
                    suppressionActivations.incrementAndGet();
                    log.info("Activated {} fire suppression for device: {}", 
                            suppressionType, alarmEvent.getDeviceId());
                } else {
                    log.warn("Failed to activate fire suppression for device: {}", 
                            alarmEvent.getDeviceId());
                }
            } catch (Exception e) {
                log.error("Error activating fire suppression for device: {}", 
                        alarmEvent.getDeviceId(), e);
            }
        }
        
        // Distribute the alarm to all notification channels
        try {
            // Convert dataprocessing.model.AlarmEvent to alarmsystem.model.AlarmEvent
            com.firesentinel.alarmsystem.model.AlarmEvent alarmEventForDistribution = 
                    convertToAlarmSystemEvent(alarmEvent);
            
            // Distribute the alarm
            alarmDistributionService.distributeAlarm(alarmEventForDistribution);
            log.info("Distributed alarm notification for device: {}", alarmEvent.getDeviceId());
        } catch (Exception e) {
            log.error("Error distributing alarm notification for device: {}", 
                    alarmEvent.getDeviceId(), e);
        }
        
        // In a real system, we would also:
        // 1. Store the alarm in a database
        // 2. Send notifications to relevant personnel
        // 3. Update dashboards and monitoring systems
        // 4. Trigger other automated responses
    }
    
    /**
     * Converts a dataprocessing AlarmEvent to an alarmsystem AlarmEvent.
     * This is needed because the two systems use different model classes.
     *
     * @param source The source alarm event from the dataprocessing module
     * @return The converted alarm event for the alarmsystem module
     */
    private com.firesentinel.alarmsystem.model.AlarmEvent convertToAlarmSystemEvent(AlarmEvent source) {
        com.firesentinel.alarmsystem.model.AlarmEvent target = 
                new com.firesentinel.alarmsystem.model.AlarmEvent();
        
        // Set basic properties
        target.setId(source.getId().toString());
        target.setDeviceId(source.getDeviceId());
        target.setType(source.getAlarmType());
        
        // Convert severity string to enum
        String severityStr = source.getSeverity().toUpperCase();
        AlarmSeverity severity;
        try {
            severity = AlarmSeverity.valueOf(severityStr);
        } catch (IllegalArgumentException e) {
            // Default to HIGH if the severity doesn't match
            log.warn("Unknown severity: {}, defaulting to HIGH", severityStr);
            severity = AlarmSeverity.HIGH;
        }
        target.setSeverity(severity);
        
        target.setTimestamp(source.getTimestamp());
        
        // Set value information
        target.setValue(source.getValue());
        target.setUnit(source.getUnit());
        
        // Set location information
        target.setLocationX(source.getLocationX());
        target.setLocationY(source.getLocationY());
        target.setLocationZ(source.getLocationZ());
        target.setBuildingId(source.getBuildingId());
        target.setFloorId(source.getFloorId());
        target.setRoomId(source.getRoomId());
        target.setZoneId(source.getZoneId());
        
        // Set additional information
        target.setMessage("Alarm: " + source.getAlarmType() + " - " + source.getSeverity());
        target.setNotes(source.getNotes());
        target.setMetadata(source.getMetadata());
        
        // Set status information
        target.setAcknowledged(source.getAcknowledged() != null ? source.getAcknowledged() : false);
        target.setAcknowledgedAt(source.getAcknowledgedAt());
        target.setAcknowledgedBy(source.getAcknowledgedBy());
        target.setResolved(source.getResolved() != null ? source.getResolved() : false);
        target.setResolvedAt(source.getResolvedAt());
        target.setResolvedBy(source.getResolvedBy());
        
        return target;
    }
    
    /**
     * Determines the appropriate suppression type based on the alarm event.
     * This is a simplified implementation. In a real system, this would be more complex
     * and would consider factors like the type of building, room, and fire.
     *
     * @param alarmEvent The alarm event
     * @return The suppression type (water, foam, gas)
     */
    private String determineSuppression(AlarmEvent alarmEvent) {
        // Default to water
        String suppressionType = "water";
        
        // Check if this is a server room or data center
        if (alarmEvent.getRoomId() != null && 
                (alarmEvent.getRoomId().toLowerCase().contains("server") || 
                 alarmEvent.getRoomId().toLowerCase().contains("data"))) {
            // Use gas for server rooms and data centers
            suppressionType = "gas";
        }
        // Check if this is a kitchen
        else if (alarmEvent.getRoomId() != null && 
                alarmEvent.getRoomId().toLowerCase().contains("kitchen")) {
            // Use foam for kitchens
            suppressionType = "foam";
        }
        // Check if this is a laboratory
        else if (alarmEvent.getRoomId() != null && 
                alarmEvent.getRoomId().toLowerCase().contains("lab")) {
            // Use foam for laboratories
            suppressionType = "foam";
        }
        
        return suppressionType;
    }
    
    /**
     * Gets active alarms for a device.
     *
     * @param deviceId The device ID
     * @return A list of active alarms for the device
     */
    public List<AlarmEvent> getActiveAlarmsForDevice(String deviceId) {
        return activeAlarmsByDevice.getOrDefault(deviceId, new CopyOnWriteArrayList<>());
    }
    
    /**
     * Gets all active alarms.
     *
     * @return A list of all active alarms
     */
    public List<AlarmEvent> getAllActiveAlarms() {
        return activeAlarms;
    }
    
    /**
     * Acknowledges an alarm.
     *
     * @param alarmId The alarm ID
     * @param acknowledgedBy Who acknowledged the alarm
     * @return True if the alarm was acknowledged, false otherwise
     */
    public boolean acknowledgeAlarm(Long alarmId, String acknowledgedBy) {
        for (AlarmEvent alarm : activeAlarms) {
            if (alarm.getId().equals(alarmId)) {
                alarm.setAcknowledged(true);
                alarm.setAcknowledgedAt(java.time.Instant.now());
                alarm.setAcknowledgedBy(acknowledgedBy);
                return true;
            }
        }
        return false;
    }
    
    /**
     * Resolves an alarm.
     *
     * @param alarmId The alarm ID
     * @param resolvedBy Who resolved the alarm
     * @return True if the alarm was resolved, false otherwise
     */
    public boolean resolveAlarm(Long alarmId, String resolvedBy) {
        for (AlarmEvent alarm : activeAlarms) {
            if (alarm.getId().equals(alarmId)) {
                alarm.setResolved(true);
                alarm.setResolvedAt(java.time.Instant.now());
                alarm.setResolvedBy(resolvedBy);
                
                // Remove from active alarms
                activeAlarms.remove(alarm);
                
                // Remove from device alarms
                List<AlarmEvent> deviceAlarms = activeAlarmsByDevice.get(alarm.getDeviceId());
                if (deviceAlarms != null) {
                    deviceAlarms.remove(alarm);
                }
                
                return true;
            }
        }
        return false;
    }
    
    /**
     * Gets statistics about the consumer.
     *
     * @return A string with statistics
     */
    public String getStatistics() {
        return String.format(
                "Total processed: %d, High severity: %d, Medium severity: %d, Low severity: %d, " +
                "Suppression activations: %d, Errors: %d, Active alarms: %d",
                totalProcessed.get(),
                highSeverityAlarms.get(),
                mediumSeverityAlarms.get(),
                lowSeverityAlarms.get(),
                suppressionActivations.get(),
                errorsEncountered.get(),
                activeAlarms.size()
        );
    }
} 