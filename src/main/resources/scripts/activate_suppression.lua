-- Activate Fire Suppression Lua Script
-- This script atomically checks device status and activates suppression if conditions are met
-- KEYS[1]: Device key (device:{deviceId})
-- ARGV[1]: Zone ID
-- ARGV[2]: Suppression type (water, foam, gas)
-- ARGV[3]: Intensity (0-100)
-- ARGV[4]: Current timestamp (milliseconds)

-- Check if device exists and is enabled
local deviceInfo = redis.call('HGETALL', KEYS[1])
if #deviceInfo == 0 then
    return false -- Device not found
end

-- Convert the flat array to a hash table for easier access
local device = {}
for i = 1, #deviceInfo, 2 do
    device[deviceInfo[i]] = deviceInfo[i + 1]
end

-- Check if device is enabled and connected
if device.enabled ~= "true" or device.connected ~= "true" then
    return false -- Device is disabled or disconnected
end

-- Check if suppression is already active
local suppressionKey = KEYS[1] .. ":suppression"
local isActive = redis.call('EXISTS', suppressionKey)
if isActive == 1 then
    -- Check if it's the same type of suppression
    local currentType = redis.call('HGET', suppressionKey, 'type')
    if currentType == ARGV[2] then
        -- Update intensity if it's the same type
        redis.call('HSET', suppressionKey, 'intensity', ARGV[3], 'last_updated', ARGV[4])
        return true
    else
        -- Don't allow different suppression types to be active simultaneously
        return false
    end
end

-- Activate suppression
redis.call('HMSET', suppressionKey, 
    'type', ARGV[2],
    'zone_id', ARGV[1],
    'intensity', ARGV[3],
    'activated_at', ARGV[4],
    'last_updated', ARGV[4]
)

-- Set expiration (auto-deactivate after 30 minutes if not explicitly deactivated)
redis.call('EXPIRE', suppressionKey, 1800)

-- Increment counters
local counterKey = KEYS[1] .. ":counters"
redis.call('HINCRBY', counterKey, 'total_activations', 1)
redis.call('HINCRBY', counterKey, ARGV[2] .. '_activations', 1)
redis.call('HSET', counterKey, 'last_activation', ARGV[4])

-- Add to activation history
local historyKey = KEYS[1] .. ":history"
local historyEntry = string.format(
    '{"timestamp":%s,"type":"%s","zone":"%s","intensity":%s}',
    ARGV[4], ARGV[2], ARGV[1], ARGV[3]
)
redis.call('LPUSH', historyKey, historyEntry)
redis.call('LTRIM', historyKey, 0, 99) -- Keep only the last 100 entries

-- Publish event to Redis pub/sub
local eventMessage = string.format(
    '{"event":"suppression_activated","device_id":"%s","zone_id":"%s","type":"%s","intensity":%s,"timestamp":%s}',
    string.sub(KEYS[1], 8), -- Extract device ID from key
    ARGV[1],
    ARGV[2],
    ARGV[3],
    ARGV[4]
)
redis.call('PUBLISH', 'fire_events', eventMessage)

return true 