package com.firesentinel.alarmsystem.controller;

import com.firesentinel.alarmsystem.service.DataSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.Map;

/**
 * REST controller for data synchronization operations.
 * Provides endpoints for retrieving alarm snapshots and delta updates.
 */
@RestController
@RequestMapping("/api/data-sync")
@RequiredArgsConstructor
@Slf4j
public class DataSyncController {

    private final DataSyncService dataSyncService;

    /**
     * Gets a snapshot of alarm events for a client.
     *
     * @param clientId The client ID
     * @param lastSyncTimestamp The timestamp of the last synchronization (optional)
     * @return A response containing the snapshot data
     */
    @GetMapping("/snapshot")
    public ResponseEntity<Map<String, Object>> getAlarmSnapshot(
            @RequestParam String clientId,
            @RequestParam(required = false) Instant lastSyncTimestamp) {
        
        log.debug("Received request for alarm snapshot from client: {}", clientId);
        
        // Check if there's a cached snapshot
        Map<String, Object> cachedSnapshot = dataSyncService.getCachedSnapshot(clientId);
        
        if (cachedSnapshot != null) {
            log.debug("Returning cached snapshot for client: {}", clientId);
            return ResponseEntity.ok(cachedSnapshot);
        }
        
        // Generate a new snapshot
        Map<String, Object> snapshot = dataSyncService.getAlarmSnapshot(clientId, lastSyncTimestamp);
        return ResponseEntity.ok(snapshot);
    }

    /**
     * Gets a delta update for a client.
     *
     * @param clientId The client ID
     * @return A response containing the delta update data
     */
    @GetMapping("/delta")
    public ResponseEntity<Map<String, Object>> getAlarmDeltaUpdate(
            @RequestParam String clientId) {
        
        log.debug("Received request for alarm delta update from client: {}", clientId);
        
        Map<String, Object> deltaUpdate = dataSyncService.getAlarmDeltaUpdate(clientId);
        return ResponseEntity.ok(deltaUpdate);
    }

    /**
     * Gets statistics about the data synchronization.
     *
     * @return A response containing the statistics
     */
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStatistics() {
        log.debug("Received request for data sync statistics");
        
        Map<String, Object> stats = dataSyncService.getStatistics();
        return ResponseEntity.ok(stats);
    }
} 