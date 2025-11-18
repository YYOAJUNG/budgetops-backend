package com.budgetops.backend.azure.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class AzureCostRateLimiter {

    private static final long MIN_INTERVAL_MILLIS = 5_000L;
    private static final long SLEEP_SLICE_MILLIS = 250L;

    private final Map<String, Long> lastCallMillis = new ConcurrentHashMap<>();

    public void awaitAllowance(String subscriptionId) {
        Objects.requireNonNull(subscriptionId, "subscriptionId must not be null");

        while (true) {
            long now = System.currentTimeMillis();
            Long previous = lastCallMillis.get(subscriptionId);

            if (previous == null || now - previous >= MIN_INTERVAL_MILLIS) {
                if (previous == null) {
                    if (lastCallMillis.putIfAbsent(subscriptionId, now) == null) {
                        return;
                    }
                } else if (lastCallMillis.replace(subscriptionId, previous, now)) {
                    return;
                }
                continue;
            }

            long waitMillis = Math.min(MIN_INTERVAL_MILLIS - (now - previous), SLEEP_SLICE_MILLIS);
            try {
                Thread.sleep(waitMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Azure Cost API rate limiter interrupted", e);
            }
        }
    }
}

