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
package de.cuioss.sheriff.oauth.core.jwks.http;

import de.cuioss.http.client.adapter.CacheKeyHeaderFilter;
import de.cuioss.http.client.adapter.ETagAwareHttpAdapter;
import de.cuioss.http.client.adapter.HttpAdapter;
import de.cuioss.http.client.adapter.ResilientHttpAdapter;
import de.cuioss.http.client.handler.HttpHandler;
import de.cuioss.http.client.result.HttpResult;
import de.cuioss.sheriff.oauth.core.json.Jwks;
import de.cuioss.sheriff.oauth.core.jwks.JwksLoader;
import de.cuioss.sheriff.oauth.core.jwks.JwksType;
import de.cuioss.sheriff.oauth.core.jwks.key.JWKSKeyLoader;
import de.cuioss.sheriff.oauth.core.jwks.key.KeyInfo;
import de.cuioss.sheriff.oauth.core.security.SecurityEventCounter;
import de.cuioss.sheriff.oauth.core.util.LoaderStatus;
import de.cuioss.sheriff.oauth.core.util.LoadingStatusProvider;
import de.cuioss.sheriff.oauth.core.well_known.HttpWellKnownResolver;
import de.cuioss.tools.logging.CuiLogger;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static de.cuioss.sheriff.oauth.core.JWTValidationLogMessages.ERROR;
import static de.cuioss.sheriff.oauth.core.JWTValidationLogMessages.INFO;
import static de.cuioss.sheriff.oauth.core.JWTValidationLogMessages.WARN;

/**
 * JWKS loader that loads from HTTP endpoint with caching and background refresh support.
 * Supports both direct HTTP endpoints and well-known discovery.
 * Uses HttpAdapter composition (ETagAwareHttpAdapter + ResilientHttpAdapter) for bandwidth
 * optimization via ETag caching and resilient retry behavior with optional scheduled background refresh.
 * Background refresh is automatically started after the first successful key load.
 * <p>
 * This implementation follows a clean architecture with:
 * <ul>
 *   <li>Simple constructor with no I/O operations</li>
 *   <li>Async initialization via CompletableFuture</li>
 *   <li>Lock-free status checks using AtomicReference</li>
 *   <li>Key rotation grace period support for Issue #110</li>
 *   <li>Proper separation of concerns</li>
 *   <li>URI-only cache keys for public OAuth endpoints (no header pollution)</li>
 * </ul>
 * <p>
 * Implements Requirement CUI-JWT-4.5: Key Rotation Grace Period
 *
 * @author Oliver Wolff
 * @see HttpJwksLoaderConfig
 * @see <a href="https://github.com/cuioss/OAuth-Sheriff/issues/110">Issue #110: Key rotation grace period</a>
 * @since 1.0
 */
public class HttpJwksLoader implements JwksLoader, LoadingStatusProvider, AutoCloseable {

    private static final CuiLogger LOGGER = new CuiLogger(HttpJwksLoader.class);
    private static final String ISSUER_NOT_CONFIGURED = "not-configured";
    private static final String ISSUER_MUST_BE_RESOLVED = "Issuer identifier must be resolved at this point";

    private final HttpJwksLoaderConfig config;
    private final AtomicReference<LoaderStatus> status = new AtomicReference<>(LoaderStatus.UNDEFINED);
    private final AtomicReference<JWKSKeyLoader> currentKeys = new AtomicReference<>();
    private final ConcurrentLinkedDeque<RetiredKeySet> retiredKeys = new ConcurrentLinkedDeque<>();
    private final AtomicReference<HttpAdapter<Jwks>> httpAdapter = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> refreshTask = new AtomicReference<>();
    private SecurityEventCounter securityEventCounter;
    private final AtomicReference<String> resolvedIssuerIdentifier = new AtomicReference<>();
    private final AtomicReference<Jwks> currentJwksContent = new AtomicReference<>();

    /**
     * Constructor using HttpJwksLoaderConfig.
     * Simple constructor with no I/O operations - all loading happens asynchronously in initJWKSLoader.
     *
     * @param config the configuration for this loader
     */
    public HttpJwksLoader(HttpJwksLoaderConfig config) {
        this.config = config;
    }

