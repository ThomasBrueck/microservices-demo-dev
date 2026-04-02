package com.okteto.vote.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-process rate limiting to demonstrate the Rate Limiting cloud pattern.
 *
 * Defaults: 60 requests per minute per client IP (based on X-Forwarded-For or remote address).
 *
 * Note: For production, prefer enforcing limits at the edge (Ingress/API Gateway) and using a
 * distributed counter (e.g., Redis) if multiple replicas exist.
 */
@Component
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final String ENV_ENABLED = "RATE_LIMIT_ENABLED";
    private static final String ENV_WINDOW_MS = "RATE_LIMIT_WINDOW_MS";
    private static final String ENV_MAX = "RATE_LIMIT_MAX";

    private static final long DEFAULT_WINDOW_MS = 60_000;
    private static final int DEFAULT_MAX = 60;

    private static final class Window {
        private volatile long windowStartMs;
        private final AtomicInteger count = new AtomicInteger(0);

        private Window(long windowStartMs) {
            this.windowStartMs = windowStartMs;
        }
    }

    private final ConcurrentHashMap<String, Window> windowsByClient = new ConcurrentHashMap<>();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // Only limit vote submissions.
        return !("POST".equalsIgnoreCase(request.getMethod()) && "/".equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!isEnabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        long windowMs = getWindowMs();
        int max = getMax();
        if (max <= 0) {
            filterChain.doFilter(request, response);
            return;
        }

        String clientId = getClientId(request);
        long now = System.currentTimeMillis();

        Window window = windowsByClient.computeIfAbsent(clientId, _k -> new Window(now));
        int current;
        long retryAfterSeconds;
        synchronized (window) {
            if (now - window.windowStartMs >= windowMs) {
                window.windowStartMs = now;
                window.count.set(0);
            }
            current = window.count.incrementAndGet();
            retryAfterSeconds = Math.max(1, (windowMs - (now - window.windowStartMs) + 999) / 1000);
        }

        if (current > max) {
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            response.setContentType("text/plain;charset=UTF-8");
            response.getOutputStream().write("Too Many Requests".getBytes(StandardCharsets.UTF_8));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private static boolean isEnabled() {
        return !"false".equalsIgnoreCase(System.getenv(ENV_ENABLED));
    }

    private static long getWindowMs() {
        String raw = System.getenv(ENV_WINDOW_MS);
        if (isBlank(raw)) {
            return DEFAULT_WINDOW_MS;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return DEFAULT_WINDOW_MS;
        }
    }

    private static int getMax() {
        String raw = System.getenv(ENV_MAX);
        if (isBlank(raw)) {
            return DEFAULT_MAX;
        }
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return DEFAULT_MAX;
        }
    }

    private static String getClientId(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (!isBlank(xff)) {
            // First IP in the list is the original client.
            String[] parts = xff.split(",");
            if (parts.length > 0) {
                String first = parts[0].trim();
                if (!isBlank(first)) {
                    return first;
                }
            }
        }
        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
