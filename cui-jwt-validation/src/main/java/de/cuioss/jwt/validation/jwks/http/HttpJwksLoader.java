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
package de.cuioss.jwt.validation.jwks.http;

import de.cuioss.jwt.validation.jwks.JwksLoader;
import de.cuioss.jwt.validation.jwks.JwksType;
import de.cuioss.jwt.validation.jwks.LoaderStatus;
import de.cuioss.jwt.validation.jwks.key.JWKSKeyLoader;
import de.cuioss.jwt.validation.jwks.key.KeyInfo;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.util.ETagAwareHttpHandler;
import de.cuioss.jwt.validation.well_known.WellKnownResult;
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.net.http.HttpHandler;
import lombok.NonNull;

import java.util.Optional;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static de.cuioss.jwt.validation.JWTValidationLogMessages.ERROR;
import static de.cuioss.jwt.validation.JWTValidationLogMessages.INFO;
import static de.cuioss.jwt.validation.JWTValidationLogMessages.WARN;

/**
 * JWKS loader that loads from HTTP endpoint with caching and background refresh support.
 * Supports both direct HTTP endpoints and well-known discovery.
 * Uses ETagAwareHttpHandler for stateful HTTP caching with optional scheduled background refresh.
 * Background refresh is automatically started after the first successful key load.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public class HttpJwksLoader implements JwksLoader {

    private static final CuiLogger LOGGER = new CuiLogger(HttpJwksLoader.class);

    private SecurityEventCounter securityEventCounter;
    private final HttpJwksLoaderConfig config;
    private final AtomicReference<JWKSKeyLoader> keyLoader = new AtomicReference<>();
    private final AtomicReference<ETagAwareHttpHandler> httpCache = new AtomicReference<>();
    private volatile LoaderStatus status = LoaderStatus.UNDEFINED;
    private final AtomicReference<ScheduledFuture<?>> refreshTask = new AtomicReference<>();
    private final AtomicBoolean schedulerStarted = new AtomicBoolean(false);
    private final AtomicBoolean initialized = new AtomicBoolean(false);

    /**
     * Constructor using HttpJwksLoaderConfig.
     * Supports both direct HTTP handlers and WellKnownResolver configurations.
     * The SecurityEventCounter must be set via initJWKSLoader() before use.
     */
    public HttpJwksLoader(@NonNull HttpJwksLoaderConfig config) {
        this.config = config;
    }

    @Override
    public Optional<KeyInfo> getKeyInfo(String kid) {
        ensureLoaded();
        JWKSKeyLoader loader = keyLoader.get();
        return loader != null ? loader.getKeyInfo(kid) : Optional.empty();
    }


    @Override
    public JwksType getJwksType() {
        // Distinguish between direct HTTP and well-known discovery based on configuration
        if (config.getWellKnownResolver() != null) {
            return JwksType.WELL_KNOWN;
        } else {
            return JwksType.HTTP;
        }
    }

    @Override
    public LoaderStatus isHealthy() {
        // For cached loader, we consider it healthy if we can load keys
        // This will trigger lazy loading on first health check
        if (keyLoader.get() == null) {
            try {
                ensureLoaded();
            } catch (JwksLoadException e) {
                LOGGER.debug("Health check failed during key loading: %s", e.getMessage());
                return LoaderStatus.ERROR;
            }
        }
        return status;
    }

    @Override
    public Optional<String> getIssuerIdentifier() {
        // Return issuer identifier from well-known resolver if configured
        if (config.getWellKnownResolver() != null) {
            if (config.getWellKnownResolver().isHealthy() == LoaderStatus.OK) {
                WellKnownResult<HttpHandler> issuerResult = config.getWellKnownResolver().getIssuer();
                if (issuerResult.isSuccess() && issuerResult.value() != null) {
                    return Optional.of(issuerResult.value().getUri().toString());
                } else {
                    LOGGER.debug("Failed to retrieve issuer identifier from well-known resolver: %s",
                            issuerResult.isError() ? issuerResult.errorMessage() : "issuer handler is null");
                }
            }
        }
        return Optional.empty();
    }


    /**
     * Shuts down the background refresh scheduler if running.
     * Package-private for testing purposes only.
     */
    void shutdown() {
        ScheduledFuture<?> task = refreshTask.get();
        if (task != null && !task.isCancelled()) {
            task.cancel(false);
            LOGGER.debug("Background refresh task cancelled");
        }
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

    private void ensureLoaded() {
        if (!initialized.get()) {
            throw new IllegalStateException("HttpJwksLoader not initialized. Call initJWKSLoader() first.");
        }
        if (keyLoader.get() == null) {
            loadKeysIfNeeded();
        }
    }

    private void loadKeysIfNeeded() {
        // Double-checked locking pattern with AtomicReference
        if (keyLoader.get() == null) {
            synchronized (this) {
                if (keyLoader.get() == null) {
                    loadKeys();
                }
            }
        }
    }

    private void loadKeys() {
        try {
            // Ensure we have a healthy ETagAwareHttpHandler
            Optional<ETagAwareHttpHandler> cacheOpt = ensureHttpCache();
            if (cacheOpt.isEmpty()) {
                this.status = LoaderStatus.ERROR;
                throw new JwksLoadException("Unable to establish healthy HTTP connection for JWKS loading");
            }

            ETagAwareHttpHandler cache = cacheOpt.get();

            ETagAwareHttpHandler.LoadResult result = cache.load();

            // Handle error states appropriately
            if (result.loadState() == ETagAwareHttpHandler.LoadState.ERROR_NO_CACHE) {
                this.status = LoaderStatus.ERROR;
                throw new JwksLoadException("Failed to load JWKS and no cached content available");
            }

            // Only update key loader if data has changed and we have content
            if (result.content() != null && (result.loadState().isDataChanged() || keyLoader.get() == null)) {
                updateKeyLoader(result);
                LOGGER.info(INFO.JWKS_KEYS_UPDATED.format(result.loadState()));

                // Start background refresh after first successful load
                startBackgroundRefreshIfNeeded();
            }

            // Log appropriate message based on load state
            switch (result.loadState()) {
                case LOADED_FROM_SERVER:
                    LOGGER.info(INFO.JWKS_HTTP_LOADED::format);
                    break;
                case CACHE_ETAG:
                    LOGGER.debug("JWKS content validated via ETag (304 Not Modified)");
                    break;
                case CACHE_CONTENT:
                    LOGGER.debug("Using cached JWKS content");
                    break;
                case ERROR_WITH_CACHE:
                    LOGGER.warn(WARN.JWKS_LOAD_FAILED_CACHED_CONTENT::format);
                    break;
                case ERROR_NO_CACHE:
                    LOGGER.warn(WARN.JWKS_LOAD_FAILED_NO_CACHE::format);
                    break;
            }

        } catch (JwksLoadException e) {
            this.status = LoaderStatus.ERROR;
            LOGGER.error(e, ERROR.JWKS_LOAD_FAILED::format);
            throw e; // Re-throw specific exception
        }
    }

    private void updateKeyLoader(ETagAwareHttpHandler.LoadResult result) {
        JWKSKeyLoader newLoader = JWKSKeyLoader.builder()
                .jwksContent(result.content())
                .jwksType(getJwksType())
                .build();
        // Initialize the JWKSKeyLoader with the SecurityEventCounter
        newLoader.initJWKSLoader(securityEventCounter);
        keyLoader.set(newLoader);
        this.status = LoaderStatus.OK;
    }

    private void startBackgroundRefreshIfNeeded() {
        if (config.getScheduledExecutorService() != null && config.getRefreshIntervalSeconds() > 0 && schedulerStarted.compareAndSet(false, true)) {

            ScheduledExecutorService executor = config.getScheduledExecutorService();
            int intervalSeconds = config.getRefreshIntervalSeconds();

            ScheduledFuture<?> task = executor.scheduleAtFixedRate(
                    this::backgroundRefresh,
                    intervalSeconds,
                    intervalSeconds,
                    TimeUnit.SECONDS
            );
            refreshTask.set(task);

            LOGGER.info(INFO.JWKS_BACKGROUND_REFRESH_STARTED.format(intervalSeconds));
        }
    }

    private void backgroundRefresh() {
        LOGGER.debug("Starting background JWKS refresh");
        Optional<ETagAwareHttpHandler> cacheOpt = Optional.ofNullable(httpCache.get());
        if (cacheOpt.isEmpty()) {
            LOGGER.warn("Background refresh skipped - no HTTP cache available");
            return;
        }

        ETagAwareHttpHandler cache = cacheOpt.get();

        ETagAwareHttpHandler.LoadResult result = cache.load();

        // Handle error states
        if (result.loadState() == ETagAwareHttpHandler.LoadState.ERROR_WITH_CACHE ||
                result.loadState() == ETagAwareHttpHandler.LoadState.ERROR_NO_CACHE) {
            LOGGER.warn("Background JWKS refresh failed: %s", result.loadState());
            return;
        }

        // Only update keys if data has actually changed
        if (result.content() != null && result.loadState().isDataChanged()) {
            updateKeyLoader(result);
            LOGGER.info(INFO.JWKS_BACKGROUND_REFRESH_UPDATED.format(result.loadState()));
        } else {
            LOGGER.debug("Background refresh completed, no changes detected: %s", result.loadState());
        }
    }

    /**
     * Ensures that we have a healthy ETagAwareHttpHandler based on configuration.
     * Creates the handler dynamically based on whether we have a direct HTTP handler
     * or need to resolve via WellKnownResolver.
     *
     * @return Optional containing the ETagAwareHttpHandler if healthy, empty if sources are not healthy
     */
    private Optional<ETagAwareHttpHandler> ensureHttpCache() {
        // Fast path - already have a cache (set by direct constructor or previous initialization)
        ETagAwareHttpHandler cache = httpCache.get();
        if (cache != null) {
            return Optional.of(cache);
        }

        // Slow path - need to create cache based on configuration
        synchronized (this) {
            // Double-check after acquiring lock
            cache = httpCache.get();
            if (cache != null) {
                return Optional.of(cache);
            }

            try {
                switch (getJwksType()) {
                    case HTTP:
                        // Direct HTTP handler configuration
                        LOGGER.debug("Creating ETagAwareHttpHandler from direct HTTP configuration for URI: %s",
                                config.getHttpHandler().getUri());
                        cache = new ETagAwareHttpHandler(config.getHttpHandler());
                        httpCache.set(cache);
                        return Optional.of(cache);

                    case WELL_KNOWN:
                        // Well-known resolver configuration
                        LOGGER.debug("Creating ETagAwareHttpHandler from WellKnownResolver");

                        // Check if well-known resolver is healthy
                        if (config.getWellKnownResolver().isHealthy() != LoaderStatus.OK) {
                            LOGGER.debug("WellKnownResolver is not healthy, cannot create HTTP cache");
                            return Optional.empty();
                        }

                        // Extract JWKS URI from well-known resolver
                        WellKnownResult<HttpHandler> jwksResult = config.getWellKnownResolver().getJwksUri();
                        if (jwksResult.isError()) {
                            LOGGER.warn("Failed to resolve JWKS URI from well-known resolver: %s", jwksResult.errorMessage());
                            return Optional.empty();
                        }

                        HttpHandler jwksHandler = jwksResult.value();
                        if (jwksHandler == null) {
                            LOGGER.warn("WellKnownResolver did not provide JWKS URI");
                            return Optional.empty();
                        }

                        LOGGER.info("Successfully resolved JWKS URI from well-known endpoint: %s", jwksHandler.getUri());
                        cache = new ETagAwareHttpHandler(jwksHandler);
                        httpCache.set(cache);
                        return Optional.of(cache);

                    default:
                        LOGGER.error("Unsupported JwksType for HttpJwksLoader: %s", getJwksType());
                        return Optional.empty();
                }

            } catch (NullPointerException e) {
                LOGGER.error(e, "HttpHandler is null when creating ETagAwareHttpHandler: %s", e.getMessage());
                return Optional.empty();
            }
        }
    }

    @Override
    public void initJWKSLoader(@NonNull SecurityEventCounter securityEventCounter) {
        if (initialized.compareAndSet(false, true)) {
            this.securityEventCounter = securityEventCounter;
            LOGGER.debug("HttpJwksLoader initialized with SecurityEventCounter");
        }
    }
}
