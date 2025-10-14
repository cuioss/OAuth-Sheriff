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

import de.cuioss.sheriff.oauth.core.security.SecurityEventCounter;
import de.cuioss.sheriff.oauth.core.test.TestTokenHolder;
import de.cuioss.sheriff.oauth.core.test.generator.TestTokenGenerators;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Performance test for IssuerConfigResolver.resolveConfig() to verify
 * lock-free operation and high-throughput under concurrent load.
 *
 * This test measures the actual performance characteristics of issuer config
 * resolution to ensure the 1000+ ops/s target is achieved without synchronization overhead.
 */
class IssuerConfigResolverPerformanceTest {

    private IssuerConfigResolver issuerConfigResolver;
    private String issuerIdentifier;

    @BeforeEach
    void setUp() {
        // Create a properly configured issuer config and resolver
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        IssuerConfig issuerConfig = tokenHolder.getIssuerConfig();
        issuerIdentifier = issuerConfig.getIssuerIdentifier();

        SecurityEventCounter securityEventCounter = new SecurityEventCounter();
        issuerConfigResolver = new IssuerConfigResolver(List.of(issuerConfig), securityEventCounter);
    }

    @Test
    @Timeout(value = 30, unit = TimeUnit.SECONDS)
    @DisplayName("Achieve >1000 ops/s throughput under concurrent load")
    void achievesHighThroughputUnderConcurrentLoad() throws InterruptedException {
        int threadCount = 100;
        int operationsPerThread = 50;
        int totalOperations = threadCount * operationsPerThread;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicLong totalNanoTime = new AtomicLong(0);
        AtomicLong maxTime = new AtomicLong(0);

        // Submit all threads
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // Wait for all threads to be ready
                    startLatch.await();

                    for (int j = 0; j < operationsPerThread; j++) {
                        long startTime = System.nanoTime();

                        IssuerConfig result = issuerConfigResolver.resolveConfig(issuerIdentifier);

                        long duration = System.nanoTime() - startTime;
                        totalNanoTime.addAndGet(duration);
                        maxTime.updateAndGet(current -> Math.max(current, duration));

                        assertNotNull(result);
                        assertEquals(issuerIdentifier, result.getIssuerIdentifier());
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (IllegalArgumentException | IllegalStateException e) {
                    fail("Thread execution failed: " + e.getMessage());
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Start all threads simultaneously
        long testStartTime = System.nanoTime();
        startLatch.countDown();

        // Wait for all threads to complete
        boolean completed = endLatch.await(25, TimeUnit.SECONDS);
        long testDuration = System.nanoTime() - testStartTime;

        assertTrue(completed, "Test did not complete within timeout");
        executor.shutdown();

        // Calculate performance metrics
        assertEquals(totalOperations, successCount.get(), "All operations should succeed");

        double avgTimeMs = totalNanoTime.get() / (double) totalOperations / 1_000_000;
        double maxTimeMs = maxTime.get() / 1_000_000.0;
        double totalTimeSeconds = testDuration / 1_000_000_000.0;
        double throughputOpsPerSec = totalOperations / totalTimeSeconds;

        // Performance assertions - should be much better after lock-free fix
        assertTrue(avgTimeMs < 0.5, "Average time should be under 0.5ms after fix (was: %.3f ms)".formatted(avgTimeMs));
        assertTrue(throughputOpsPerSec > 10000, "Throughput should exceed 10000 ops/s after fix (was: %.0f ops/s)".formatted(throughputOpsPerSec));
        assertTrue(maxTimeMs < 10, "Maximum time should be reasonable (was: %.2f ms)".formatted(maxTimeMs));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Demonstrate lock-free warmup without blocking")
    void demonstratesLockFreeWarmup() throws InterruptedException {
        // Create fresh resolvers to test warmup behavior
        int resolverCount = 5;
        int threadsPerResolver = 20;
        ExecutorService executor = Executors.newFixedThreadPool(resolverCount * threadsPerResolver);
        CountDownLatch latch = new CountDownLatch(resolverCount * threadsPerResolver);

        AtomicInteger totalOperations = new AtomicInteger(0);
        AtomicLong totalTime = new AtomicLong(0);

        SecurityEventCounter securityEventCounter = new SecurityEventCounter();

        for (int i = 0; i < resolverCount; i++) {
            // Create a new resolver for each group
            TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
            IssuerConfigResolver resolver = new IssuerConfigResolver(List.of(tokenHolder.getIssuerConfig()), securityEventCounter);
            String issuerId = tokenHolder.getIssuerConfig().getIssuerIdentifier();

            // Launch multiple threads per resolver
            for (int j = 0; j < threadsPerResolver; j++) {
                executor.submit(() -> {
                    try {
                        long startTime = System.nanoTime();

                        // First call triggers warmup
                        IssuerConfig result = resolver.resolveConfig(issuerId);

                        long duration = System.nanoTime() - startTime;
                        totalTime.addAndGet(duration);
                        totalOperations.incrementAndGet();

                        assertNotNull(result);
                    } catch (IllegalArgumentException | IllegalStateException e) {
                        fail("Warmup test failed: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        boolean completed = latch.await(8, TimeUnit.SECONDS);
        assertTrue(completed, "Warmup test should complete quickly");
        executor.shutdown();

        double avgTimeMs = totalTime.get() / (double) totalOperations.get() / 1_000_000;
        double throughputOpsPerSec = totalOperations.get() / (totalTime.get() / 1_000_000_000.0);

        // Warmup should be fast with lock-free implementation
        // Threshold increased to 2.0ms to account for CI environment variability
        assertTrue(avgTimeMs < 2.0, "Warmup operations should be fast (was: %.2f ms)".formatted(avgTimeMs));
        assertTrue(throughputOpsPerSec > 1000, "Warmup throughput should be good (was: %.0f ops/s)".formatted(throughputOpsPerSec));
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS)
    @DisplayName("Ensure uniform response times indicate no convoy effect")
    void ensuresNoConvoyEffect() throws InterruptedException {
        int threadCount = 50;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CyclicBarrier barrier = new CyclicBarrier(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        ConcurrentLinkedQueue<Long> executionTimes = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // Synchronize all threads to start at the same time
                    barrier.await();

                    long startTime = System.nanoTime();
                    IssuerConfig result = issuerConfigResolver.resolveConfig(issuerIdentifier);
                    long duration = System.nanoTime() - startTime;

                    executionTimes.add(duration);
                    assertNotNull(result);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (BrokenBarrierException e) {
                    fail("Barrier broken: " + e.getMessage());
                } catch (IllegalArgumentException | IllegalStateException e) {
                    fail("Thread failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertTrue(completed, "All threads should complete quickly");
        executor.shutdown();

        // Analyze timing distribution to detect convoy effect
        assertEquals(threadCount, executionTimes.size());

        long totalTime = 0;
        long minTime = Long.MAX_VALUE;
        long maxTime = 0;

        for (Long time : executionTimes) {
            totalTime += time;
            minTime = Math.min(minTime, time);
            maxTime = Math.max(maxTime, time);
        }

        double avgTimeMs = totalTime / (double) threadCount / 1_000_000;
        double minTimeMs = minTime / 1_000_000.0;
        double maxTimeMs = maxTime / 1_000_000.0;

        // Skip ratio test if measurements are too fast to be meaningful
        if (minTimeMs > 0.010 && avgTimeMs >= 0.10) {
            double ratio = maxTimeMs / minTimeMs;
            // Without convoy effect, times should be relatively uniform
            assertTrue(ratio < 5, "Max/Min ratio should be low without convoy effect (was: %.1f - min: %.2f ms, max: %.2f ms)".formatted(
                    ratio, minTimeMs, maxTimeMs
            ));
        }

        assertTrue(avgTimeMs < 0.5, "Average time should be low without blocking (was: %.2f ms)".formatted(avgTimeMs));
    }
}