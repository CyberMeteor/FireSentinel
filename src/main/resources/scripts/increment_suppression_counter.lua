-- Increment Suppression Counter Lua Script
-- This script atomically increments a suppression counter for a device
-- KEYS[1]: Counter key (device:{deviceId}:counters)
-- ARGV[1]: Suppression type (water, foam, gas)
-- ARGV[2]: Current timestamp (milliseconds)

-- Increment the specific counter
local newValue = redis.call('HINCRBY', KEYS[1], ARGV[1] .. '_activations', 1)

-- Update the last activation timestamp
redis.call('HSET', KEYS[1], 'last_activation', ARGV[2])

-- Increment the total activations counter
redis.call('HINCRBY', KEYS[1], 'total_activations', 1)

-- Return the new counter value
return newValue 