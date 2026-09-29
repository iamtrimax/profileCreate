package com.trimax.linkhub.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import java.util.List;

@Service @RequiredArgsConstructor
public class RateLimiter {
    private final StringRedisTemplate redis;
    private static final DefaultRedisScript<Long> SCRIPT = new DefaultRedisScript<>(
            "local n = redis.call('INCR', KEYS[1]); if n == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end; return n", Long.class);
    public boolean allow(String key, int limit, int seconds) {
        Long count = redis.execute(SCRIPT, List.of("linkhub:rate:" + key), String.valueOf(seconds));
        return count != null && count <= limit;
    }
}