    @Override
    @SuppressWarnings("java:S3776") // Cognitive complexity - initialization logic requires these checks
    public CompletableFuture<LoaderStatus> initJWKSLoader(SecurityEventCounter counter) {
        this.securityEventCounter = counter;

        // Execute initialization asynchronously
        return CompletableFuture.supplyAsync(() -> {
            status.set(LoaderStatus.LOADING);

            // Resolve the adapter (may involve well-known discovery)
            Optional<HttpAdapter<Jwks>> adapterOpt = resolveJWKSAdapter();
            if (adapterOpt.isEmpty()) {
                status.set(LoaderStatus.ERROR);
                String errorDetail = config.getWellKnownConfig() != null
                        ? "Well-known discovery failed"
                        : "No HTTP handler configured";

                // Log appropriate message based on failure type
                if (config.getWellKnownConfig() != null) {
                    LOGGER.warn(WARN.JWKS_URI_RESOLUTION_FAILED);
                }
                LOGGER.error(ERROR.JWKS_INITIALIZATION_FAILED, errorDetail, getIssuerIdentifier().orElse(ISSUER_NOT_CONFIGURED));
                return LoaderStatus.ERROR;
            }

            HttpAdapter<Jwks> adapter = adapterOpt.get();
            httpAdapter.set(adapter);

            // Load JWKS via HttpAdapter (async with join for initialization)
            HttpResult<Jwks> result = adapter.get().join();

            // Start background refresh if configured (regardless of initial load status to enable retries)
            boolean backgroundRefreshEnabled = config.isBackgroundRefreshEnabled();
            if (backgroundRefreshEnabled) {
                startBackgroundRefresh();
            }

            if (result.isSuccess()) {
                result.getContent().ifPresent(this::updateKeys);

                // Log successful HTTP load
                LOGGER.info(INFO.JWKS_LOADED, getIssuerIdentifier().orElseThrow(() -> new IllegalStateException(ISSUER_MUST_BE_RESOLVED)));

                status.set(LoaderStatus.OK);
                return LoaderStatus.OK;
            }

            // Log appropriate warning if no cached content
            result.getErrorMessage().ifPresent(msg -> {
                if (msg.contains("no cached content")) {
                    LOGGER.warn(WARN.JWKS_LOAD_FAILED_NO_CACHE);
                }
            });

            LOGGER.error(ERROR.JWKS_LOAD_FAILED, result.getErrorMessage().orElse("Unknown error"), getIssuerIdentifier().orElseThrow(() -> new IllegalStateException(ISSUER_MUST_BE_RESOLVED)));

            // If background refresh is enabled, keep status as UNDEFINED to allow retries
            // Otherwise set to ERROR for permanent failure
            if (backgroundRefreshEnabled) {
                status.set(LoaderStatus.UNDEFINED);
                return LoaderStatus.UNDEFINED;
            } else {
                status.set(LoaderStatus.ERROR);
                return LoaderStatus.ERROR;
            }
        });
    }

