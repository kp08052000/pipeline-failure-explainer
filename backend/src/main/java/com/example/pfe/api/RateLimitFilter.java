package com.example.pfe.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter limiter;

    public RateLimitFilter(@Value("${rate-limit.per-client-per-minute}") int perClientPerMinute,
                           @Value("${rate-limit.per-day}") int perDay) {
        this.limiter = new RateLimiter(perClientPerMinute, perDay, Clock.systemUTC());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !("POST".equals(request.getMethod()) && "/api/diagnose".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        // getRemoteAddr honours X-Forwarded-For only from trusted proxies (server.forward-headers-strategy=native).
        RateLimiter.Decision decision = limiter.tryAcquire(request.getRemoteAddr());
        if (decision == RateLimiter.Decision.ALLOWED) {
            chain.doFilter(request, response);
            return;
        }
        String message = decision == RateLimiter.Decision.DAILY_LIMIT
                ? "Daily analysis limit reached for this demo. Please try again tomorrow."
                : "Too many requests. Please wait a minute and try again.";
        response.setStatus(429);
        response.setHeader("Retry-After", decision == RateLimiter.Decision.DAILY_LIMIT ? "3600" : "60");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
