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

import de.cuioss.jwt.validation.metrics.MeasurementType;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitor;
import de.cuioss.jwt.validation.test.TestTokenHolder;
import de.cuioss.jwt.validation.test.generator.TestTokenGenerators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.tools.logging.CuiLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test to verify that performance monitoring is properly integrated
 * into the TokenValidator pipeline and recording measurements during token validation.
 * <p>
 * This test demonstrates the integration between TokenValidator and TokenValidatorMonitor,
 * ensuring that performance metrics are captured for each pipeline step.
 *
 * @author Oliver Wolff
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("TokenValidator Performance Monitoring Integration Tests")
class TokenValidatorPerformanceIntegrationTest {

    private static final CuiLogger log = new CuiLogger(TokenValidatorPerformanceIntegrationTest.class);

    @Test
    @DisplayName("Should record performance metrics during token validation attempts")
    void shouldRecordPerformanceMetricsDuringValidation() {
        // Create a TokenValidator (will initialize with default performance monitor)
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        var issuerConfig = tokenHolder.getIssuerConfig();
        var tokenValidator = TokenValidator.builder().issuerConfig(issuerConfig).build();

        // Get the performance monitor
        TokenValidatorMonitor performanceMonitor = tokenValidator.getPerformanceMonitor();

        // Verify initial state - no measurements
        assertEquals(Duration.ZERO, performanceMonitor.getValidationMetrics(MeasurementType.COMPLETE_VALIDATION)
                .map(de.cuioss.tools.concurrent.StripedRingBufferStatistics::p50).orElse(Duration.ZERO));
        assertEquals(0, performanceMonitor.getSampleCount(MeasurementType.COMPLETE_VALIDATION));

        // Try to validate an invalid token (empty string) - this should record metrics even for failures
        try {
            tokenValidator.createAccessToken("");
            fail("Should have thrown TokenValidationException for empty token");
        } catch (Exception e) {
            // Expected - token validation should fail for empty string
        }

        // Verify that complete validation time was recorded (even for failed validation)
        assertTrue(performanceMonitor.getSampleCount(MeasurementType.COMPLETE_VALIDATION) > 0,
                "Complete validation time should be recorded even for failed validations");
        assertTrue(performanceMonitor.getValidationMetrics(MeasurementType.COMPLETE_VALIDATION)
                        .map(de.cuioss.tools.concurrent.StripedRingBufferStatistics::p50).orElse(Duration.ZERO).toNanos() > 0,
                "Complete validation average should be positive");

        // Try with a malformed token - this should get further in the pipeline
        try {
            tokenValidator.createAccessToken("not.a.valid.jwt.token");
            fail("Should have thrown TokenValidationException for malformed token");
        } catch (Exception e) {
            // Expected - token validation should fail for malformed token
        }

        // Verify that more measurements were recorded
        assertTrue(performanceMonitor.getSampleCount(MeasurementType.COMPLETE_VALIDATION) >= 2,
                "Should have at least 2 complete validation measurements");

        // Check if token parsing was attempted (depends on how far validation got)
        // For malformed tokens, parsing might be attempted
        var parsingCount = performanceMonitor.getSampleCount(MeasurementType.TOKEN_PARSING);
        var parsingAverage = performanceMonitor.getValidationMetrics(MeasurementType.TOKEN_PARSING)
                .map(de.cuioss.tools.concurrent.StripedRingBufferStatistics::p50).orElse(Duration.ZERO);

        if (parsingCount > 0) {
            assertTrue(parsingAverage.toNanos() > 0,
                    "Token parsing average should be positive when parsing was attempted");
        }

        log.info("Performance metrics after validation attempts:");
        log.info("- Complete validation: {} samples, avg {:.2f} μs",
                performanceMonitor.getSampleCount(MeasurementType.COMPLETE_VALIDATION),
                performanceMonitor.getValidationMetrics(MeasurementType.COMPLETE_VALIDATION)
                        .map(de.cuioss.tools.concurrent.StripedRingBufferStatistics::p50).orElse(Duration.ZERO).toNanos() / 1000.0);
        log.info("- Token parsing: {} samples, avg {:.2f} μs",
                performanceMonitor.getSampleCount(MeasurementType.TOKEN_PARSING),
                performanceMonitor.getValidationMetrics(MeasurementType.TOKEN_PARSING)
                        .map(de.cuioss.tools.concurrent.StripedRingBufferStatistics::p50).orElse(Duration.ZERO).toNanos() / 1000.0);
        log.info("- Header validation: {} samples, avg {:.2f} μs",
                performanceMonitor.getSampleCount(MeasurementType.HEADER_VALIDATION),
                performanceMonitor.getValidationMetrics(MeasurementType.HEADER_VALIDATION)
                        .map(de.cuioss.tools.concurrent.StripedRingBufferStatistics::p50).orElse(Duration.ZERO).toNanos() / 1000.0);
        log.info("- Signature validation: {} samples, avg {:.2f} μs",
                performanceMonitor.getSampleCount(MeasurementType.SIGNATURE_VALIDATION),
                performanceMonitor.getValidationMetrics(MeasurementType.SIGNATURE_VALIDATION)
                        .map(de.cuioss.tools.concurrent.StripedRingBufferStatistics::p50).orElse(Duration.ZERO).toNanos() / 1000.0);
        log.info("- Claims validation: {} samples, avg {:.2f} μs",
                performanceMonitor.getSampleCount(MeasurementType.CLAIMS_VALIDATION),
                performanceMonitor.getValidationMetrics(MeasurementType.CLAIMS_VALIDATION)
                        .map(de.cuioss.tools.concurrent.StripedRingBufferStatistics::p50).orElse(Duration.ZERO).toNanos() / 1000.0);
        log.info("- JWKS operations: {} samples, avg {:.2f} μs",
                performanceMonitor.getSampleCount(MeasurementType.JWKS_OPERATIONS),
                performanceMonitor.getValidationMetrics(MeasurementType.JWKS_OPERATIONS)
                        .map(de.cuioss.tools.concurrent.StripedRingBufferStatistics::p50).orElse(Duration.ZERO).toNanos() / 1000.0);
    }

    @Test
    @DisplayName("Should provide access to performance monitor through getter")
    void shouldProvideAccessToPerformanceMonitor() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        var issuerConfig = tokenHolder.getIssuerConfig();
        var tokenValidator = TokenValidator.builder().issuerConfig(issuerConfig).build();

        // Verify performance monitor is accessible
        TokenValidatorMonitor performanceMonitor = tokenValidator.getPerformanceMonitor();
        assertNotNull(performanceMonitor, "Performance monitor should be accessible");

        // Verify it's properly initialized
        for (MeasurementType type : MeasurementType.values()) {
            assertEquals(Duration.ZERO, performanceMonitor.getValidationMetrics(type)
                            .map(de.cuioss.tools.concurrent.StripedRingBufferStatistics::p50).orElse(Duration.ZERO),
                    "Initial average should be zero for " + type);
            assertEquals(0, performanceMonitor.getSampleCount(type),
                    "Initial sample count should be zero for " + type);
        }
    }
}