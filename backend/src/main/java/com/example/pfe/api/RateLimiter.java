package com.example.pfe.api;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/** Fixed-window limiter: N requests per client per minute plus a global daily cap. In-memory, single instance. */
public class RateLimiter {

    public enum Decision { ALLOWED, CLIENT_LIMIT, DAILY_LIMIT }

    private final int perClientPerMinute;
    private final int perDay;
    private final Clock clock;

    private final Map<String, Integer> clientCounts = new HashMap<>();
    private long currentMinute = -1;
    private LocalDate currentDay;
    private int dayCount;

    public RateLimiter(int perClientPerMinute, int perDay, Clock clock) {
        this.perClientPerMinute = perClientPerMinute;
        this.perDay = perDay;
        this.clock = clock;
    }

    public synchronized Decision tryAcquire(String clientId) {
        long minute = clock.millis() / 60_000;
        if (minute != currentMinute) {
            currentMinute = minute;
            clientCounts.clear();
        }
        LocalDate today = LocalDate.now(clock.withZone(ZoneOffset.UTC));
        if (!today.equals(currentDay)) {
            currentDay = today;
            dayCount = 0;
        }

        if (dayCount >= perDay) {
            return Decision.DAILY_LIMIT;
        }
        int used = clientCounts.getOrDefault(clientId, 0);
        if (used >= perClientPerMinute) {
            return Decision.CLIENT_LIMIT;
        }
        clientCounts.put(clientId, used + 1);
        dayCount++;
        return Decision.ALLOWED;
    }
}
