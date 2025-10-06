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
package de.cuioss.jwt.validation.jwks.http;

import de.cuioss.http.client.HttpHandlerProvider;
import de.cuioss.http.client.retry.RetryStrategy;
import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.jwt.validation.JWTValidationLogMessages.WARN;
import de.cuioss.jwt.validation.ParserConfig;
import de.cuioss.jwt.validation.jwks.JwksType;
import de.cuioss.jwt.validation.well_known.WellKnownConfig;
import de.cuioss.tools.base.Preconditions;
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.net.http.HttpHandler;
import de.cuioss.tools.net.http.SecureSSLContextProvider;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Configuration parameters for {@link HttpJwksLoader}.
 * <p>
 * This class encapsulates configuration options for the HttpJwksLoader,
 * including JWKS endpoint URL, refresh interval, SSL context, and
 * background refresh parameters. The JWKS endpoint URL can be configured
 * directly or discovered via a {@link WellKnownConfig}.
 * <p>
 * Complex caching parameters (maxCacheSize, adaptiveWindowSize) have been
 * removed for simplification while keeping essential refresh functionality.
 * <p>
 * For more detailed information about the HTTP-based JWKS loading, see the
 * <a href="https://github.com/cuioss/cui-jwt/tree/main/doc/specification/technical-components.adoc#_jwksloader">Technical Components Specification</a>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@ToString
@EqualsAndHashCode
public class HttpJwksLoaderConfig implements HttpHandlerProvider {

    private static final CuiLogger LOGGER = new CuiLogger(HttpJwksLoaderConfig.class);

    /**
     * A Default of 10 minutes (600 seconds).
     */
    private static final int DEFAULT_REFRESH_INTERVAL_IN_SECONDS = 60 * 10;

    /**
     * Default key rotation grace period of 5 minutes (as per Issue #110).
     */
    private static final Duration DEFAULT_KEY_ROTATION_GRACE_PERIOD = Duration.ofMinutes(5);

    /**
     * Default maximum number of retired key sets to keep.
     */
    private static final int DEFAULT_MAX_RETIRED_KEY_SETS = 3;

    /**
     * The interval in seconds at which to refresh the keys.
     * If set to 0, no time-based caching will be used.
     * It defaults to 10 minutes (600 seconds).
     */
    @Getter
    private final int refreshIntervalSeconds;

    /**
     * The HttpHandler used for HTTP requests.
     * <p>
     * This field is guaranteed to be non-null when using direct JWKS URI configuration.
     * It will be null only when using WellKnownConfig for discovery.
     * <p>
     * The non-null contract for HTTP configurations is enforced by the {@link HttpJwksLoaderConfigBuilder#build()}
     * method, which validates that the HttpHandler was successfully created before constructing the config.
     */
    @EqualsAndHashCode.Exclude
    private final HttpHandler httpHandler;

    /**
     * The WellKnownConfig used for well-known endpoint discovery.
     * Will be null if using direct HttpHandler.
     * Contains all configuration needed to load well-known configuration.
     */
    @Getter
    @EqualsAndHashCode.Exclude
    private final WellKnownConfig wellKnownConfig;

    /**
     * The retry strategy for HTTP operations.
     */
    @Getter
    private final RetryStrategy retryStrategy;

    /**
     * The ScheduledExecutorService used for background refresh operations.
     * Can be null if no background refresh is needed.
     */
    @Getter
    @EqualsAndHashCode.Exclude
    private final ScheduledExecutorService scheduledExecutorService;

    /**
     * The issuer identifier for this JWKS configuration.
     * Used for logging and identification purposes.
     */
    @Getter
    private final String issuerIdentifier;

    /**
     * The grace period for keeping retired key sets after rotation.
     */
    @Getter
    private final Duration keyRotationGracePeriod;

    /**
     * Maximum number of retired key sets to keep.
     */
    @Getter
    private final int maxRetiredKeySets;

    @SuppressWarnings("java:S107") // ok for builder
    private HttpJwksLoaderConfig(int refreshIntervalSeconds,
            HttpHandler httpHandler,
            WellKnownConfig wellKnownConfig,
            RetryStrategy retryStrategy,
            ScheduledExecutorService scheduledExecutorService,
            String issuerIdentifier,
            Duration keyRotationGracePeriod,
            int maxRetiredKeySets) {
        this.refreshIntervalSeconds = refreshIntervalSeconds;
        this.httpHandler = httpHandler;
        this.wellKnownConfig = wellKnownConfig;
        this.retryStrategy = retryStrategy;
        this.scheduledExecutorService = scheduledExecutorService;
        this.issuerIdentifier = issuerIdentifier;
        this.keyRotationGracePeriod = keyRotationGracePeriod;
        this.maxRetiredKeySets = maxRetiredKeySets;
    }