    /**
     * Resolves the JWKS adapter based on configuration.
     * For well-known: performs discovery to get JWKS URL and validates issuer
     * For direct: uses the configured HTTP handler
     *
     * @return Optional containing the adapter, or empty if resolution failed
     */
    @SuppressWarnings("java:S3776") // Cognitive complexity - issuer resolution requires these checks
    private Optional<HttpAdapter<Jwks>> resolveJWKSAdapter() {
        HttpHandler handler;

        if (config.getWellKnownConfig() != null) {
            // Well-known discovery - the resolver itself uses HttpAdapter for retry!
            HttpWellKnownResolver resolver = config.getWellKnownConfig().createResolver();

            // This call may block but we're in async context
            Optional<String> jwksUri = resolver.getJwksUri();
            if (jwksUri.isEmpty()) {
                return Optional.empty();
            }

            // Resolve issuer from well-known document
            Optional<String> discoveredIssuer = resolver.getIssuer();
            String configuredIssuer = config.getIssuerIdentifier();

            if (discoveredIssuer.isPresent()) {
                if (configuredIssuer != null) {
                    // Configured issuer takes precedence, but validate against discovered
                    if (!configuredIssuer.equals(discoveredIssuer.get())) {
                        LOGGER.warn(WARN.ISSUER_MISMATCH, configuredIssuer, discoveredIssuer.get());
                        securityEventCounter.increment(SecurityEventCounter.EventType.ISSUER_MISMATCH);
                    }
                    resolvedIssuerIdentifier.set(configuredIssuer);
                } else {
                    // No configured issuer - use discovered issuer from well-known
                    resolvedIssuerIdentifier.set(discoveredIssuer.get());
                }
            } else {
                // No issuer in well-known document
                if (configuredIssuer != null) {
                    // Use configured issuer if available
                    resolvedIssuerIdentifier.set(configuredIssuer);
                } else {
                    // No issuer available at all - fail
                    LOGGER.error(ERROR.JWKS_INITIALIZATION_FAILED, "No issuer identifier found", "well-known");
                    return Optional.empty();
                }
            }

            // Use overloaded method to create handler for discovered JWKS URL
            handler = config.getHttpHandler(jwksUri.get());
        } else {
            // Direct HTTP configuration - use existing handler from config
            handler = config.getHttpHandler();
            resolvedIssuerIdentifier.set(config.getIssuerIdentifier());
        }

        // Create base adapter with ETag caching
        HttpAdapter<Jwks> baseAdapter = ETagAwareHttpAdapter.<Jwks>builder()
                .httpHandler(handler)
                .responseConverter(new JwksHttpContentConverter())
                .cacheKeyHeaderFilter(CacheKeyHeaderFilter.NONE)  // URI only - public OAuth endpoints
                .build();

        // Wrap with retry behavior
        return Optional.of(ResilientHttpAdapter.wrap(baseAdapter, config.getRetryConfig()));
    }

    @Override
    public Optional<KeyInfo> getKeyInfo(String kid) {
        // Check current keys
        JWKSKeyLoader current = currentKeys.get();
        if (current != null) {
            Optional<KeyInfo> key = current.getKeyInfo(kid);
            if (key.isPresent()) return key;
        }

        // Check retired keys (grace period for Issue #110)
        // Skip checking retired keys if grace period is zero
        if (!config.getKeyRotationGracePeriod().isZero()) {
            Instant cutoff = Instant.now().minus(config.getKeyRotationGracePeriod());
            for (RetiredKeySet retired : retiredKeys) {
                if (retired.retiredAt.isAfter(cutoff)) {
                    Optional<KeyInfo> key = retired.loader.getKeyInfo(kid);
                    if (key.isPresent()) return key;
                }
            }
        }

        return Optional.empty();
    }

    @Override
    public LoaderStatus getLoaderStatus() {
        return status.get(); // Pure atomic read
    }

    @Override
    public JwksType getJwksType() {
        return config.getJwksType(); // Delegate to config
    }

    @Override
    public Optional<String> getIssuerIdentifier() {
        // Return resolved issuer if available, otherwise fall back to config
        String resolved = resolvedIssuerIdentifier.get();
        if (resolved != null) {
            return Optional.of(resolved);
        }
        return Optional.ofNullable(config.getIssuerIdentifier());
    }

