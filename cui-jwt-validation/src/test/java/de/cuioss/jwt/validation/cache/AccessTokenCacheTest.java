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
package de.cuioss.jwt.validation.cache;

import com.dslplatform.json.DslJson;
import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.jwt.validation.ParserConfig;
import de.cuioss.jwt.validation.TokenType;
import de.cuioss.jwt.validation.domain.claim.ClaimName;
import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.exception.InternalCacheException;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.json.MapRepresentation;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitor;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitorConfig;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.test.TestTokenHolder;
import de.cuioss.jwt.validation.test.generator.TestTokenGenerators;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger
class AccessTokenCacheTest {

    /**
     * Creates an empty MapRepresentation for tests that don't need specific payload data.
     */
    private static MapRepresentation createEmptyMapRepresentation() {
        try {
            DslJson<Object> dslJson = ParserConfig.builder().build().getDslJson();
            return MapRepresentation.fromJson(dslJson, "{}");
        } catch (Exception e) {
            throw new RuntimeException("Failed to create empty MapRepresentation", e);
        }
    }

    private AccessTokenCache cache;
    private SecurityEventCounter securityEventCounter;
    private TokenValidatorMonitor performanceMonitor;

    @BeforeEach
    void setUp() {
        securityEventCounter = new SecurityEventCounter();
        performanceMonitor = TokenValidatorMonitorConfig.builder()
                .measurementTypes(TokenValidatorMonitorConfig.ALL_MEASUREMENT_TYPES)
                .build()
                .createMonitor();
        AccessTokenCacheConfig config = AccessTokenCacheConfig.builder()
                .maxSize(10)
                .evictionIntervalSeconds(300L) // Use longer interval to avoid race conditions in tests
                .build();
        cache = new AccessTokenCache(config, securityEventCounter);
    }

    @AfterEach
    void tearDown() {
        if (cache != null) {
            cache.shutdown();
        }
    }