    /**
     * Provides the HttpHandler for HTTP operations, implementing HttpHandlerProvider interface.
     * <p>
     * This method handles both configuration modes:
     * <ul>
     *   <li><strong>Direct HTTP mode</strong>: Returns the configured HttpHandler</li>
     *   <li><strong>WellKnown mode</strong>: Returns the HttpHandler from the WellKnownConfig</li>
     * </ul>
     *
     * @return the HttpHandler instance, never null
     * @throws IllegalStateException if no HttpHandler is available in either mode
     */
    @Override
    @NonNull
    public HttpHandler getHttpHandler() {
        if (httpHandler != null) {
            // Direct HTTP mode - return the configured HttpHandler
            return httpHandler;
        } else if (wellKnownConfig != null) {
            // WellKnown mode - get HttpHandler from the WellKnownConfig
            return wellKnownConfig.getHttpHandler();
        } else {
            throw new IllegalStateException("Neither HttpHandler nor WellKnownConfig is configured");
        }
    }


    /**
     * Creates a new HttpHandler for the given URL using configured values.
     * This overloaded method centralizes HttpHandler creation with consistent settings
     * based on the current configuration instance.
     *
     * @param url the URL to create a handler for
     * @return a new HttpHandler instance with configured settings
     */
    @NonNull
    public HttpHandler getHttpHandler(@NonNull String url) {
        // Reuse existing HttpHandler configuration as base
        HttpHandler baseHandler = getHttpHandler();

        // Create a new handler with the same configuration but different URL
        return baseHandler.asBuilder()
                .url(url)
                .build();
    }

    /**
     * Determines if background refresh is enabled based on configuration.
     * Background refresh is enabled when both a ScheduledExecutorService is configured
     * and the refresh interval is greater than 0.
     *
     * @return true if background refresh should be enabled, false otherwise
     */
    public boolean isBackgroundRefreshEnabled() {
        return scheduledExecutorService != null && refreshIntervalSeconds > 0;
    }

    /**
     * Gets the JWKS type based on configuration.
     * Returns WELL_KNOWN if using well-known discovery, HTTP if using direct endpoint.
     *
     * @return the JwksType for this configuration
     */
    @NonNull
    public JwksType getJwksType() {
        return wellKnownConfig != null ? JwksType.WELL_KNOWN : JwksType.HTTP;
    }

    /**
     * Creates a new builder for HttpJwksLoaderConfig.
     * <p>
     * This method provides a convenient way to create a new instance of
     * HttpJwksLoaderConfigBuilder, allowing for fluent configuration of the
     * HttpJwksLoaderConfig parameters.
     *
     * @return a new HttpJwksLoaderConfigBuilder instance
     */
    public static HttpJwksLoaderConfigBuilder builder() {
        return new HttpJwksLoaderConfigBuilder();
    }

    /**
     * Enum to track which endpoint configuration method was used.
     */
    private enum EndpointSource {
        JWKS_URI,
        JWKS_URL,
        WELL_KNOWN_URL,
        WELL_KNOWN_URI
    }

    /**
     * Builder for creating HttpJwksLoaderConfig instances with validation.
     */
    public static class HttpJwksLoaderConfigBuilder {
        private Integer refreshIntervalSeconds = DEFAULT_REFRESH_INTERVAL_IN_SECONDS;
        private final HttpHandler.HttpHandlerBuilder httpHandlerBuilder;
        private RetryStrategy retryStrategy;
        private ScheduledExecutorService scheduledExecutorService;
        private WellKnownConfig wellKnownConfig;
        private String issuerIdentifier;
        private Duration keyRotationGracePeriod = DEFAULT_KEY_ROTATION_GRACE_PERIOD;
        private int maxRetiredKeySets = DEFAULT_MAX_RETIRED_KEY_SETS;

        // Track which endpoint configuration method was used to ensure mutual exclusivity
        private EndpointSource endpointSource = null;

        /**
         * Constructor initializing the HttpHandlerBuilder.
         */
        public HttpJwksLoaderConfigBuilder() {
            this.httpHandlerBuilder = HttpHandler.builder();
        }

