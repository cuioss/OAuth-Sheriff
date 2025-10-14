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
package de.cuioss.sheriff.oauth.core;

import de.cuioss.sheriff.oauth.core.test.generator.TestTokenGenerators;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for concurrent access to TokenValidator to reproduce the
 * UnsupportedOperationException race condition.
 */
class TokenValidatorConcurrencyTest {

    private static final int THREAD_COUNT = 200;
    private static final int ITERATIONS_PER_THREAD = 10;

    /**
     * Reproduces the race condition where multiple threads try to resolve issuer configs
     * while optimization to read-only access happens concurrently.
     *
     * This test should reproduce the UnsupportedOperationException that occurs when:
     * 1. Thread A optimizes the map to read-only (Map.copyOf)
     * 2. Thread B tries to put() into the now-immutable map
     */
    @RepeatedTest(5)
    void shouldReproduceRaceConditionWithReadOnlyOptimization() throws InterruptedException {
        // Generate test tokens using existing infrastructure
        var testToken1 = TestTokenGenerators.accessTokens().next();
        var testToken2 = TestTokenGenerators.accessTokens().next();

        // Create multiple issuer configs to trigger the race condition
        var issuerConfig1 = testToken1.getIssuerConfig();
        var issuerConfig2 = testToken2.getIssuerConfig();

        var parserConfig = ParserConfig.builder().build();
        var tokenValidator = TokenValidator.builder().parserConfig(parserConfig).issuerConfig(issuerConfig1).issuerConfig(issuerConfig2).build();

        // Use the first test token for validation
        var validJwt = testToken1.getRawToken();

        // Atomic counter for tracking results
        AtomicInteger successCount = new AtomicInteger(0);

        // Create thread pool and coordination
        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(THREAD_COUNT);

        // Submit concurrent tasks
        for (int i = 0; i < THREAD_COUNT; i++) {
            executor.submit(() -> {
                try {
                    // Wait for all threads to be ready
                    startLatch.await();

                    // Perform multiple iterations to increase chance of race condition
                    for (int j = 0; j < ITERATIONS_PER_THREAD; j++) {
                        // This should trigger the resolveIssuerConfig method
                        tokenValidator.createAccessToken(validJwt);
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    completionLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously to maximize chance of race condition
        startLatch.countDown();

        // Wait for completion with timeout
        assertTrue(completionLatch.await(30, TimeUnit.SECONDS),
                "Test should complete within 30 seconds");

        executor.shutdown();

        // Verify all operations succeeded - any exception will cause test to fail naturally
        assertEquals(THREAD_COUNT * ITERATIONS_PER_THREAD, successCount.get(),
                "All token validation operations should succeed");
    }

    /**
     * Simplified test that focuses specifically on the map optimization race condition.
     */
    @Test
    void shouldDemonstrateMapOptimizationRaceCondition() throws InterruptedException {
        // Generate test token using existing infrastructure
        var testToken = TestTokenGenerators.accessTokens().next();
        var issuerConfig = testToken.getIssuerConfig();
        var tokenValidator = TokenValidator.builder().parserConfig(ParserConfig.builder().build()).issuerConfig(issuerConfig).build();
        var validJwt = testToken.getRawToken();

        ExecutorService executor = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(50);

        // Launch multiple threads to create access tokens simultaneously
        for (int i = 0; i < 50; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < 20; j++) {
                        tokenValidator.createAccessToken(validJwt);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(20, TimeUnit.SECONDS));
        executor.shutdown();

        // Test passes if no exceptions occurred - any UnsupportedOperationException will fail the test naturally
    }
}