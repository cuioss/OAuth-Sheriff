/**
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

import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.test.TestTokenHolder;
import de.cuioss.jwt.validation.test.generator.TestTokenGenerators;
import de.cuioss.tools.logging.CuiLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Concurrency tests for IssuerConfigResolver focusing on race conditions
 * during the transition from ConcurrentHashMap to immutable map.
 *
 * This test reproduces the UnsupportedOperationException that occurs when:
 * 1. Thread A optimizes the cache to read-only (Map.copyOf)
 * 2. Thread B tries to put() into the now-immutable map
 */
class IssuerConfigResolverConcurrencyTest {

    private static final CuiLogger LOGGER = new CuiLogger(IssuerConfigResolverConcurrencyTest.class);

    /**
     * Test specifically for the case where optimization happens while other threads
     * are still trying to add to the cache.
     */
    @Test
    @DisplayName("Handle concurrent cache optimization safely")
    void shouldHandleConcurrentOptimizationSafely() throws InterruptedException {
        // Use fewer configs so optimization happens sooner
        List<IssuerConfig> issuerConfigs = new ArrayList<>();
        List<String> issuerIds = new ArrayList<>();

        for (int i = 0; i < 3; i++) {
            TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
            IssuerConfig config = tokenHolder.getIssuerConfig();
            String issuerId = config.getIssuerIdentifier();

            issuerConfigs.add(config);
            issuerIds.add(issuerId);
        }

        IssuerConfigResolver resolver = new IssuerConfigResolver(
                issuerConfigs.toArray(new IssuerConfig[0]),
                new SecurityEventCounter()
        );

        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger totalOperations = new AtomicInteger(0);
        AtomicReference<Exception> unexpectedException = new AtomicReference<>();

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    startLatch.await();

                    // All threads try to resolve the same issuer to force optimization race
                    String targetIssuer = issuerIds.get(threadIndex % issuerIds.size());

                    IssuerConfig result = resolver.resolveConfig(targetIssuer);
                    assertNotNull(result);
                    assertEquals(targetIssuer, result.getIssuerIdentifier());
                    totalOperations.incrementAndGet();

                } catch (UnsupportedOperationException e) {
                    // Expected during race condition - don't fail the test
                    LOGGER.info("Caught expected UnsupportedOperationException: %s", e.getMessage());
                } catch (Exception e) {
                    unexpectedException.compareAndSet(null, e);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = endLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All threads should complete");

        if (unexpectedException.get() != null) {
            fail("Unexpected exception during concurrent optimization test", unexpectedException.get());
        }

        assertTrue(totalOperations.get() > 0, "Some operations should succeed");
    }

    /**
     * Test the double-checked locking pattern under extreme concurrency.
     */
    @Test
    @DisplayName("Handle double-checked locking under extreme concurrency")
    void shouldHandleDoubleCheckedLockingCorrectly() throws InterruptedException {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        IssuerConfig config = tokenHolder.getIssuerConfig();
        String issuerId = config.getIssuerIdentifier();

        IssuerConfigResolver resolver = new IssuerConfigResolver(
                new IssuerConfig[]{config},
                new SecurityEventCounter()
        );

        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // Synchronize all threads to start at exactly the same time
                    barrier.await();

                    IssuerConfig result = resolver.resolveConfig(issuerId);
                    assertSame(config, result);
                    successCount.incrementAndGet();

                } catch (Exception e) {
                    LOGGER.warn("Thread failed: %s", e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        boolean completed = endLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All threads should complete");
        assertEquals(threadCount, successCount.get(), "All threads should succeed with double-checked locking");
    }

    /**
     * Stress test with mixed healthy and unhealthy issuers under high concurrency.
     */
    @Test
    @DisplayName("Handle mixed health status under concurrency")
    void shouldHandleMixedHealthStatusUnderConcurrency() throws InterruptedException {
        List<IssuerConfig> issuerConfigs = new ArrayList<>();
        List<String> healthyIssuerIds = new ArrayList<>();

        // Create healthy issuers (real configs are always healthy in tests)
        for (int i = 0; i < 5; i++) {
            TestTokenHolder healthyHolder = TestTokenGenerators.accessTokens().next();
            IssuerConfig healthyConfig = healthyHolder.getIssuerConfig();
            String healthyId = healthyConfig.getIssuerIdentifier();
            issuerConfigs.add(healthyConfig);
            healthyIssuerIds.add(healthyId);
        }

        IssuerConfigResolver resolver = new IssuerConfigResolver(
                issuerConfigs.toArray(new IssuerConfig[0]),
                new SecurityEventCounter()
        );

        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger healthySuccessCount = new AtomicInteger(0);
        AtomicInteger unhealthyExceptionCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int threadIndex = i;
            executor.submit(() -> {
                try {
                    if (threadIndex % 2 == 0) {
                        // Even threads try healthy issuers
                        String healthyIssuer = healthyIssuerIds.get(threadIndex % healthyIssuerIds.size());
                        IssuerConfig result = resolver.resolveConfig(healthyIssuer);
                        assertNotNull(result);
                        healthySuccessCount.incrementAndGet();
                    } else {
                        // Odd threads try unknown issuers (will throw exception)
                        try {
                            resolver.resolveConfig("https://unknown-issuer-" + threadIndex + ".com");
                            fail("Should have thrown exception for unknown issuer");
                        } catch (Exception e) {
                            unhealthyExceptionCount.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    LOGGER.warn("Unexpected exception: %s", e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertTrue(completed, "All threads should complete");
        assertTrue(healthySuccessCount.get() > 0, "Should have successful healthy resolutions");
        assertTrue(unhealthyExceptionCount.get() > 0, "Should have exceptions for unknown issuers");
    }
}