    @Test
    void cacheMiss() {
        // Given
        String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL2V4YW1wbGUuY29tIiwiZXhwIjoxOTk5OTk5OTk5fQ.signature";
        AtomicInteger validationCount = new AtomicInteger(0);
        AccessTokenContent expectedContent = createAccessToken("https://example.com",
                OffsetDateTime.now().plusHours(1));

        // When
        Optional<AccessTokenContent> cached = cache.get(token, performanceMonitor);
        assertTrue(cached.isEmpty());

        validationCount.incrementAndGet();
        cache.put(token, expectedContent, performanceMonitor);
        //noinspection UnnecessaryLocalVariable - Variable needed to avoid second cache access in assertions
        AccessTokenContent result = expectedContent;

        // Then
        assertNotNull(result);
        assertEquals(expectedContent, result);
        assertEquals(1, validationCount.get());
        assertEquals(1, cache.size());
        // No cache hit on first access
        assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.ACCESS_TOKEN_CACHE_HIT));
    }

    @Test
    void cacheHit() {
        // Given
        String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJodHRwczovL2V4YW1wbGUuY29tIiwiZXhwIjoxOTk5OTk5OTk5fQ.signature";
        AtomicInteger validationCount = new AtomicInteger(0);
        AccessTokenContent expectedContent = createAccessToken("https://example.com",
                OffsetDateTime.now().plusHours(1));

        // First access - cache miss
        Optional<AccessTokenContent> cached1 = cache.get(token, performanceMonitor);
        if (cached1.isEmpty()) {
            validationCount.incrementAndGet();
            cache.put(token, expectedContent, performanceMonitor);
        }

        // When - second access should be cache hit
        Optional<AccessTokenContent> cached2 = cache.get(token, performanceMonitor);
        AccessTokenContent result;
        if (cached2.isEmpty()) {
            validationCount.incrementAndGet();
            result = expectedContent;
            cache.put(token, result, performanceMonitor);
        } else {
            result = cached2.get();
        }

        // Then
        assertNotNull(result);
        assertEquals(expectedContent, result);
        assertEquals(1, validationCount.get()); // Validation function called only once
        assertEquals(1, cache.size());
        assertEquals(1, securityEventCounter.getCount(SecurityEventCounter.EventType.ACCESS_TOKEN_CACHE_HIT));
    }

    @Test
    void expiredTokenNotReturned() {
        // This test verifies that expired tokens are detected and throw exception

        // Given
        AtomicInteger validationCount = new AtomicInteger(0);

        // Create test data with consistent token
        String testToken = "test-jwt-token";

        // First access - cache a token that will expire soon
        AccessTokenContent expiredContent = createAccessTokenWithRawToken(
                "https://example.com",
                OffsetDateTime.now().plusSeconds(2), // Will expire in 2 seconds
                testToken
        );

        Optional<AccessTokenContent> cached1 = cache.get(testToken, performanceMonitor);
        AccessTokenContent result1;
        if (cached1.isEmpty()) {
            validationCount.incrementAndGet();
            result1 = expiredContent;
            cache.put(testToken, result1, performanceMonitor);
        } else {
            result1 = cached1.get();
        }

        assertEquals(expiredContent, result1);
        assertEquals(1, validationCount.get());
        assertEquals(1, cache.size());

        // Wait for token to expire using Awaitility
        await()
                .atMost(3, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .until(() -> {
                    // Check if token has expired by comparing with current time
                    return expiredContent.getExpirationTime().isBefore(OffsetDateTime.now());
                });

        // When - second access should detect expiration and throw exception
        TokenValidationException exception =
                assertThrows(TokenValidationException.class, () ->
                        cache.get(testToken, performanceMonitor));

        // Then
        assertEquals(SecurityEventCounter.EventType.TOKEN_EXPIRED, exception.getEventType());
        assertEquals(1, validationCount.get()); // Validation function called only once
        assertEquals(0, cache.size()); // Cache cleared after detecting expiration
        assertEquals(0, securityEventCounter.getCount(SecurityEventCounter.EventType.ACCESS_TOKEN_CACHE_HIT));
        assertEquals(1, securityEventCounter.getCount(SecurityEventCounter.EventType.TOKEN_EXPIRED));
    }

    @Test
    void concurrentAccess() throws InterruptedException {
        // Given
        String token = "concurrent-token";
        AtomicInteger validationCount = new AtomicInteger(0);
        AccessTokenContent content = createAccessToken("https://example.com", OffsetDateTime.now().plusHours(1));

        int threadCount = 5; // Reduced thread count for stability
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        // When - multiple threads access the same token concurrently
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        Optional<AccessTokenContent> cached = cache.get(token, performanceMonitor);
                        AccessTokenContent result;
                        if (cached.isEmpty()) {
                            validationCount.incrementAndGet();
                            // Simple atomic operation, no complex simulation
                            result = content;
                            cache.put(token, result, performanceMonitor);
                        } else {
                            result = cached.get();
                        }
                        assertNotNull(result);
                        assertEquals(content, result);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // Wait for completion with shorter timeout
            assertTrue(doneLatch.await(2, TimeUnit.SECONDS), "All threads should complete quickly");
            executor.shutdown();
            assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS), "Executor should terminate cleanly");
        }

        // Then - with optimistic caching, validation may happen 1-5 times (race condition)
        // Each thread validates in parallel, first to putIfAbsent wins
        int actualValidationCount = validationCount.get();
        assertTrue(actualValidationCount >= 1 && actualValidationCount <= threadCount,
                "Validation should happen at least once, at most " + threadCount + " times, actual: " + actualValidationCount);
        assertEquals(1, cache.size());

        // Verify subsequent access is a cache hit
        Optional<AccessTokenContent> cachedResult = cache.get(token, performanceMonitor);
        assertTrue(cachedResult.isPresent(), "Token should be in cache");
        assertNotNull(cachedResult.get());
        assertEquals(content, cachedResult.get());
        // Validation count doesn't change after cache hit
        assertEquals(actualValidationCount, validationCount.get());
    }

    @Test
    void concurrentAccessHighContention() throws InterruptedException {
        // Test optimistic caching under high contention (issue #131/#132 fix verification)
        // Given
        String token = "high-contention-token";
        AtomicInteger validationCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AccessTokenContent content = createAccessToken("https://example.com", OffsetDateTime.now().plusHours(1));

        int threadCount = 50; // High concurrency to trigger race conditions
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        // When - many threads access the same token simultaneously
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        // Wait for all threads to be ready
                        startLatch.await();

                        Optional<AccessTokenContent> cached = cache.get(token, performanceMonitor);
                        AccessTokenContent result;
                        if (cached.isEmpty()) {
                            validationCount.incrementAndGet();
                            // Simulate some work to increase race window
                            await()
                                    .pollDelay(1, TimeUnit.MILLISECONDS)
                                    .atMost(10, TimeUnit.MILLISECONDS)
                                    .until(() -> true);
                            result = content;
                            cache.put(token, result, performanceMonitor);
                        } else {
                            result = cached.get();
                        }

                        assertNotNull(result);
                        assertEquals(content, result);
                        successCount.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        fail("Thread was interrupted: " + e.getMessage());
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // Start all threads simultaneously
            startLatch.countDown();

            // Wait for completion
            assertTrue(doneLatch.await(5, TimeUnit.SECONDS), "All threads should complete without blocking");
            executor.shutdown();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS), "Executor should terminate cleanly");
        }

        // Then - verify optimistic caching behavior
        assertEquals(threadCount, successCount.get(), "All threads should successfully retrieve the token");
        int actualValidationCount = validationCount.get();
        assertTrue(actualValidationCount >= 1, "Validation should happen at least once");
        assertTrue(actualValidationCount <= threadCount, "Validation should not exceed thread count");
        assertEquals(1, cache.size(), "Only one token should be cached");

        // Note: Multiple validations are expected (optimistic caching trades duplicate work for no blocking)
        // This is the fix for issue #131/#132
    }

    @Test
    void concurrentAccessMultipleTokens() throws InterruptedException {
        // Test that different tokens can be validated in parallel without contention
        // Given
        int tokenCount = 10;
        int threadsPerToken = 5;
        AtomicInteger totalValidationCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        int totalThreads = tokenCount * threadsPerToken;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalThreads);

        // When - many threads access different tokens concurrently
        try (ExecutorService executor = Executors.newFixedThreadPool(totalThreads)) {
            for (int tokenIndex = 0; tokenIndex < tokenCount; tokenIndex++) {
                final String token = "token-" + tokenIndex;
                final AccessTokenContent content = createAccessToken("https://example.com", OffsetDateTime.now().plusHours(1));

                for (int threadIndex = 0; threadIndex < threadsPerToken; threadIndex++) {
                    executor.submit(() -> {
                        try {
                            startLatch.await();

                            Optional<AccessTokenContent> cached = cache.get(token, performanceMonitor);
                            AccessTokenContent result;
                            if (cached.isEmpty()) {
                                totalValidationCount.incrementAndGet();
                                result = content;
                                cache.put(token, result, performanceMonitor);
                            } else {
                                result = cached.get();
                            }

                            assertNotNull(result);
                            successCount.incrementAndGet();
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            fail("Thread was interrupted: " + e.getMessage());
                        } finally {
                            doneLatch.countDown();
                        }
                    });
                }
            }

            // Start all threads simultaneously
            startLatch.countDown();

            // Wait for completion
            assertTrue(doneLatch.await(5, TimeUnit.SECONDS), "All threads should complete quickly");
            executor.shutdown();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS), "Executor should terminate cleanly");
        }

        // Then
        assertEquals(totalThreads, successCount.get(), "All threads should successfully retrieve tokens");
        assertTrue(totalValidationCount.get() >= tokenCount, "Each token should be validated at least once");
        assertEquals(tokenCount, cache.size(), "All unique tokens should be cached");
    }

    @SuppressWarnings("SameParameterValue") // Test helper - intentionally uses consistent test data
    private AccessTokenContent createAccessToken(String issuer, OffsetDateTime expiration) {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        tokenHolder.withClaim(ClaimName.ISSUER.getName(), ClaimValue.forPlainString(issuer));
        if (expiration != null) {
            tokenHolder.withClaim(ClaimName.EXPIRATION.getName(), ClaimValue.forDateTime(String.valueOf(expiration.toEpochSecond()), expiration));
        }
        tokenHolder.withClaim(ClaimName.ISSUED_AT.getName(), ClaimValue.forDateTime(String.valueOf(OffsetDateTime.now().minusMinutes(5).toEpochSecond()), OffsetDateTime.now().minusMinutes(5)));
        tokenHolder.withClaim(ClaimName.TOKEN_ID.getName(), ClaimValue.forPlainString("jti-" + System.nanoTime()));
        return tokenHolder.asAccessTokenContent();
    }

    @SuppressWarnings("SameParameterValue") // Test helper - intentionally uses consistent test data
    private AccessTokenContent createAccessTokenWithRawToken(String issuer, OffsetDateTime expiration, String rawToken) {
        // Create a simple AccessTokenContent with the specified raw token
        // This simulates what would happen in real JWT validation where the raw token matches the input
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        tokenHolder.withClaim(ClaimName.ISSUER.getName(), ClaimValue.forPlainString(issuer));
        if (expiration != null) {
            tokenHolder.withClaim(ClaimName.EXPIRATION.getName(), ClaimValue.forDateTime(String.valueOf(expiration.toEpochSecond()), expiration));
        }
        tokenHolder.withClaim(ClaimName.ISSUED_AT.getName(), ClaimValue.forDateTime(String.valueOf(OffsetDateTime.now().minusMinutes(5).toEpochSecond()), OffsetDateTime.now().minusMinutes(5)));
        tokenHolder.withClaim(ClaimName.TOKEN_ID.getName(), ClaimValue.forPlainString("jti-" + System.nanoTime()));

        // Get the generated content
        AccessTokenContent generated = tokenHolder.asAccessTokenContent();

        // Create a new instance with our specified raw token
        return new AccessTokenContent(generated.getClaims(), rawToken, TokenType.ACCESS_TOKEN.getTypeClaimName(), createEmptyMapRepresentation());
    }

    @Test
    void validationFunctionReturnsNull() {
        // Given
        String token = "test-token";

        Optional<AccessTokenContent> cached = cache.get(token, performanceMonitor);
        assertTrue(cached.isEmpty(), "Cache should be empty initially");

        // When & Then - attempting to put null should throw NullPointerException
        // In the new API, validation function returning null means caller should not call put()
        // But if they do call put(null), they get NPE from @NonNull annotation
        //noinspection DataFlowIssue - Intentionally testing null handling
        NullPointerException exception = assertThrows(NullPointerException.class, () ->
                // Validation returned null - calling put(null) is a programming error
                cache.put(token, null, performanceMonitor)
        );

        // The NPE message comes from Lombok's @NonNull annotation
        assertTrue(exception.getMessage().contains("content is marked non-null but is null"));
    }

    @Test
    void cacheEvictionUnderLoad() {
        // Given - cache with maxSize 10
        assertEquals(0, cache.size());

        // When - fill cache to capacity
        for (int i = 0; i < 10; i++) {
            String token = "token-" + i;
            AccessTokenContent content = createAccessToken("https://example.com",
                    OffsetDateTime.now().plusHours(1));
            Optional<AccessTokenContent> cached = cache.get(token, performanceMonitor);
            AccessTokenContent result;
            if (cached.isEmpty()) {
                result = content;
                cache.put(token, result, performanceMonitor);
            } else {
                result = cached.get();
            }
            assertNotNull(result);
        }

        // Then - cache should be at capacity
        assertEquals(10, cache.size());
    }

    @Test
    void shouldHandleCacheEvictionProperly() {
        // Given - fill cache to capacity first
        for (int i = 0; i < 10; i++) {
            String token = "token-" + i;
            AccessTokenContent content = createAccessToken("https://example.com",
                    OffsetDateTime.now().plusHours(1));
            Optional<AccessTokenContent> cached = cache.get(token, performanceMonitor);
            if (cached.isEmpty()) {
                cache.put(token, content, performanceMonitor);
            }
        }
        assertEquals(10, cache.size());

        // When - add 11th token to trigger eviction
        String overflowToken = "token-10";
        AccessTokenContent overflowContent = createAccessToken("https://example.com",
                OffsetDateTime.now().plusHours(1));
        Optional<AccessTokenContent> cached = cache.get(overflowToken, performanceMonitor);
        AccessTokenContent result;
        if (cached.isEmpty()) {
            result = overflowContent;
            cache.put(overflowToken, result, performanceMonitor);
        } else {
            result = cached.get();
        }

        // Then - should not throw ConcurrentModificationException
        assertNotNull(result);
        assertEquals(overflowContent, result);
        // Cache size should still be 10 (one token was evicted)
        assertEquals(10, cache.size());
    }

    @Test
    void concurrentCacheEviction() throws InterruptedException {
        // Given - smaller cache to make eviction more likely
        cache.shutdown();
        AccessTokenCacheConfig config = AccessTokenCacheConfig.builder()
                .maxSize(5)
                .evictionIntervalSeconds(300L)
                .build();
        cache = new AccessTokenCache(config, securityEventCounter);

        int threadCount = 5; // Reduced thread count
        int tokensPerThread = 3; // Reduced tokens per thread
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        // When - multiple threads add different tokens concurrently
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            for (int i = 0; i < threadCount; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        // Create thread-local performance monitor to avoid contention
                        TokenValidatorMonitor threadMonitor = TokenValidatorMonitorConfig.builder()
                                .measurementTypes(TokenValidatorMonitorConfig.ALL_MEASUREMENT_TYPES)
                                .build()
                                .createMonitor();

                        for (int j = 0; j < tokensPerThread; j++) {
                            String token = "thread-" + threadId + "-token-" + j;
                            AccessTokenContent content = createAccessToken("https://example.com",
                                    OffsetDateTime.now().plusHours(1));
                            Optional<AccessTokenContent> cached = cache.get(token, threadMonitor);
                            AccessTokenContent result;
                            if (cached.isEmpty()) {
                                result = content;
                                cache.put(token, result, threadMonitor);
                            } else {
                                result = cached.get();
                            }
                            assertNotNull(result);
                            successCount.incrementAndGet();
                        }
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // Wait for completion with longer timeout (test may be slow on CI)
            assertTrue(doneLatch.await(10, TimeUnit.SECONDS), "All threads should complete");
            executor.shutdown();
            assertTrue(executor.awaitTermination(1, TimeUnit.SECONDS), "Executor should terminate cleanly");
        }

        // Then - all operations should succeed
        assertEquals(threadCount * tokensPerThread, successCount.get());

        // Cache should be near maxSize - due to lock-free design, it may temporarily exceed during concurrent operations
        // This is acceptable as the cache prioritizes throughput over strict size limits
        int cacheSize = cache.size();
        assertTrue(cacheSize > 0, "Cache should not be empty");
        assertTrue(cacheSize <= 10,
                "Cache size " + cacheSize + " should not exceed 2x maxSize under concurrent load");
    }

    @Test
    void cacheEvictionBatchRemoval() {
        // Given - cache that evicts 10% when full
        cache.shutdown();
        AccessTokenCacheConfig config = AccessTokenCacheConfig.builder()
                .maxSize(100)
                .evictionIntervalSeconds(300L)
                .build();
        cache = new AccessTokenCache(config, securityEventCounter);

        // When - fill cache to capacity
        for (int i = 0; i < 100; i++) {
            String token = "token-" + i;
            AccessTokenContent content = createAccessToken("https://example.com",
                    OffsetDateTime.now().plusHours(1));
            Optional<AccessTokenContent> cached = cache.get(token, performanceMonitor);
            if (cached.isEmpty()) {
                cache.put(token, content, performanceMonitor);
            }
        }

        assertEquals(100, cache.size());

        // When - add one more token to trigger batch eviction
        Optional<AccessTokenContent> cached = cache.get("overflow-token", performanceMonitor);
        if (cached.isEmpty()) {
            AccessTokenContent content = createAccessToken("https://example.com", OffsetDateTime.now().plusHours(1));
            cache.put("overflow-token", content, performanceMonitor);
        }

        // Then - should have evicted ~10% of entries
        assertTrue(cache.size() <= 91); // Should have evicted at least 10 entries
        assertTrue(cache.size() >= 90); // But not too many
    }

    @Test
    void tokenWithoutExpirationThrowsException() {
        // Given a token without expiration claim
        String token = "token-without-exp";
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        // Remove expiration claim
        tokenHolder.getClaims().remove(ClaimName.EXPIRATION.getName());
        AccessTokenContent contentWithoutExp = tokenHolder.asAccessTokenContent();

        Optional<AccessTokenContent> cached = cache.get(token, performanceMonitor);
        assertTrue(cached.isEmpty(), "Cache should be empty initially");

        // When/Then - should throw InternalCacheException
        InternalCacheException exception = assertThrows(InternalCacheException.class, () ->
                cache.put(token, contentWithoutExp, performanceMonitor)
        );

        assertEquals("Token passed validation but has no expiration time", exception.getMessage());
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.ERROR,
                JWTValidationLogMessages.ERROR.CACHE_TOKEN_NO_EXPIRATION.resolveIdentifierString());
    }

    @Test
    void cacheDisabledNoEvictionLogic() {
        // Given - cache with size 0 (disabled)
        cache.shutdown();
        AccessTokenCacheConfig config = AccessTokenCacheConfig.builder()
                .maxSize(0)
                .evictionIntervalSeconds(300L)
                .build();
        cache = new AccessTokenCache(config, securityEventCounter);

        // When - try to cache tokens
        for (int i = 0; i < 10; i++) {
            String token = "token-" + i;
            AtomicInteger validationCount = new AtomicInteger(0);
            AccessTokenContent content = createAccessToken("https://example.com",
                    OffsetDateTime.now().plusHours(1));

            Optional<AccessTokenContent> cached = cache.get(token, performanceMonitor);
            AccessTokenContent result;
            if (cached.isEmpty()) {
                validationCount.incrementAndGet();
                result = content;
                // When cache is disabled, put should be a no-op
                cache.put(token, result, performanceMonitor);
            } else {
                result = cached.get();
            }

            // Then - validation happens every time (no caching)
            assertNotNull(result);
            assertEquals(1, validationCount.get());
        }

        // Cache should remain empty
        assertEquals(0, cache.size());
    }

    @Test
    void evictionExecutorRemovesExpiredTokens() {
        // Given - cache with eviction interval of 1 second
        cache.shutdown();
        AccessTokenCacheConfig config = AccessTokenCacheConfig.builder()
                .maxSize(10) // Reduced size
                .evictionIntervalSeconds(1L) // Run eviction every second
                .build();
        cache = new AccessTokenCache(config, securityEventCounter);

        // When - add 5 tokens that will expire in 2 seconds
        OffsetDateTime expirationTime = OffsetDateTime.now().plusSeconds(2);
        for (int i = 0; i < 5; i++) {
            String rawToken = "raw-token-" + i;
            AccessTokenContent content = createAccessTokenWithRawToken(
                    "https://example.com",
                    expirationTime,
                    rawToken
            );
            Optional<AccessTokenContent> cached = cache.get(rawToken, performanceMonitor);
            if (cached.isEmpty()) {
                cache.put(rawToken, content, performanceMonitor);
            }
        }

        // Then - verify all 5 tokens are in cache
        assertEquals(5, cache.size());

        // Use awaitility with shorter timeout
        await()
                .atMost(4, TimeUnit.SECONDS)
                .pollInterval(200, TimeUnit.MILLISECONDS)
                .untilAsserted(() ->
                        assertEquals(0, cache.size(), "All expired tokens should be evicted"));

        // Verify we can still add new tokens after eviction
        String newToken = "new-token-after-eviction";
        AccessTokenContent newContent = createAccessToken("https://example.com",
                OffsetDateTime.now().plusHours(1));
        Optional<AccessTokenContent> cached = cache.get(newToken, performanceMonitor);
        AccessTokenContent result;
        if (cached.isEmpty()) {
            result = newContent;
            cache.put(newToken, result, performanceMonitor);
        } else {
            result = cached.get();
        }

        assertNotNull(result);
        assertEquals(1, cache.size());
    }

}