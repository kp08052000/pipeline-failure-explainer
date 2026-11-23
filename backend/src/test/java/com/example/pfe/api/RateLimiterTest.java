package com.example.pfe.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.pfe.api.RateLimiter.Decision;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class RateLimiterTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-23T10:00:05Z"));
    private final Clock clock = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    };

    @Test
    void limitsEachClientPerMinuteAndResetsNextMinute() {
        RateLimiter limiter = new RateLimiter(2, 100, clock);

        assertThat(limiter.tryAcquire("a")).isEqualTo(Decision.ALLOWED);
        assertThat(limiter.tryAcquire("a")).isEqualTo(Decision.ALLOWED);
        assertThat(limiter.tryAcquire("a")).isEqualTo(Decision.CLIENT_LIMIT);
        assertThat(limiter.tryAcquire("b")).isEqualTo(Decision.ALLOWED);

        now.set(now.get().plus(Duration.ofMinutes(1)));
        assertThat(limiter.tryAcquire("a")).isEqualTo(Decision.ALLOWED);
    }

    @Test
    void enforcesGlobalDailyCapAndResetsNextDay() {
        RateLimiter limiter = new RateLimiter(10, 2, clock);

        assertThat(limiter.tryAcquire("a")).isEqualTo(Decision.ALLOWED);
        assertThat(limiter.tryAcquire("b")).isEqualTo(Decision.ALLOWED);
        assertThat(limiter.tryAcquire("c")).isEqualTo(Decision.DAILY_LIMIT);

        now.set(now.get().plus(Duration.ofDays(1)));
        assertThat(limiter.tryAcquire("c")).isEqualTo(Decision.ALLOWED);
    }
}
