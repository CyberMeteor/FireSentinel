package com.firesentinel.alarmsystem.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.firesentinel.alarmsystem.model.AlarmEvent;
import com.firesentinel.alarmsystem.model.AlarmSeverity;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ResilientAlarmHistoryServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;
    
    @Mock
    private ZSetOperations<String, String> zSetOperations;
    
    private ObjectMapper objectMapper;
    private MeterRegistry meterRegistry;
    private ResilientAlarmHistoryService alarmHistoryService;
    
    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        meterRegistry = new SimpleMeterRegistry();
        
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        
        alarmHistoryService = new ResilientAlarmHistoryService(redisTemplate, objectMapper, meterRegistry);
    }
    
    @Test
    void storeAlarmEvent_Success() throws Exception {
        // Arrange
        AlarmEvent alarmEvent = createSampleAlarmEvent();
        
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        when(redisTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);
        
        // Act
        boolean result = alarmHistoryService.storeAlarmEvent(alarmEvent);
        
        // Assert
        assertTrue(result);
        verify(zSetOperations, times(4)).add(anyString(), anyString(), anyDouble());
        verify(redisTemplate, times(4)).expire(anyString(), anyLong(), any());
    }
    
    @Test
    void storeAlarmEvent_Fallback_WhenRedisUnavailable() {
        // Arrange
        AlarmEvent alarmEvent = createSampleAlarmEvent();
        
        when(zSetOperations.add(anyString(), anyString(), anyDouble()))
                .thenThrow(new RedisConnectionFailureException("Connection refused"));
        
        // Act
        boolean result = alarmHistoryService.storeAlarmEvent(alarmEvent);
        
        // Assert
        assertTrue(result);
        assertEquals(1, meterRegistry.counter("alarm.history.store.fallback").count());
    }
    
    @Test
    void getRecentAlarms_Success() throws Exception {
        // Arrange
        AlarmEvent alarmEvent = createSampleAlarmEvent();
        String alarmJson = objectMapper.writeValueAsString(alarmEvent);
        
        when(zSetOperations.reverseRange(anyString(), anyLong(), anyLong()))
                .thenReturn(Set.of(alarmJson));
        
        // Act
        CompletableFuture<List<AlarmEvent>> future = alarmHistoryService.getRecentAlarms(10);
        List<AlarmEvent> result = future.get();
        
        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(alarmEvent.getId(), result.get(0).getId());
        assertEquals(1, meterRegistry.counter("alarm.history.get.success").count());
    }
    
    @Test
    void getRecentAlarms_Fallback_WhenRedisUnavailable() throws ExecutionException, InterruptedException {
        // Arrange
        AlarmEvent alarmEvent = createSampleAlarmEvent();
        
        // Add to in-memory cache
        alarmHistoryService.storeAlarmEventFallback(alarmEvent, new RedisConnectionFailureException("Connection refused"));
        
        when(zSetOperations.reverseRange(anyString(), anyLong(), anyLong()))
                .thenThrow(new RedisConnectionFailureException("Connection refused"));
        
        // Act
        CompletableFuture<List<AlarmEvent>> future = alarmHistoryService.getRecentAlarms(10);
        List<AlarmEvent> result = future.get();
        
        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(alarmEvent.getId(), result.get(0).getId());
        assertEquals(1, meterRegistry.counter("alarm.history.get.fallback").count());
    }
    
    @Test
    void getAlarmsInTimeWindow_Success() throws Exception {
        // Arrange
        AlarmEvent alarmEvent = createSampleAlarmEvent();
        String alarmJson = objectMapper.writeValueAsString(alarmEvent);
        
        Instant startTime = Instant.now().minusSeconds(3600);
        Instant endTime = Instant.now();
        
        when(zSetOperations.rangeByScore(anyString(), anyDouble(), anyDouble()))
                .thenReturn(Set.of(alarmJson));
        
        // Act
        CompletableFuture<List<AlarmEvent>> future = alarmHistoryService.getAlarmsInTimeWindow(startTime, endTime);
        List<AlarmEvent> result = future.get();
        
        // Assert
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(alarmEvent.getId(), result.get(0).getId());
    }
    
    @Test
    void cleanupOldAlarms_Success() {
        // Arrange
        when(zSetOperations.removeRangeByScore(anyString(), anyDouble(), anyDouble()))
                .thenReturn(5L);
        
        // Act
        long result = alarmHistoryService.cleanupOldAlarms();
        
        // Assert
        assertEquals(5L, result);
        verify(zSetOperations).removeRangeByScore(anyString(), anyDouble(), anyDouble());
    }
    
    @Test
    void isRedisAvailable_ReturnsFalse_WhenRedisUnavailable() {
        // Arrange
        when(redisTemplate.hasKey(anyString()))
                .thenThrow(new RedisConnectionFailureException("Connection refused"));
        
        // Act
        boolean result = alarmHistoryService.isRedisAvailable();
        
        // Assert
        assertFalse(result);
    }
    
    private AlarmEvent createSampleAlarmEvent() {
        AlarmEvent event = new AlarmEvent();
        event.setId(UUID.randomUUID().toString());
        event.setDeviceId("device-123");
        event.setType("FIRE");
        event.setSeverity(AlarmSeverity.HIGH);
        event.setMessage("Fire detected in zone 1");
        event.setTimestamp(Instant.now());
        return event;
    }
} 