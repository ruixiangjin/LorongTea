local rate = tonumber(ARGV[1])
local capacity = tonumber(ARGV[2]) * 1000
local ttl = math.ceil(capacity / rate)

local clock = redis.call('TIME')
local now = tonumber(clock[1]) * 1000 + math.floor(tonumber(clock[2]) / 1000)
local state = redis.call('HMGET', KEYS[1], 'credits', 'updated')
local credits = tonumber(state[1]) or capacity
local updated = tonumber(state[2]) or now
local elapsed = math.max(0, math.min(now - updated, ttl))
credits = math.min(capacity, credits + elapsed * rate)

local wait = 0
if credits >= 1000 then
    credits = credits - 1000
else
    wait = math.ceil((1000 - credits) / rate)
end

redis.call('HSET', KEYS[1], 'credits', credits, 'updated', math.max(now, updated))
redis.call('PEXPIRE', KEYS[1], ttl)
return wait