    private void updateKeys(Jwks newJwks) {
        // Check if content has actually changed (Issue #110)
        Jwks currentJwks = currentJwksContent.get();
        if (currentJwks != null && currentJwks.equals(newJwks)) {
            LOGGER.debug("JWKS content unchanged, skipping key rotation");
            return; // Content unchanged, no need to update
        }

        // Content has changed, update the reference
        currentJwksContent.set(newJwks);

        JWKSKeyLoader newLoader = JWKSKeyLoader.builder()
                .jwksContent(newJwks)
                .jwksType(config.getJwksType())
                .build();
        newLoader.initJWKSLoader(securityEventCounter);

        // Use a single timestamp to avoid timing issues (Issue #110)
        Instant now = Instant.now();

        // Retire old keys with grace period
        JWKSKeyLoader oldLoader = currentKeys.getAndSet(newLoader);
        if (oldLoader != null) {
            // Special handling for zero grace period - don't retain retired keys
            if (config.getKeyRotationGracePeriod().isZero()) {
                // With zero grace period, immediately discard old keys
                // Don't add to retiredKeys at all
            } else {
                retiredKeys.addFirst(new RetiredKeySet(oldLoader, now));

                // Clean up expired retired keys
                Instant cutoff = now.minus(config.getKeyRotationGracePeriod());
                retiredKeys.removeIf(retired -> retired.retiredAt.isBefore(cutoff));

                // Keep max N retired sets
                while (retiredKeys.size() > config.getMaxRetiredKeySets()) {
                    retiredKeys.removeLast();
                }
            }
        }

        // Log keys update
        LOGGER.info(INFO.JWKS_KEYS_UPDATED, status.get());
    }

    private void startBackgroundRefresh() {
        refreshTask.set(config.getScheduledExecutorService().scheduleAtFixedRate(() -> {
                    try {
                        HttpAdapter<Jwks> adapter = httpAdapter.get();
                        if (adapter == null) {
                            LOGGER.warn(WARN.BACKGROUND_REFRESH_NO_HANDLER);
                            return;
                        }

                        // Async load with join (acceptable in background thread)
                        HttpResult<Jwks> result = adapter.get().join();

                        // CRITICAL: 304 is a SUCCESS result with cached content
                        if (result.isSuccess()) {
                            int httpStatus = result.getHttpStatus().orElse(0);

                            if (httpStatus == 200) {
                                // New content received, update keys
                                result.getContent().ifPresent(this::updateKeys);
                                LOGGER.debug("Background refresh updated keys");
                            } else if (httpStatus == 304) {
                                // Not modified - cached content still valid (don't update)
                                LOGGER.debug("Background refresh: keys unchanged (304)");
                            }
                        } else {
                            // Handle error (network, server, etc.)
                            String statusDesc = result.getErrorMessage()
                                    .orElseGet(() -> "HTTP status: " + result.getHttpStatus().map(String::valueOf).orElse("N/A"));
                            LOGGER.warn(WARN.BACKGROUND_REFRESH_FAILED, statusDesc);
                        }
                    } catch (IllegalArgumentException e) {
                        // JSON parsing or validation errors
                        LOGGER.warn(WARN.BACKGROUND_REFRESH_PARSE_ERROR, e.getMessage(), getIssuerIdentifier().orElseThrow(() -> new IllegalStateException(ISSUER_MUST_BE_RESOLVED)));
                    } catch (IllegalStateException e) {
                        // State errors (e.g., from orElseThrow when issuer not resolved)
                        LOGGER.warn(WARN.BACKGROUND_REFRESH_FAILED, e.getMessage());
                    }
                },
                config.getRefreshIntervalSeconds(),
                config.getRefreshIntervalSeconds(),
                TimeUnit.SECONDS));

        LOGGER.info(INFO.JWKS_BACKGROUND_REFRESH_STARTED, config.getRefreshIntervalSeconds());
    }

    @Override
    public void close() {
        ScheduledFuture<?> task = refreshTask.get();
        if (task != null) {
            task.cancel(false);
        }
        currentKeys.set(null);
        retiredKeys.clear();
        httpAdapter.set(null);
        currentJwksContent.set(null);
        status.set(LoaderStatus.UNDEFINED);
    }

    /**
     * Checks if background refresh is enabled and running.
     * Package-private for testing purposes only.
     *
     * @return true if background refresh is active, false otherwise
     */
    boolean isBackgroundRefreshActive() {
        ScheduledFuture<?> task = refreshTask.get();
        return task != null && !task.isCancelled() && !task.isDone();
    }


    /**
     * Private record to hold retired key sets with their retirement timestamp.
     */
    private record RetiredKeySet(JWKSKeyLoader loader, Instant retiredAt) {
    }
}