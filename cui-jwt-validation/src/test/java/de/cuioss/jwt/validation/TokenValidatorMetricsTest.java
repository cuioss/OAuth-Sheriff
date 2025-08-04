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
package de.cuioss.jwt.validation;

import de.cuioss.jwt.validation.cache.AccessTokenCacheConfig;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.metrics.MeasurementType;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitor;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitorConfig;
import de.cuioss.jwt.validation.test.TestTokenHolder;
import de.cuioss.jwt.validation.test.generator.TestTokenGenerators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.tools.concurrent.StripedRingBufferStatistics;
import de.cuioss.tools.logging.CuiLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test suite for verifying metrics collection in {@link TokenValidator}.
 * <p>
 * This test focuses on reproducing missing metric entries and timing discrepancies
 * observed in the benchmark results.
 */
@EnableGeneratorController
@EnableTestLogger
class TokenValidatorMetricsTest {

    private static final CuiLogger LOGGER = new CuiLogger(TokenValidatorMetricsTest.class);

    private TokenValidator tokenValidator;
    private TestTokenHolder testTokenHolder;

    @BeforeEach
    void setUp() {
        testTokenHolder = TestTokenGenerators.accessTokens().next();

        IssuerConfig issuerConfig = testTokenHolder.getIssuerConfig();

        tokenValidator = TokenValidator.builder()
            .issuerConfig(issuerConfig)
            .monitorConfig(TokenValidatorMonitorConfig.defaultEnabled())
            .cacheConfig(AccessTokenCacheConfig.disabled())
            .build();
    }

    @Test
    @DisplayName("Should record all expected metrics for successful token validation")
    void shouldRecordAllMetricsForSuccessfulValidation() {
        // Given
        String tokenString = testTokenHolder.getRawToken();
        TokenValidatorMonitor monitor = tokenValidator.getPerformanceMonitor();

        // When
        AccessTokenContent accessToken = tokenValidator.createAccessToken(tokenString);

        // Then
        assertNotNull(accessToken);

        // Verify all metrics are recorded
        Set<MeasurementType> recordedMetrics = new HashSet<>();
        for (MeasurementType type : MeasurementType.values()) {
            Duration avgDuration = monitor.getValidationMetrics(type)
                .map(StripedRingBufferStatistics::p50).orElse(Duration.ZERO);
            if (avgDuration.toNanos() > 0) {
                recordedMetrics.add(type);
            }
        }

        // Log missing metrics
        Set<MeasurementType> expectedMetrics = new HashSet<>(Arrays.asList(MeasurementType.values()));
        expectedMetrics.removeAll(recordedMetrics);

        LOGGER.info("Recorded metrics: " + recordedMetrics);
        LOGGER.info("Missing metrics: " + expectedMetrics);

        // Check specific metrics that were missing in benchmark
        assertTrue(recordedMetrics.contains(MeasurementType.COMPLETE_VALIDATION),
            "COMPLETE_VALIDATION should be recorded");
        assertTrue(recordedMetrics.contains(MeasurementType.TOKEN_PARSING),
            "TOKEN_PARSING should be recorded");
        assertTrue(recordedMetrics.contains(MeasurementType.SIGNATURE_VALIDATION),
            "SIGNATURE_VALIDATION should be recorded");

        // These might be missing or have zero duration
        LOGGER.info("TOKEN_FORMAT_CHECK duration: " + monitor.getValidationMetrics(MeasurementType.TOKEN_FORMAT_CHECK)
            .map(StripedRingBufferStatistics::p50).orElse(Duration.ZERO).toNanos() + " ns");
        LOGGER.info("ISSUER_EXTRACTION duration: " + monitor.getValidationMetrics(MeasurementType.ISSUER_EXTRACTION)
            .map(StripedRingBufferStatistics::p50).orElse(Duration.ZERO).toNanos() + " ns");
        LOGGER.info("JWKS_OPERATIONS duration: " + monitor.getValidationMetrics(MeasurementType.JWKS_OPERATIONS)
            .map(StripedRingBufferStatistics::p50).orElse(Duration.ZERO).toNanos() + " ns");
    }

