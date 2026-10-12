package com.nexusclient.backend;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RateLimiter – "at most N times per time window" for anything (e.g. sign-in attempts per internet
 * address), kept in memory. Each check is recorded; once the limit is reached, check() throws a
 * plain-English "429 Too many requests" until old attempts fall out of the window.
 */
@Component
public class RateLimiter {

    private final Map<String, Deque<Long>> attempts = new ConcurrentHashMap<>();

    /**
     * Records one attempt for this key and refuses it if there were already max in the window.
     *
     * @param key e.g. "signin-ip:1.2.3.4" – anything that names what's being limited
     */
    public void check(String key, int max, Duration window, String message) {
        long now = System.currentTimeMillis();
        long oldest = now - window.toMillis();
        Deque<Long> times = attempts.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() < oldest) {
                times.pollFirst();
            }
            if (times.size() >= max) {
                throw NexusException.tooManyRequests(message);
            }
            times.addLast(now);
        }
        if (attempts.size() > 50_000) {
            forgetOld(oldest); // don't let a flood of different keys fill the memory
        }
    }

    private void forgetOld(long oldest) {
        attempts.entrySet().removeIf(entry -> {
            synchronized (entry.getValue()) {
                return entry.getValue().isEmpty() || entry.getValue().peekLast() < oldest;
            }
        });
    }
}
