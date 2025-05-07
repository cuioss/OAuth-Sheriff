/*
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cuioss.jwt.validation.jwks.http;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.jwt.validation.JWTValidationLogMessages.DEBUG;
import de.cuioss.jwt.validation.JWTValidationLogMessages.WARN;
import de.cuioss.jwt.validation.jwks.JwksLoader;
import de.cuioss.jwt.validation.jwks.key.JWKSKeyLoader;
import de.cuioss.jwt.validation.jwks.key.KeyInfo;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.string.MoreStrings;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

/**
 * Implementation of {@link JwksLoader} that loads JWKS from an HTTP endpoint.
 * Uses Caffeine cache for caching keys.
 * <p>
 * This implementation includes several performance and reliability enhancements:
 * <ul>
 *   <li>HTTP 304 "Not Modified" handling: Uses the ETag header to avoid unnecessary downloads</li>
 *   <li>Content-based caching: Only creates new key loaders when content actually changes</li>
 *   <li>Fallback mechanism: Uses the last valid result if a new request fails</li>
 *   <li>Multi-issuer support: Efficiently caches keys for multiple issuers</li>
 *   <li>Adaptive caching: Adjusts cache behavior based on usage patterns</li>
 *   <li>Background refresh: Preemptively refreshes keys before they expire</li>
 *   <li>Cache size limits: Prevents memory issues in multi-issuer environments</li>
 * </ul>
 * <p>
 * For more details on the security aspects, see the
 * <a href="https://github.com/cuioss/cui-jwt-validation/tree/main/doc/specification/security.adoc">Security Specification</a>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@ToString(exclude = {"httpClient", "cacheManager", "backgroundRefreshManager", "securityEventCounter"})
@EqualsAndHashCode(exclude = {"httpClient", "cacheManager", "backgroundRefreshManager", "securityEventCounter"})
public class HttpJwksLoader implements JwksLoader, AutoCloseable {

    private static final CuiLogger LOGGER = new CuiLogger(HttpJwksLoader.class);

    @Getter
    private final HttpJwksLoaderConfig config;
    private final JwksHttpClient httpClient;
    private final JwksCacheManager cacheManager;
    private final BackgroundRefreshManager backgroundRefreshManager;
    @NonNull
    private final SecurityEventCounter securityEventCounter;


    /**
     * Creates a new HttpJwksLoader with the specified configuration and security event counter.
     *
     * @param config               the configuration
     * @param securityEventCounter the counter for security events
     * @throws IllegalArgumentException if the configuration is null
     */
    public HttpJwksLoader(@NonNull HttpJwksLoaderConfig config, @NonNull SecurityEventCounter securityEventCounter) {
        this.config = config;
        this.httpClient = JwksHttpClient.create(config);
        this.cacheManager = new JwksCacheManager(config, this::loadJwksKeyLoader);
        this.backgroundRefreshManager = new BackgroundRefreshManager(config, cacheManager);
        this.securityEventCounter = securityEventCounter;

        // Initial JWKS content fetch to populate cache
        cacheManager.resolve();

        LOGGER.debug(DEBUG.INITIALIZED_JWKS_LOADER.format(
                config.getJwksUri().toString(), config.getRefreshIntervalSeconds()));
    }

    /**
     * Loads a JWKSKeyLoader for the given cache key.
     * The cache calls this method when a value is not found or needs to be refreshed.
     *
     * @param cacheKey the cache key
     * @return a JWKSKeyLoader instance
     */
    private JWKSKeyLoader loadJwksKeyLoader(String cacheKey) {
        LOGGER.debug("Loading JWKS for key: %s", cacheKey);

        // Get the current ETag from the cache manager
        String etag = cacheManager.getCurrentEtag();

        try {
            // Fetch JWKS content from the HTTP endpoint
            JwksHttpClient.JwksHttpResponse response = httpClient.fetchJwksContent(etag);

            // Handle 304 Not Modified response
            if (response.isNotModified()) {
                return cacheManager.handleNotModified();
            }

            // Update the cache with the new content
            JwksCacheManager.KeyRotationResult result = cacheManager.updateCache(
                    response.getContent(), response.getEtag().orElse(null));

            // Check if key rotation was detected
            if (result.keyRotationDetected()) {
                LOGGER.warn(WARN.KEY_ROTATION_DETECTED::format);
                securityEventCounter.increment(SecurityEventCounter.EventType.KEY_ROTATION_DETECTED);
            }

            // Log successful loading and parsing of JWKS
            LOGGER.info(JWTValidationLogMessages.INFO.JWKS_LOADED.format(
                    config.getJwksUri().toString(),
                    result.keyLoader().keySet().size()));

            return result.keyLoader();
        } catch (Exception e) {
            LOGGER.warn(e, WARN.JWKS_FETCH_FAILED.format(e.getMessage()));
            securityEventCounter.increment(SecurityEventCounter.EventType.JWKS_FETCH_FAILED);
            // Return the last valid result if available, or an empty JWKS
            return cacheManager.getLastValidResult().orElse(new JWKSKeyLoader("{}"));
        }
    }

    /**
     * Gets a key by its ID.
     *
     * @param kid the key ID
     * @return an Optional containing the key info, or empty if not found
     */
    @Override
    public Optional<KeyInfo> getKeyInfo(String kid) {
        if (MoreStrings.isEmpty(kid)) {
            LOGGER.debug(DEBUG.KEY_ID_EMPTY::format);
            return Optional.empty();
        }

        JWKSKeyLoader keyLoader = cacheManager.resolve();
        Optional<KeyInfo> keyInfo = keyLoader.getKeyInfo(kid);

        if (keyInfo.isEmpty() && config.getRefreshIntervalSeconds() > 0) {
            // Key not found, try refreshing the cache
            LOGGER.debug(DEBUG.KEY_NOT_FOUND_REFRESHING.format(kid));
            try {
                cacheManager.refresh();
                keyLoader = cacheManager.resolve();
                keyInfo = keyLoader.getKeyInfo(kid);
            } catch (Exception e) {
                // Handle connection errors gracefully
                LOGGER.warn(e, WARN.JWKS_FETCH_FAILED.format(e.getMessage()));
                securityEventCounter.increment(SecurityEventCounter.EventType.JWKS_FETCH_FAILED);
            }
        }

        if (keyInfo.isEmpty()) {
            LOGGER.warn(WARN.KEY_NOT_FOUND.format(kid));
            securityEventCounter.increment(SecurityEventCounter.EventType.KEY_NOT_FOUND);
        }

        return keyInfo;
    }

    /**
     * Gets the first available key.
     *
     * @return an Optional containing the first key info if available, empty otherwise
     */
    @Override
    public Optional<KeyInfo> getFirstKeyInfo() {
        JWKSKeyLoader keyLoader = cacheManager.resolve();
        if (keyLoader.isNotEmpty()) {
            return keyLoader.getFirstKeyInfo();
        }
        return Optional.empty();
    }

    /**
     * Gets all available keys with their algorithms.
     *
     * @return a List containing all available key infos
     */
    @Override
    public List<KeyInfo> getAllKeyInfos() {
        return cacheManager.resolve().getAllKeyInfos();
    }

    /**
     * Gets the set of all available key IDs.
     *
     * @return a Set containing all available key IDs
     */
    @Override
    public Set<String> keySet() {
        return cacheManager.resolve().keySet();
    }

    /**
     * Closes resources used by this loader.
     * This method should be called when the loader is no longer needed.
     */
    @Override
    public void close() {
        backgroundRefreshManager.close();
    }
}