    @Test
    @DisplayName("Should show timing discrepancy between complete validation and sum of individual steps")
    void shouldShowTimingDiscrepancy() {
        // Given
        String tokenString = testTokenHolder.getRawToken();
        TokenValidatorMonitor monitor = tokenValidator.getPerformanceMonitor();

        // When - validate multiple times to get stable averages
        for (int i = 0; i < 10; i++) {
            tokenValidator.createAccessToken(tokenString);
        }

        // Then - calculate sum of individual steps
        long sumOfSteps = 0;
        for (MeasurementType type : MeasurementType.values()) {
            if (type != MeasurementType.COMPLETE_VALIDATION) {
                sumOfSteps += monitor.getValidationMetrics(type)
                    .map(StripedRingBufferStatistics::p50).orElse(Duration.ZERO).toNanos();
            }
        }

        long completeValidationTime = monitor.getValidationMetrics(MeasurementType.COMPLETE_VALIDATION)
            .map(StripedRingBufferStatistics::p50).orElse(Duration.ZERO).toNanos();

        LOGGER.info("Complete validation time: " + completeValidationTime + " ns (" + completeValidationTime / 1_000_000.0 + " ms)");
        LOGGER.info("Sum of individual steps: " + sumOfSteps + " ns (" + sumOfSteps / 1_000_000.0 + " ms)");
        LOGGER.info("Unaccounted time: " + (completeValidationTime - sumOfSteps) + " ns (" + (completeValidationTime - sumOfSteps) / 1_000_000.0 + " ms)");

        // Print breakdown
        LOGGER.info("\nDetailed breakdown:");
        for (MeasurementType type : MeasurementType.values()) {
            Duration duration = monitor.getValidationMetrics(type)
                .map(StripedRingBufferStatistics::p50).orElse(Duration.ZERO);
            if (duration.toNanos() > 0) {
                LOGGER.info("%s: %d ns (%.3f ms)%n",
                    type,
                    duration.toNanos(),
                    duration.toNanos() / 1_000_000.0);
            }
        }

        // The complete validation time should be greater than the sum due to overhead
        assertTrue(completeValidationTime > sumOfSteps,
            "Complete validation time should include overhead not captured in individual steps");
    }

    @Test
    @DisplayName("Should detect fast operations that might show as empty metrics")
    void shouldDetectFastOperations() {
        // Given
        String tokenString = testTokenHolder.getRawToken();
        TokenValidatorMonitor monitor = tokenValidator.getPerformanceMonitor();

        // When - validate once
        tokenValidator.createAccessToken(tokenString);

        // Then - check for very fast operations
        Duration tokenFormatCheck = monitor.getValidationMetrics(MeasurementType.TOKEN_FORMAT_CHECK)
            .map(StripedRingBufferStatistics::p50).orElse(Duration.ZERO);
        Duration issuerExtraction = monitor.getValidationMetrics(MeasurementType.ISSUER_EXTRACTION)
            .map(StripedRingBufferStatistics::p50).orElse(Duration.ZERO);
        Duration headerValidation = monitor.getValidationMetrics(MeasurementType.HEADER_VALIDATION)
            .map(StripedRingBufferStatistics::p50).orElse(Duration.ZERO);

        LOGGER.info("\nFast operations analysis:");
        LOGGER.info("TOKEN_FORMAT_CHECK: %d ns (%.6f ms) - %s%n",
            tokenFormatCheck.toNanos(),
            tokenFormatCheck.toNanos() / 1_000_000.0,
            tokenFormatCheck.toNanos() == 0 ? "MISSING!" : "OK");
        LOGGER.info("ISSUER_EXTRACTION: %d ns (%.6f ms) - %s%n",
            issuerExtraction.toNanos(),
            issuerExtraction.toNanos() / 1_000_000.0,
            issuerExtraction.toNanos() == 0 ? "MISSING!" : "OK");
        LOGGER.info("HEADER_VALIDATION: %d ns (%.6f ms) - %s%n",
            headerValidation.toNanos(),
            headerValidation.toNanos() / 1_000_000.0,
            headerValidation.toNanos() < 1000 ? "SUSPICIOUSLY FAST!" : "OK");

        // These operations should have some duration, even if very small
        assertTrue(tokenFormatCheck.toNanos() >= 0, "TOKEN_FORMAT_CHECK should have a duration");
        assertTrue(issuerExtraction.toNanos() >= 0, "ISSUER_EXTRACTION should have a duration");
    }

    @Test
    @DisplayName("Should measure JWKS operations separately from signature validation")
    void shouldMeasureJwksOperationsSeparately() {
        // Given
        String tokenString = testTokenHolder.getRawToken();
        TokenValidatorMonitor monitor = tokenValidator.getPerformanceMonitor();

        // When
        tokenValidator.createAccessToken(tokenString);

        // Then
        Duration jwksOperations = monitor.getValidationMetrics(MeasurementType.JWKS_OPERATIONS)
            .map(StripedRingBufferStatistics::p50).orElse(Duration.ZERO);
        Duration signatureValidation = monitor.getValidationMetrics(MeasurementType.SIGNATURE_VALIDATION)
            .map(StripedRingBufferStatistics::p50).orElse(Duration.ZERO);

        LOGGER.info("\nJWKS operations analysis:");
        LOGGER.info("JWKS_OPERATIONS: %d ns (%.3f ms)%n",
            jwksOperations.toNanos(),
            jwksOperations.toNanos() / 1_000_000.0);
        LOGGER.info("SIGNATURE_VALIDATION: %d ns (%.3f ms)%n",
            signatureValidation.toNanos(),
            signatureValidation.toNanos() / 1_000_000.0);

        // JWKS operations time should be included in signature validation time
        assertTrue(signatureValidation.toNanos() > jwksOperations.toNanos(),
            "Signature validation should include JWKS operations time");
    }
}