        /**
         * Sets the issuer identifier for this JWKS configuration.
         * Used for logging and identification purposes.
         *
         * @param issuerIdentifier the issuer identifier
         * @return this builder instance
         */
        public HttpJwksLoaderConfigBuilder issuerIdentifier(@NonNull String issuerIdentifier) {
            this.issuerIdentifier = issuerIdentifier;
            return this;
        }

        /**
         * Sets the key rotation grace period.
         * Defaults to 1 hour if not specified.
         *
         * @param keyRotationGracePeriod the grace period duration
         * @return this builder instance
         */
        public HttpJwksLoaderConfigBuilder keyRotationGracePeriod(@NonNull Duration keyRotationGracePeriod) {
            Preconditions.checkArgument(!keyRotationGracePeriod.isNegative(), "keyRotationGracePeriod must not be negative");
            this.keyRotationGracePeriod = keyRotationGracePeriod;
            return this;
        }

        /**
         * Sets the maximum number of retired key sets to keep.
         * Defaults to 3 if not specified.
         *
         * @param maxRetiredKeySets the maximum number of retired key sets
         * @return this builder instance
         */
        public HttpJwksLoaderConfigBuilder maxRetiredKeySets(int maxRetiredKeySets) {
            Preconditions.checkArgument(maxRetiredKeySets > 0, "maxRetiredKeySets must be positive");
            this.maxRetiredKeySets = maxRetiredKeySets;
            return this;
        }

        /**
         * Sets the JWKS URI directly.
         * <p>
         * This method is mutually exclusive with {@link #jwksUrl(String)}, {@link #wellKnownUrl(String)}, and {@link #wellKnownUri(URI)}.
         * Only one endpoint configuration method can be used per builder instance.
         * </p>
         *
         * @param jwksUri the URI of the JWKS endpoint. Must not be null.
         * @return this builder instance
         * @throws IllegalArgumentException if another endpoint configuration method was already used
         */
        public HttpJwksLoaderConfigBuilder jwksUri(@NonNull URI jwksUri) {
            validateEndpointExclusivity(EndpointSource.JWKS_URI);
            this.endpointSource = EndpointSource.JWKS_URI;
            httpHandlerBuilder.uri(jwksUri);
            return this;
        }

        /**
         * Sets the JWKS URL as a string, which will be converted to a URI.
         * <p>
         * This method is mutually exclusive with {@link #jwksUri(URI)}, {@link #wellKnownUrl(String)}, and {@link #wellKnownUri(URI)}.
         * Only one endpoint configuration method can be used per builder instance.
         * </p>
         *
         * @param jwksUrl the URL string of the JWKS endpoint. Must not be null.
         * @return this builder instance
         * @throws IllegalArgumentException if another endpoint configuration method was already used
         */
        public HttpJwksLoaderConfigBuilder jwksUrl(@NonNull String jwksUrl) {
            validateEndpointExclusivity(EndpointSource.JWKS_URL);
            this.endpointSource = EndpointSource.JWKS_URL;
            httpHandlerBuilder.url(jwksUrl);
            return this;
        }

        /**
         * Configures the JWKS loading using well-known endpoint discovery from a URL string.
         * <p>
         * This method creates a {@link WellKnownConfig} internally for dynamic JWKS URI resolution.
         * The JWKS URI will be extracted at runtime from the well-known discovery document.
         * </p>
         * <p>
         * This method is mutually exclusive with {@link #jwksUri(URI)}, {@link #jwksUrl(String)}, and {@link #wellKnownUri(URI)}.
         * Only one endpoint configuration method can be used per builder instance.
         * </p>
         *
         * @param wellKnownUrl The well-known discovery endpoint URL string. Must not be null.
         * @return this builder instance
         * @throws IllegalArgumentException if another endpoint configuration method was already used
         * @throws IllegalArgumentException if {@code wellKnownUrl} is null or invalid
         */
        public HttpJwksLoaderConfigBuilder wellKnownUrl(@NonNull String wellKnownUrl) {
            validateEndpointExclusivity(EndpointSource.WELL_KNOWN_URL);
            this.endpointSource = EndpointSource.WELL_KNOWN_URL;
            this.wellKnownConfig = WellKnownConfig.builder()
                    .wellKnownUrl(wellKnownUrl)
                    .retryStrategy(RetryStrategy.exponentialBackoff())
                    .parserConfig(ParserConfig.builder().build())
                    .build();
            return this;
        }

