package com.example.ticketing.redis;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * Minimal Redis lock so a scheduled job runs on one app instance at a time (SET NX PX + token-checked unlock).
 * It is an efficiency measure only: the jobs are also safe to run concurrently (row locks + SKIP LOCKED).
 */
@Component
public class DistributedJobLock {

    private static final RedisScript<Long> UNLOCK = RedisScript.of("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;

    public DistributedJobLock(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /** @return true if this instance got the lock and ran the task */
    public boolean runExclusively(String name, Duration ttl, Runnable task) {
        String key = "ticketing:joblock:" + name;
        String token = UUID.randomUUID().toString();
        Boolean acquired = redis.opsForValue().setIfAbsent(key, token, ttl);
        if (!Boolean.TRUE.equals(acquired)) {
            return false;
        }
        try {
            task.run();
            return true;
        } finally {
            redis.execute(UNLOCK, List.of(key), token);
        }
    }
}
