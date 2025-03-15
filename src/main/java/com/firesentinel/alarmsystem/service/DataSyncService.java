package com.firesentinel.alarmsystem.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.firesentinel.alarmsystem.model.AlarmEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for synchronizing data between the server and clients.
 * Implements a hybrid push/pull model for efficient real-time updates and historical data retrieval.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataSyncService {

    private final SimpMessagingTemplate webSocketTemplate;
    private final AlarmHistoryService alarmHistoryService;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${websocket.topic.prefix:/topic/alarm/}")
    private String webSocketTopicPrefix;
    
    @Value("${data-sync.snapshot-interval-seconds:300}")
    private int snapshotIntervalSeconds;
    
    @Value("${data-sync.max-events-per-snapshot:1000}")
    private int maxEventsPerSnapshot;
    
    // Cache for tracking the last snapshot timestamp for each client
    private final Map<String, Instant> clientLastSnapshotTimestamps = new ConcurrentHashMap<>();
    
    // Statistics counters
    private final AtomicLong pushUpdatesCount = new AtomicLong(0);
    private final AtomicLong pullUpdatesCount = new AtomicLong(0);
    private final AtomicLong snapshotsGeneratedCount = new AtomicLong(0);
    
    // Redis key prefixes
    private static final String SNAPSHOT_KEY_PREFIX = "data-sync:snapshot:";
    private static final String LAST_UPDATE_KEY = "data-sync:last-update";
    
    /**
     * Sends a real-time update for an alarm event.
     * This method pushes the event to all connected clients via WebSocket.
     *
     * @param alarmEvent The alarm event to push
     */
    public void pushAlarmUpdate(AlarmEvent alarmEvent) {
        try {
            // Send to the general topic
            webSocketTemplate.convertAndSend(webSocketTopicPrefix + "all", alarmEvent);
            
            // Send to the severity-specific topic
            String severityTopic = webSocketTopicPrefix + alarmEvent.getSeverity().toString().toLowerCase();
            webSocketTemplate.convertAndSend(severityTopic, alarmEvent);
            
            // Update the last update timestamp in Redis
            redisTemplate.opsForValue().set(LAST_UPDATE_KEY, Instant.now().toString());
            
            // Increment the push updates counter
            pushUpdatesCount.incrementAndGet();
            
            log.debug("Pushed alarm update: {}", alarmEvent.getId());
            
        } catch (Exception e) {
            log.error("Failed to push alarm update: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Gets a snapshot of alarm events for a client.
     * This method is used for the pull part of the hybrid model, allowing clients
     * to retrieve historical data in batches.
     *
     * @param clientId The client ID
     * @param lastSyncTimestamp The timestamp of the last synchronization
     * @return A map containing the snapshot data
     */
    public Map<String, Object> getAlarmSnapshot(String clientId, Instant lastSyncTimestamp) {
        Map<String, Object> snapshot = new HashMap<>();
        
        try {
            // Get the current timestamp
            Instant now = Instant.now();
            
            // If no last sync timestamp is provided, use a default (e.g., 1 hour ago)
            if (lastSyncTimestamp == null) {
                lastSyncTimestamp = now.minus(1, ChronoUnit.HOURS);
            }
            
            // Get alarms since the last sync
            List<AlarmEvent> alarms = alarmHistoryService.getAlarmsInTimeWindow(lastSyncTimestamp, now);
            
            // Limit the number of events to avoid overwhelming the client
            if (alarms.size() > maxEventsPerSnapshot) {
                alarms = alarms.subList(0, maxEventsPerSnapshot);
            }
            
            // Add the alarms to the snapshot
            snapshot.put("alarms", alarms);
            snapshot.put("timestamp", now);
            snapshot.put("count", alarms.size());
            
            // Update the client's last snapshot timestamp
            clientLastSnapshotTimestamps.put(clientId, now);
            
            // Increment the pull updates counter
            pullUpdatesCount.incrementAndGet();
            
            // Cache the snapshot in Redis for future use
            cacheSnapshot(clientId, snapshot);
            
            log.debug("Generated alarm snapshot for client {}: {} events", clientId, alarms.size());
            
        } catch (Exception e) {
            log.error("Failed to generate alarm snapshot: {}", e.getMessage(), e);
            
            // Add error information to the snapshot
            snapshot.put("error", e.getMessage());
            snapshot.put("timestamp", Instant.now());
        }
        
        return snapshot;
    }
    
    /**
     * Gets a delta update for a client.
     * This method is used for efficient incremental updates, sending only the
     * events that have occurred since the last snapshot.
     *
     * @param clientId The client ID
     * @return A map containing the delta update data
     */
    public Map<String, Object> getAlarmDeltaUpdate(String clientId) {
        Map<String, Object> deltaUpdate = new HashMap<>();
        
        try {
            // Get the client's last snapshot timestamp
            Instant lastSnapshotTimestamp = clientLastSnapshotTimestamps.getOrDefault(
                    clientId, Instant.now().minus(1, ChronoUnit.HOURS));
            
            // Get the current timestamp
            Instant now = Instant.now();
            
            // Get alarms since the last snapshot
            List<AlarmEvent> alarms = alarmHistoryService.getAlarmsInTimeWindow(lastSnapshotTimestamp, now);
            
            // Add the alarms to the delta update
            deltaUpdate.put("alarms", alarms);
            deltaUpdate.put("timestamp", now);
            deltaUpdate.put("count", alarms.size());
            deltaUpdate.put("lastSnapshotTimestamp", lastSnapshotTimestamp);
            
            log.debug("Generated alarm delta update for client {}: {} events", clientId, alarms.size());
            
        } catch (Exception e) {
            log.error("Failed to generate alarm delta update: {}", e.getMessage(), e);
            
            // Add error information to the delta update
            deltaUpdate.put("error", e.getMessage());
            deltaUpdate.put("timestamp", Instant.now());
        }
        
        return deltaUpdate;
    }
    
    /**
     * Caches a snapshot in Redis for future use.
     *
     * @param clientId The client ID
     * @param snapshot The snapshot to cache
     */
    private void cacheSnapshot(String clientId, Map<String, Object> snapshot) {
        try {
            // Convert the snapshot to JSON
            String snapshotJson = objectMapper.writeValueAsString(snapshot);
            
            // Cache the snapshot in Redis
            redisTemplate.opsForValue().set(
                    SNAPSHOT_KEY_PREFIX + clientId,
                    snapshotJson,
                    snapshotIntervalSeconds,
                    java.util.concurrent.TimeUnit.SECONDS);
            
            // Increment the snapshots generated counter
            snapshotsGeneratedCount.incrementAndGet();
            
        } catch (JsonProcessingException e) {
            log.error("Failed to cache snapshot: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Gets a cached snapshot for a client.
     *
     * @param clientId The client ID
     * @return The cached snapshot, or null if not found
     */
    public Map<String, Object> getCachedSnapshot(String clientId) {
        try {
            // Get the cached snapshot from Redis
            String snapshotJson = redisTemplate.opsForValue().get(SNAPSHOT_KEY_PREFIX + clientId);
            
            if (snapshotJson != null) {
                // Convert the JSON to a map
                @SuppressWarnings("unchecked")
                Map<String, Object> snapshot = objectMapper.readValue(snapshotJson, Map.class);
                return snapshot;
            }
            
        } catch (JsonProcessingException e) {
            log.error("Failed to get cached snapshot: {}", e.getMessage(), e);
        }
        
        return null;
    }
    
    /**
     * Generates and broadcasts a snapshot to all clients.
     * This method is scheduled to run periodically to ensure all clients
     * have up-to-date data.
     */
    @Scheduled(fixedRateString = "${data-sync.snapshot-broadcast-interval-seconds:3600000}")
    public void broadcastSnapshot() {
        try {
            // Get the current timestamp
            Instant now = Instant.now();
            
            // Get recent alarms (last hour)
            Instant oneHourAgo = now.minus(1, ChronoUnit.HOURS);
            List<AlarmEvent> alarms = alarmHistoryService.getAlarmsInTimeWindow(oneHourAgo, now);
            
            // Limit the number of events to avoid overwhelming the clients
            if (alarms.size() > maxEventsPerSnapshot) {
                alarms = alarms.subList(0, maxEventsPerSnapshot);
            }
            
            // Create the snapshot
            Map<String, Object> snapshot = new HashMap<>();
            snapshot.put("alarms", alarms);
            snapshot.put("timestamp", now);
            snapshot.put("count", alarms.size());
            snapshot.put("type", "periodic-snapshot");
            
            // Broadcast the snapshot to all clients
            webSocketTemplate.convertAndSend(webSocketTopicPrefix + "snapshot", snapshot);
            
            log.info("Broadcasted alarm snapshot: {} events", alarms.size());
            
        } catch (Exception e) {
            log.error("Failed to broadcast alarm snapshot: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Gets statistics about the data synchronization.
     *
     * @return A map of statistics
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("pushUpdatesCount", pushUpdatesCount.get());
        stats.put("pullUpdatesCount", pullUpdatesCount.get());
        stats.put("snapshotsGeneratedCount", snapshotsGeneratedCount.get());
        stats.put("activeClients", clientLastSnapshotTimestamps.size());
        
        // Get the last update timestamp
        String lastUpdateStr = redisTemplate.opsForValue().get(LAST_UPDATE_KEY);
        if (lastUpdateStr != null) {
            stats.put("lastUpdateTimestamp", lastUpdateStr);
        }
        
        return stats;
    }
} 