        /**
         * Configures the JWKS loading using well-known endpoint discovery from a URI.
         * <p>
         * This method creates a {@link WellKnownConfig} internally for dynamic JWKS URI resolution.
         * The JWKS URI will be extracted at runtime from the well-known discovery document.
         * </p>
         * <p>
         * This method is mutually exclusive with {@link #jwksUri(URI)}, {@link #jwksUrl(String)}, and {@link #wellKnownUrl(String)}.
         * Only one endpoint configuration method can be used per builder instance.
         * </p>
         *
         * @param wellKnownUri The well-known discovery endpoint URI. Must not be null.
         * @return this builder instance
         * @throws IllegalArgumentException if another endpoint configuration method was already used
         * @throws IllegalArgumentException if {@code wellKnownUri} is null
         */
        public HttpJwksLoaderConfigBuilder wellKnownUri(@NonNull URI wellKnownUri) {
            validateEndpointExclusivity(EndpointSource.WELL_KNOWN_URI);
            this.endpointSource = EndpointSource.WELL_KNOWN_URI;
            this.wellKnownConfig = WellKnownConfig.builder()
                    .wellKnownUri(wellKnownUri)
                    .retryStrategy(RetryStrategy.exponentialBackoff())
                    .parserConfig(ParserConfig.builder().build())
                    .build();
            return this;
        }

        /**
         * Sets the TLS versions configuration.
         *
         * @param secureSSLContextProvider the TLS versions configuration to use
         * @return this builder instance
         */
        public HttpJwksLoaderConfigBuilder tlsVersions(SecureSSLContextProvider secureSSLContextProvider) {
            httpHandlerBuilder.tlsVersions(secureSSLContextProvider);
            return this;
        }

        /**
         * Sets the refresh interval in seconds.
         * <p>
         * If set to 0, no time-based caching will be used. It defaults to 10 minutes (600 seconds).
         * </p>
         *
         * @param refreshIntervalSeconds the refresh interval in seconds
         * @return this builder instance
         * @throws IllegalArgumentException if a refresh interval is negative
         */
        public HttpJwksLoaderConfigBuilder refreshIntervalSeconds(int refreshIntervalSeconds) {
            Preconditions.checkArgument(refreshIntervalSeconds > -1, "refreshIntervalSeconds must be zero or positive");
            this.refreshIntervalSeconds = refreshIntervalSeconds;
            return this;
        }


        /**
         * Sets the SSL context to use for HTTPS connections.
         * <p>
         * If not set, a default secure SSL context will be created.
         * </p>
         *
         * @param sslContext The SSL context to use.
         * @return This builder instance.
         */
        public HttpJwksLoaderConfigBuilder sslContext(SSLContext sslContext) {
            httpHandlerBuilder.sslContext(sslContext);
            return this;
        }

        /**
         * Sets the connection timeout in seconds.
         *
         * @param connectTimeoutSeconds the connection timeout in seconds
         * @return this builder instance
         * @throws IllegalArgumentException if connectTimeoutSeconds is not positive
         */
        public HttpJwksLoaderConfigBuilder connectTimeoutSeconds(int connectTimeoutSeconds) {
            Preconditions.checkArgument(connectTimeoutSeconds > 0, "connectTimeoutSeconds must be > 0, but was %s", connectTimeoutSeconds);
            httpHandlerBuilder.connectionTimeoutSeconds(connectTimeoutSeconds);
            return this;
        }

        /**
         * Sets the read timeout in seconds.
         *
         * @param readTimeoutSeconds the read timeout in seconds
         * @return this builder instance
         * @throws IllegalArgumentException if readTimeoutSeconds is not positive
         */
        public HttpJwksLoaderConfigBuilder readTimeoutSeconds(int readTimeoutSeconds) {
            Preconditions.checkArgument(readTimeoutSeconds > 0, "readTimeoutSeconds must be > 0, but was %s", readTimeoutSeconds);
            httpHandlerBuilder.readTimeoutSeconds(readTimeoutSeconds);
            return this;
        }

        /**
         * Sets the retry strategy for HTTP operations.
         *
         * @param retryStrategy the retry strategy to use for HTTP requests
         * @return this builder instance
         * @throws IllegalArgumentException if retryStrategy is null
         */
        public HttpJwksLoaderConfigBuilder retryStrategy(@NonNull RetryStrategy retryStrategy) {
            this.retryStrategy = retryStrategy;
            return this;
        }

        /**
         * Sets the ScheduledExecutorService for background refresh operations.
         *
         * @param scheduledExecutorService the executor service to use
         * @return this builder instance
         */
        public HttpJwksLoaderConfigBuilder scheduledExecutorService(ScheduledExecutorService scheduledExecutorService) {
            this.scheduledExecutorService = scheduledExecutorService;
            return this;
        }

