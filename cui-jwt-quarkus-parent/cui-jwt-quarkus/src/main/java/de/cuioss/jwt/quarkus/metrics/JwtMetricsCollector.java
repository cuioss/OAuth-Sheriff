/*
 * Copyright © 2025 CUI-OpenSource-Software (info@cuioss.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cuioss.jwt.quarkus.metrics;

import de.cuioss.jwt.quarkus.config.JwtPropertyKeys;
import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.security.EventCategory;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.tools.logging.CuiLogger;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.quarkus.arc.Unremovable;
import io.quarkus.scheduler.Scheduled;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NonNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages.INFO;
import static de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages.WARN;

/**
 * Collects JWT validation metrics from {@link SecurityEventCounter} and exposes them as Micrometer metrics.
 * <p>
 * This collector registers metrics for security events:
 * <ul>
 *   <li>Security Event Counters - from {@link SecurityEventCounter}</li>
 * </ul>
 * <p>
 * All metrics follow Micrometer naming conventions and include appropriate tags
 * for filtering:
 * <ul>
 *   <li>cui.jwt.validation.errors - Counter for validation errors by type</li>
 *   <li>cui.jwt.validation.success - Counter for successful operations by type</li>
 * </ul>
 * <p>
 * Security event metrics include tags:
 * <ul>
 *   <li>event_type - The type of security event</li>
 *   <li>result - The validation result (success or failure)</li>
 *   <li>category - The category of event (structure, signature, semantic) - only for failures</li>
 * </ul>
 */
@ApplicationScoped
@Unremovable
public class JwtMetricsCollector {

    private static final CuiLogger LOGGER = new CuiLogger(JwtMetricsCollector.class);

    private static final String TAG_EVENT_TYPE = "event_type";
    private static final String TAG_RESULT = "result";
    private static final String TAG_CATEGORY = "category";

    private static final String RESULT_FAILURE = "failure";
    private static final String RESULT_SUCCESS = "success";

    private final MeterRegistry registry;
    private final TokenValidator tokenValidator;

    private SecurityEventCounter securityEventCounter;

    // Caching of counters to avoid lookups
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    // Track last known counts to calculate deltas
    private final Map<SecurityEventCounter.EventType, Long> lastKnownCounts = new ConcurrentHashMap<>();

    /**
     * Creates a new JwtMetricsCollector with the given MeterRegistry and TokenValidator.
     *
     * @param registry the Micrometer registry
     * @param tokenValidator the token validator containing the security event counter
     */
    @Inject
    public JwtMetricsCollector(@NonNull MeterRegistry registry,
            @NonNull TokenValidator tokenValidator) {
        this.registry = registry;
        this.tokenValidator = tokenValidator;
    }

    /**
     * Initializes all metrics after dependency injection is complete.
     * This method is guaranteed to run before any business method can be called.
     */
    @PostConstruct
    void initialize() {
        LOGGER.info(INFO.INITIALIZING_JWT_METRICS_COLLECTOR);
        securityEventCounter = tokenValidator.getSecurityEventCounter();

        // Register counters for all event types
        registerEventCounters();

        // Initialize the last known counts
        Map<SecurityEventCounter.EventType, Long> currentCounts = securityEventCounter.getCounters();
        lastKnownCounts.putAll(currentCounts);

        LOGGER.info(INFO.JWT_METRICS_COLLECTOR_INITIALIZED, counters.size());

        // Force initial update to ensure metrics are visible immediately
        updateCounters();
    }

