-- Get Device Status Lua Script
-- This script atomically gets the device status and related information
-- KEYS[1]: Device key (device:{deviceId})

-- Get device info
local deviceInfo = redis.call('HGETALL', KEYS[1])
if #deviceInfo == 0 then
    return {} -- Device not found
end

-- Convert the flat array to a hash table
local result = {}
for i = 1, #deviceInfo, 2 do
    result[deviceInfo[i]] = deviceInfo[i + 1]
end

-- Check if suppression is active
local suppressionKey = KEYS[1] .. ":suppression"
local isActive = redis.call('EXISTS', suppressionKey)
if isActive == 1 then
    -- Get suppression info
    local suppressionInfo = redis.call('HGETALL', suppressionKey)
    
    -- Add suppression info to result
    result['suppression_active'] = 'true'
    for i = 1, #suppressionInfo, 2 do
        result['suppression_' .. suppressionInfo[i]] = suppressionInfo[i + 1]
    end
else
    result['suppression_active'] = 'false'
end

-- Get counter info
local counterKey = KEYS[1] .. ":counters"
local counterInfo = redis.call('HGETALL', counterKey)
if #counterInfo > 0 then
    -- Add counter info to result
    for i = 1, #counterInfo, 2 do
        result['counter_' .. counterInfo[i]] = counterInfo[i + 1]
    end
end

-- Get last history entry
local historyKey = KEYS[1] .. ":history"
local lastHistory = redis.call('LINDEX', historyKey, 0)
if lastHistory then
    result['last_activation_details'] = lastHistory
end

return result 