        /**
         * Validates that the proposed endpoint source doesn't conflict with an already configured one.
         * <p>
         * This validation ensures mutual exclusivity between direct JWKS endpoint configuration
         * and well-known discovery configuration. When using well-known discovery, the issuer identifier
         * is automatically provided by the discovery document and cannot be manually configured.
         * </p>
         *
         * @param proposedSource the endpoint source that is being configured
         * @throws IllegalArgumentException if another endpoint configuration method was already used
         */
        private void validateEndpointExclusivity(EndpointSource proposedSource) {
            if (endpointSource != null && endpointSource != proposedSource) {
                throw new IllegalArgumentException(
                        """
                                Cannot use %s endpoint configuration when %s was already configured. \
                                Methods jwksUri(), jwksUrl(), wellKnownUrl(), and wellKnownUri() are mutually exclusive. \
                                When using well-known discovery, the issuer identifier is automatically provided by the discovery document."""
                                .formatted(proposedSource.name().toLowerCase().replace("_", ""), endpointSource.name().toLowerCase().replace("_", "")));
            }
        }

        /**
         * Builds a new HttpJwksLoaderConfig instance with the configured parameters.
         * Validates all parameters and applies default values where appropriate.
         *
         * @return a new HttpJwksLoaderConfig instance
         * @throws IllegalArgumentException if any parameter is invalid
         * @throws IllegalArgumentException if no endpoint was configured
         */
        @SuppressWarnings("java:S3776") // ok for builder
        public HttpJwksLoaderConfig build() {
            // Ensure at least one endpoint configuration method was used
            if (endpointSource == null) {
                throw new IllegalArgumentException(
                        "No JWKS endpoint configured. Must call one of: jwksUri(), jwksUrl(), wellKnownUrl(), or wellKnownUri()");
            }

            // Ensure RetryStrategy is configured
            if (retryStrategy == null) {
                retryStrategy = RetryStrategy.exponentialBackoff();
            }

            HttpHandler jwksHttpHandler = null;
            WellKnownConfig configuredWellKnownConfig = null;
            if (endpointSource == EndpointSource.WELL_KNOWN_URL || endpointSource == EndpointSource.WELL_KNOWN_URI) {
                // Use WellKnownConfig directly
                configuredWellKnownConfig = this.wellKnownConfig;
            } else {
                // Build the HttpHandler for direct URL/URI configuration
                try {
                    jwksHttpHandler = httpHandlerBuilder.build();
                    if (jwksHttpHandler == null) {
                        throw new IllegalArgumentException("HttpHandler build() returned null - this indicates a programming error in the builder");
                    }

                    // Check for insecure HTTP protocol
                    URI uri = jwksHttpHandler.getUri();
                    if (uri != null && "http".equalsIgnoreCase(uri.getScheme())) {
                        LOGGER.warn(JWTValidationLogMessages.WARN.INSECURE_HTTP_JWKS.format(uri.toString()));
                    }
                } catch (IllegalArgumentException | IllegalStateException e) {
                    LOGGER.warn(WARN.INVALID_JWKS_URI::format);
                    throw new IllegalArgumentException("Invalid URL or HttpHandler configuration", e);
                }
            }

            // Create default ScheduledExecutorService if not provided and refresh interval > 0
            ScheduledExecutorService executor = this.scheduledExecutorService;
            if (executor == null && refreshIntervalSeconds > 0) {
                String hostName = jwksHttpHandler != null ? jwksHttpHandler.getUri().getHost() : "wellknown";
                executor = Executors.newScheduledThreadPool(1, r -> {
                    Thread t = new Thread(r, "jwks-refresh-" + hostName);
                    t.setDaemon(true);
                    return t;
                });
            }

            // Validate issuer requirement
            if (configuredWellKnownConfig == null && jwksHttpHandler != null && issuerIdentifier == null) {
                throw new IllegalArgumentException("Issuer identifier is mandatory when using direct JWKS configuration");
            }

            // For well-known, issuer will be discovered dynamically during resolution (optional in config)

            return new HttpJwksLoaderConfig(
                    refreshIntervalSeconds,
                    jwksHttpHandler,
                    configuredWellKnownConfig,
                    retryStrategy,
                    executor,
                    issuerIdentifier,
                    keyRotationGracePeriod,
                    maxRetiredKeySets);
        }

    }

}