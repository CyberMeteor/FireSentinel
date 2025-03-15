package com.firesentinel.cache.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Service that implements a multi-level cache with Caffeine and Redis.
 * Uses dynamic TTL to avoid cache avalanche.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MultiLevelCacheService {

    private final RedisTemplate<String, Object> redisTemplate;
    
    @Value("${cache.caffeine.max-size:500}")
    private int caffeineMaxSize;
    
    @Value("${cache.caffeine.expiration-seconds:300}")
    private int caffeineExpirationSeconds;
    
    @Value("${cache.redis.base-ttl-seconds:600}")
    private int redisBaseTtlSeconds;
    
    @Value("${cache.ttl.jitter-percent:15}")
    private int ttlJitterPercent;
    
    private Cache<String, Object> caffeineCache;
    
    @PostConstruct
    public void init() {
        caffeineCache = Caffeine.newBuilder()
                .maximumSize(caffeineMaxSize)
                .expireAfterWrite(caffeineExpirationSeconds, TimeUnit.SECONDS)
                .recordStats()
                .build();
    }
    
    /**
     * Gets a value from the cache, or loads it using the provided loader function.
     * First checks Caffeine, then Redis, then loads from the source.
     *
     * @param key The cache key
     * @param loader The function to load the value if not found in cache
     * @param <T> The type of the value
     * @return The cached or loaded value
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Function<String, T> loader) {
        // Try to get from Caffeine cache first
        Object cachedValue = caffeineCache.getIfPresent(key);
        if (cachedValue != null) {
            log.debug("Cache hit (Caffeine): {}", key);
            return (T) cachedValue;
        }
        
        // If not in Caffeine, try Redis
        cachedValue = redisTemplate.opsForValue().get(key);
        if (cachedValue != null) {
            log.debug("Cache hit (Redis): {}", key);
            // Store in Caffeine for future requests
            caffeineCache.put(key, cachedValue);
            return (T) cachedValue;
        }
        
        // If not in Redis, load from source
        log.debug("Cache miss: {}", key);
        T value = loader.apply(key);
        if (value != null) {
            // Store in both caches
            caffeineCache.put(key, value);
            
            // Calculate dynamic TTL with jitter to avoid cache avalanche
            int ttlWithJitter = calculateDynamicTtl(redisBaseTtlSeconds, ttlJitterPercent);
            redisTemplate.opsForValue().set(key, value, ttlWithJitter, TimeUnit.SECONDS);
            
            log.debug("Cached value with TTL of {} seconds: {}", ttlWithJitter, key);
        }
        
        return value;
    }
    
    /**
     * Puts a value in both Caffeine and Redis caches.
     *
     * @param key The cache key
     * @param value The value to cache
     * @param <T> The type of the value
     */
    public <T> void put(String key, T value) {
        caffeineCache.put(key, value);
        
        int ttlWithJitter = calculateDynamicTtl(redisBaseTtlSeconds, ttlJitterPercent);
        redisTemplate.opsForValue().set(key, value, ttlWithJitter, TimeUnit.SECONDS);
        
        log.debug("Manually cached value with TTL of {} seconds: {}", ttlWithJitter, key);
    }
    
    /**
     * Evicts a value from both Caffeine and Redis caches.
     *
     * @param key The cache key to evict
     */
    public void evict(String key) {
        caffeineCache.invalidate(key);
        redisTemplate.delete(key);
        log.debug("Evicted from cache: {}", key);
    }
    
    /**
     * Calculates a dynamic TTL with jitter to avoid cache avalanche.
     *
     * @param baseTtl The base TTL in seconds
     * @param jitterPercent The jitter percentage (±)
     * @return The TTL with jitter applied
     */
    private int calculateDynamicTtl(int baseTtl, int jitterPercent) {
        // Calculate the jitter range
        double jitterRange = baseTtl * (jitterPercent / 100.0);
        
        // Generate a random jitter value between -jitterRange and +jitterRange
        double jitter = ThreadLocalRandom.current().nextDouble(-jitterRange, jitterRange);
        
        // Apply the jitter to the base TTL
        int ttlWithJitter = (int) Math.max(1, baseTtl + jitter);
        
        return ttlWithJitter;
    }
    
    /**
     * Gets cache statistics.
     *
     * @return A string representation of the cache statistics
     */
    public String getCacheStats() {
        return caffeineCache.stats().toString();
    }
} 