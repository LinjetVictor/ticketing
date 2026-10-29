package com.example.ticketing.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.*;

/**
 * Redis-backed seat holds. One key per held seat: {@code ticketing:hold:{eventId}:seat:<seatId> = holdId}
 * with a TTL. Expiry is therefore enforced by Redis itself - no cleanup job is needed for correctness.
 *
 * All multi-seat operations are Lua scripts, so they are atomic (all seats or none) and cannot interleave
 * with other clients. The {eventId} hash tag keeps every seat of one event in the same Redis Cluster slot,
 * which is required for multi-key scripts there.
 */
@Component
public class SeatHoldStore {

    private static final String PREFIX = "ticketing:hold:";

    /** KEYS = seat keys, ARGV[1] = holdId, ARGV[2] = ttl millis. Returns 1 if all acquired, else 0 (nothing written). */
    private static final RedisScript<Long> ACQUIRE = RedisScript.of("""
            for _, key in ipairs(KEYS) do
              if redis.call('EXISTS', key) == 1 then return 0 end
            end
            for _, key in ipairs(KEYS) do
              redis.call('SET', key, ARGV[1], 'PX', ARGV[2])
            end
            return 1
            """, Long.class);

    /** Returns 1 only if every key is still owned by ARGV[1]. */
    private static final RedisScript<Long> OWNS_ALL = RedisScript.of("""
            for _, key in ipairs(KEYS) do
              if redis.call('GET', key) ~= ARGV[1] then return 0 end
            end
            return 1
            """, Long.class);

    /** Deletes only keys still owned by ARGV[1]; never removes somebody else's hold. */
    private static final RedisScript<Long> RELEASE = RedisScript.of("""
            local n = 0
            for _, key in ipairs(KEYS) do
              if redis.call('GET', key) == ARGV[1] then n = n + redis.call('DEL', key) end
            end
            return n
            """, Long.class);

    private final StringRedisTemplate redis;

    public SeatHoldStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean tryAcquire(long eventId, Collection<Long> seatIds, String holdId, Duration ttl) {
        Long result = redis.execute(ACQUIRE, keys(eventId, seatIds), holdId, String.valueOf(ttl.toMillis()));
        return Long.valueOf(1L).equals(result);
    }

    public boolean ownsAll(long eventId, Collection<Long> seatIds, String holdId) {
        Long result = redis.execute(OWNS_ALL, keys(eventId, seatIds), holdId);
        return Long.valueOf(1L).equals(result);
    }

    public void release(long eventId, Collection<Long> seatIds, String holdId) {
        redis.execute(RELEASE, keys(eventId, seatIds), holdId);
    }

    /** Which of the given seats currently have a live hold (any holder). One round trip. */
    public Set<Long> heldSeatIds(long eventId, List<Long> seatIds) {
        if (seatIds.isEmpty()) {
            return Set.of();
        }
        List<String> values = redis.opsForValue().multiGet(keys(eventId, seatIds));
        Set<Long> held = new HashSet<>();
        if (values != null) {
            for (int i = 0; i < values.size(); i++) {
                if (values.get(i) != null) {
                    held.add(seatIds.get(i));
                }
            }
        }
        return held;
    }

    private static List<String> keys(long eventId, Collection<Long> seatIds) {
        List<String> keys = new ArrayList<>(seatIds.size());
        for (Long seatId : seatIds) {
            keys.add(PREFIX + "{" + eventId + "}:seat:" + seatId);
        }
        return keys;
    }
}
