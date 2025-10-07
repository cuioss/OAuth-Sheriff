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

import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.exception.InternalCacheException;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.metrics.MeasurementType;
import de.cuioss.jwt.validation.metrics.MetricsTicker;
import de.cuioss.jwt.validation.metrics.MetricsTickerFactory;
import de.cuioss.jwt.validation.metrics.TokenValidatorMonitor;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.tools.logging.CuiLogger;
import lombok.NonNull;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Thread-safe cache for validated access tokens using optimistic caching strategy.
 * <p>
 * This cache stores successfully validated access tokens to avoid redundant validation
 * of the same tokens. It uses integer hashCode for cache keys to optimize performance
 * while maintaining security through raw token verification on cache hits.
 * <p>
 * Features:
 * <ul>
 *   <li>Integer hashCode of tokens for cache keys</li>
 *   <li>Simple get/put API for explicit caching control</li>
 *   <li>Lock-free concurrent access via ConcurrentHashMap</li>
 *   <li>Configurable maximum cache size with automatic overflow eviction</li>
 *   <li>Automatic expiration checking on retrieval</li>
 *   <li>Background thread for periodic expired token cleanup</li>
 *   <li>Security event tracking for cache hits</li>
 *   <li>No external dependencies (Quarkus compatible)</li>
 * </ul>
 * <p>
 * The cache verifies token content on each hit to handle potential hash collisions.
 * JWTs self-expire via their exp claim, reducing need for complex eviction strategies.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public class AccessTokenCache {

    private static final CuiLogger LOGGER = new CuiLogger(AccessTokenCache.class);

    /**
     * The maximum number of tokens to cache.
     */
    private final int maxSize;

    /**
     * The main cache storage using ConcurrentHashMap for thread safety.
     * Key: hashCode of token string
     * Value: CachedToken wrapper
     */
    private final Map<Integer, CachedToken> cache;

    /**
     * Security event counter for tracking cache hits.
     */
    @NonNull
    private final SecurityEventCounter securityEventCounter;

    /**
     * Background executor for expired token eviction.
     */
    private final ScheduledExecutorService evictionExecutor;

    /**
     * Creates a new AccessTokenCache with the specified configuration.
     *
     * @param config the cache configuration
     * @param securityEventCounter the security event counter for tracking cache hits
     */
    public AccessTokenCache(
            @NonNull AccessTokenCacheConfig config,
            @NonNull SecurityEventCounter securityEventCounter) {

        this.maxSize = config.getMaxSize();
        this.securityEventCounter = securityEventCounter;

        if (this.maxSize > 0) {
            // Only initialize cache structures when caching is enabled
            this.cache = new ConcurrentHashMap<>(this.maxSize);

            // Use provided executor or create default for background expiration cleanup
            this.evictionExecutor = config.getOrCreateScheduledExecutorService();

            if (this.evictionExecutor != null) {
                this.evictionExecutor.scheduleWithFixedDelay(
                        this::evictExpiredTokens,
                        config.getEvictionIntervalSeconds(),
                        config.getEvictionIntervalSeconds(),
                        TimeUnit.SECONDS);
            }

            LOGGER.debug("AccessTokenCache initialized with maxSize=%s, evictionInterval=%ss",
                    this.maxSize, config.getEvictionIntervalSeconds());
        } else {
            // Cache disabled - no cache structures or background threads needed
            this.cache = null;
            this.evictionExecutor = null;
            LOGGER.debug("AccessTokenCache disabled (maxSize=0) - no executor started");
        }
    }

    /**
     * Retrieves a cached access token if present and valid.
     * <p>
     * This method checks if a token exists in cache and is still valid (not expired,
     * raw token matches). If found and valid, returns the cached token and increments
     * cache hit counter. If expired, removes it from cache and throws exception.
     *
     * @param tokenString the raw JWT token string to look up
     * @param performanceMonitor the monitor for recording CACHE_LOOKUP metrics
     * @return Optional containing the cached token if found and valid, empty otherwise
     * @throws TokenValidationException if the cached token is expired
     */
    public Optional<AccessTokenContent> get(
            @NonNull String tokenString,
            @NonNull TokenValidatorMonitor performanceMonitor) {

        // If cache size is 0, caching is disabled
        if (maxSize == 0) {
            return Optional.empty();
        }

        // Create metrics ticker for cache lookup
        MetricsTicker lookupTicker = MetricsTickerFactory.createStartedTicker(MeasurementType.CACHE_LOOKUP, performanceMonitor);

        // Generate cache key from token string
        int cacheKey = tokenString.hashCode();

        // Try to get existing cached value
        CachedToken existing = cache.get(cacheKey);
        lookupTicker.stopAndRecord();

        if (existing != null) {
            OffsetDateTime now = OffsetDateTime.now();
            if (existing.verifyToken(tokenString) && !existing.isExpired(now)) {
                // True cache hit - valid cached token
                securityEventCounter.increment(SecurityEventCounter.EventType.ACCESS_TOKEN_CACHE_HIT);
                return Optional.of(existing.getContent());
            } else if (existing.isExpired(now)) {
                // Token is expired - remove from cache and throw exception
                cache.remove(cacheKey, existing);
                LOGGER.warn(JWTValidationLogMessages.WARN.TOKEN_EXPIRED);
                securityEventCounter.increment(SecurityEventCounter.EventType.TOKEN_EXPIRED);
                throw new TokenValidationException(
                        SecurityEventCounter.EventType.TOKEN_EXPIRED,
                        "Cached token is expired"
                );
            } else {
                // Token verification failed - different token with same hash (unlikely but possible)
                cache.remove(cacheKey, existing);
                LOGGER.debug("Cached token verification failed - hash collision detected");
            }
        }

        // Cache miss
        return Optional.empty();
    }

    /**
     * Stores a validated access token in the cache.
     * <p>
     * Wraps the token in a CachedToken and stores it using putIfAbsent to handle
     * concurrent storage attempts. If multiple threads attempt to store the same
     * token simultaneously, only the first succeeds.
     *
     * @param tokenString the raw JWT token string as cache key
     * @param content the validated access token content to cache
     * @param performanceMonitor the monitor for recording CACHE_STORE metrics
     * @throws InternalCacheException if token has no expiration or cache store fails
     */
    public void put(
            @NonNull String tokenString,
            @NonNull AccessTokenContent content,
            @NonNull TokenValidatorMonitor performanceMonitor) {

        // If cache size is 0, caching is disabled
        if (maxSize == 0) {
            return;
        }

        // Start metrics for cache store operation
        MetricsTicker storeTicker = MetricsTickerFactory.createStartedTicker(MeasurementType.CACHE_STORE, performanceMonitor);

        // Use try-finally to ensure ticker is always stopped, even on unexpected exceptions
        try {
            // Generate cache key from token string
            int cacheKey = tokenString.hashCode();

            // Wrap validated token in CachedToken for storage
            // Note: expirationTime is guaranteed to be present because tokens are validated
            // before caching, and validation requires a valid exp claim
            OffsetDateTime expirationTime = content.getExpirationTime();

            CachedToken newCachedToken = CachedToken.builder()
                    .rawToken(tokenString)
                    .content(content)
                    .expirationTime(expirationTime)
                    .build();

            // putIfAbsent: only store if no value exists (handles concurrent validation races)
            // If another thread already stored, their value wins - we silently discard ours
            CachedToken previous = cache.putIfAbsent(cacheKey, newCachedToken);

            if (previous == null) {
                // Successfully stored - we won the race (or no race occurred)
                LOGGER.debug("Token cached, current size: %s", cache.size());

                // Enforce size limit after successful insertion
                if (cache.size() > maxSize) {
                    enforceSize();
                }
            } else {
                // Another thread won the race and already stored this token
                LOGGER.debug("Token already cached by concurrent thread");
            }
        } finally {
            // Always stop and record metrics, even if exceptions occur
            storeTicker.stopAndRecord();
        }
    }


    /**
     * Enforces cache size limit by evicting entries when cache exceeds maxSize.
     * <p>
     * Evicts 10% of entries (minimum 1) when cache is full to reduce eviction frequency.
     * Uses iteration order for eviction. Since JWTs self-expire, complex eviction
     * strategies are unnecessary - expired tokens are removed by background cleanup.
     */
    private void enforceSize() {
        if (cache != null && cache.size() >= maxSize) {
            // Calculate batch size: evict 10% or at least 1 entry
            int batchSize = Math.max(1, maxSize / 10);
            int evicted = 0;

            // Iterate and remove first N entries (random based on HashMap iteration order)
            var iterator = cache.entrySet().iterator();
            while (iterator.hasNext() && evicted < batchSize) {
                iterator.next();
                iterator.remove();
                evicted++;
            }

            LOGGER.debug("Evicted %s tokens from cache due to size limit (current size: %s)", evicted, cache.size());
        }
    }

    /**
     * Background task to evict expired tokens.
     * <p>
     * Scans cache for expired tokens and removes them in batches.
     * Only called when cache is enabled (maxSize > 0) and executor is configured.
     * <p>
     * Note: ConcurrentHashMap operations are thread-safe and don't throw
     * IllegalStateException or SecurityException under normal circumstances.
     */
    private void evictExpiredTokens() {
        OffsetDateTime now = OffsetDateTime.now();
        List<Integer> expiredKeys = new ArrayList<>();

        // Collect expired keys (thread-safe iteration)
        for (Map.Entry<Integer, CachedToken> entry : cache.entrySet()) {
            if (entry.getValue().isExpired(now)) {
                expiredKeys.add(entry.getKey());
            }
        }

        if (!expiredKeys.isEmpty()) {
            // Batch remove from cache (thread-safe operations)
            expiredKeys.forEach(cache::remove);
            LOGGER.debug("Evicted %s expired tokens from cache", expiredKeys.size());
        }
    }

    /**
     * Shuts down the cache and its background threads.
     * Should be called when the cache is no longer needed.
     */
    public void shutdown() {
        if (evictionExecutor != null) {
            evictionExecutor.shutdown();
            try {
                if (!evictionExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    evictionExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                evictionExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (cache != null) {
            cache.clear();
        }
        LOGGER.debug("AccessTokenCache shut down");
    }

    /**
     * Gets the current cache size.
     * Package-private for testing purposes.
     *
     * @return the number of tokens currently cached
     */
    int size() {
        return cache != null ? cache.size() : 0;
    }


}