    /**
     * Registers counters for all security event types.
     * Creates both success and failure counters as appropriate.
     */
    private void registerEventCounters() {
        // For each event type, create appropriate counters
        for (SecurityEventCounter.EventType eventType : SecurityEventCounter.EventType.values()) {
            EventCategory category = eventType.getCategory();

            if (category == null) {
                // Success events (no category) - register as success metrics
                Tags tags = Tags.of(
                        Tag.of(TAG_EVENT_TYPE, eventType.name()),
                        Tag.of(TAG_RESULT, RESULT_SUCCESS)
                );

                Counter counter = Counter.builder(MetricIdentifier.VALIDATION.SUCCESS)
                        .tags(tags)
                        .description("Number of successful JWT operations by type")
                        .baseUnit("operations")
                        .register(registry);

                counters.put(eventType.name(), counter);
                LOGGER.debug("Registered success counter for event type %s", eventType.name());
            } else {
                // Failure events (with category) - register as error metrics
                Tags tags = Tags.of(
                        Tag.of(TAG_EVENT_TYPE, eventType.name()),
                        Tag.of(TAG_RESULT, RESULT_FAILURE),
                        Tag.of(TAG_CATEGORY, category.name())
                );

                Counter counter = Counter.builder(MetricIdentifier.VALIDATION.ERRORS)
                        .tags(tags)
                        .description("Number of JWT validation errors by type")
                        .baseUnit("errors")
                        .register(registry);

                counters.put(eventType.name(), counter);
                LOGGER.debug("Registered error counter for event type %s", eventType.name());
            }
        }
    }


    /**
     * Updates all counters from the current state.
     * This method is called periodically to ensure metrics are up to date.
     * <p>
     * The interval can be configured via the property: cui.jwt.metrics.collection.interval
     * Default: 10s (production), can be set to 2s for faster integration testing.
     */
    @Scheduled(every = "${" + JwtPropertyKeys.METRICS.COLLECTION_INTERVAL + ":10s}")
    public void updateCounters() {
        updateSecurityEventCounters();
    }

    /**
     * Clears all metrics by resetting the underlying monitors and clearing tracking maps.
     * <p>
     * This method performs the following operations:
     * <ul>
     *   <li>Clears the SecurityEventCounter to reset all security event counts</li>
     *   <li>Resets internal tracking maps to ensure proper delta calculations after clearing</li>
     * </ul>
     * <p>
     * Note: This method only clears the underlying monitors. The Micrometer metrics
     * themselves will retain their values until the next scheduled update cycle.
     */
    public void clear() {
        LOGGER.info(INFO.CLEARING_JWT_METRICS);

        // Clear security event counter
        if (securityEventCounter != null) {
            securityEventCounter.reset();
        }

        // Clear tracking maps to reset delta calculations
        lastKnownCounts.clear();

        LOGGER.info(INFO.JWT_METRICS_CLEARED);
    }

    /**
     * Updates security event counters from the SecurityEventCounter state.
     */
    private void updateSecurityEventCounters() {
        // Get current counts for all event types
        Map<SecurityEventCounter.EventType, Long> currentCounts = securityEventCounter.getCounters();

        // Debug: Log current state for success events
        for (SecurityEventCounter.EventType eventType : SecurityEventCounter.EventType.values()) {
            if (eventType.getCategory() == null) {
                long count = securityEventCounter.getCount(eventType);
                if (count > 0) {
                    LOGGER.debug("Success event %s has count %s", eventType.name(), count);
                }
            }
        }

        // Update counters based on current counts
        for (Map.Entry<SecurityEventCounter.EventType, Long> entry : currentCounts.entrySet()) {
            SecurityEventCounter.EventType eventType = entry.getKey();
            Long currentCount = entry.getValue();

            // Get the last known count for this event type
            Long lastCount = lastKnownCounts.getOrDefault(eventType, 0L);

            // Calculate the delta
            long delta = currentCount - lastCount;

            // Only update if there's a change
            if (delta > 0) {
                // Get the counter for this event type
                Counter counter = counters.get(eventType.name());
                if (counter != null) {
                    // Increment the counter by the delta
                    counter.increment(delta);
                    LOGGER.debug("Updated counter for event type %s by %s (total: %s)", eventType.name(), delta, counter.count());
                } else {
                    LOGGER.warn(WARN.NO_MICROMETER_COUNTER_FOUND, eventType.name(), delta);
                }

                // Update the last known count
                lastKnownCounts.put(eventType, currentCount);
            }
        }
    }

}
