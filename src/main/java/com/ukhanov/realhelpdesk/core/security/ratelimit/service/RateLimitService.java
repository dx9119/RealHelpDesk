package com.ukhanov.realhelpdesk.core.security.ratelimit.service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Service;

@Service
public class RateLimitService {

    private static final int SWEEP_THRESHOLD = 1000;

    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    private record Window(long expiresAt, AtomicInteger count) {
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    public Decision check(String key, int maxRequests, Duration window) {
        long now = System.currentTimeMillis();
        long windowMillis = window.toMillis();
        sweepExpired(now);

        Window current = windows.compute(key,
                (k, existing) -> existing == null || existing.expiresAt() <= now
                        ? new Window(now + windowMillis, new AtomicInteger(0))
                        : existing);

        int count = current.count().incrementAndGet();
        if (count <= maxRequests) {
            return new Decision(true, 0);
        }

        long retryAfterMillis = Math.max(0, current.expiresAt() - now);
        return new Decision(false, Math.max(1, (retryAfterMillis + 999) / 1000));
    }

    public void reset() {
        windows.clear();
    }

    private void sweepExpired(long now) {
        if (windows.size() <= SWEEP_THRESHOLD) {
            return;
        }
        windows.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    }
}
