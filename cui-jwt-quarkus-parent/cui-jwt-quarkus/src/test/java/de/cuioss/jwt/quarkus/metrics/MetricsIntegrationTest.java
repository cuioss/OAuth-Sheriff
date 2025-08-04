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
import de.cuioss.jwt.quarkus.config.JwtTestProfile;
import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.test.juli.junit5.EnableTestLogger;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for metrics collection during token validation.
 * This test verifies that metrics are properly collected when the TokenValidator
 * is used in a real application context.
 */
@QuarkusTest
@TestProfile(JwtTestProfile.class)
@EnableTestLogger
class MetricsIntegrationTest {

    @Inject
    TokenValidator tokenValidator;

    @Inject
    MeterRegistry meterRegistry;

    @Inject
    JwtMetricsCollector metricsCollector;

    @Test
    @DisplayName("Should record metrics for token validation")
    void shouldRecordMetricsForTokenValidation() {
        // Force initialization of metrics collector
        metricsCollector.updateCounters();

        String invalidToken = "invalid.jwt.token";

        assertThrows(TokenValidationException.class, () -> tokenValidator.createAccessToken(invalidToken),
                "Should have thrown TokenValidationException for invalid token");

        assertNotNull(meterRegistry.find(JwtPropertyKeys.METRICS.VALIDATION_ERRORS).counters(),
                "Error counters should be registered");

        assertFalse(meterRegistry.find(JwtPropertyKeys.METRICS.VALIDATION_ERRORS).counters().isEmpty(), "Error counters should be registered");
    }

    @Test
    @DisplayName("Should register JWKs cache size metrics")
    void shouldRegisterJwksCacheSizeMetrics() {
        // Verify that JWKS cache size gauges are registered
        assertNotNull(meterRegistry.find(JwtPropertyKeys.METRICS.JWKS_CACHE_SIZE).gauges(),
                "JWKS cache size gauges should be registered");
    }

    @Test
    @DisplayName("Should register metrics for all security event types")
    void shouldRegisterMetricsForAllSecurityEventTypes() {
        // Force initialization of metrics collector
        metricsCollector.updateCounters();

        // Verify that metrics are registered for all event types
        for (SecurityEventCounter.EventType eventType : SecurityEventCounter.EventType.values()) {
            // Skip success events as they're handled differently (registered under success metrics)
            if (eventType.getCategory() == null) {
                continue;
            }

            // Look for a counter with this event type
            boolean hasMetricForEventType = !meterRegistry.find(JwtPropertyKeys.METRICS.VALIDATION_ERRORS)
                    .tag("event_type", eventType.name())
                    .counters().isEmpty();

            assertTrue(hasMetricForEventType,
                    "Should have metrics registered for event type: " + eventType.name());
        }
    }

    /**
     * Provides different invalid token scenarios for testing.
     * Each scenario includes:
     * - A description of the test case
     * - An invalid token string
     * - The expected exception type
     */
    static Stream<Arguments> invalidTokenScenarios() {
        return Stream.of(
                // Empty token
                Arguments.of("Empty token", "", TokenValidationException.class),

                // Malformed token (not enough segments)
                Arguments.of("Malformed token (single segment)", "invalid", TokenValidationException.class),

                // Malformed token (only two segments)
                Arguments.of("Malformed token (two segments)", "header.payload", TokenValidationException.class),

                // Invalid format (not Base64)
                Arguments.of("Invalid format (not Base64)", "header.payload.signature", TokenValidationException.class),

                // Too long token
                Arguments.of("Too long token", "a".repeat(10000) + ".payload.signature", TokenValidationException.class)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTokenScenarios")
    @DisplayName("Should record metrics for different invalid token scenarios")
    void shouldRecordMetricsForDifferentInvalidTokens(String description, String invalidToken, Class<? extends Exception> expectedException) {
        // Force initialization of metrics collector
        metricsCollector.updateCounters();

        Exception thrownException = assertThrows(expectedException, () -> tokenValidator.createAccessToken(invalidToken), "Should throw an exception for invalid token: " + description);

        assertTrue(expectedException.isAssignableFrom(thrownException.getClass()),
                "Expected exception of type " + expectedException.getSimpleName() +
                        " but got " + thrownException.getClass().getSimpleName() +
                        " for scenario: " + description);

        assertFalse(meterRegistry.find(JwtPropertyKeys.METRICS.VALIDATION_ERRORS).counters().isEmpty(),
                "Error counters should be registered for scenario: " + description);
    }
}
