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

import de.cuioss.jwt.validation.domain.claim.mapper.ClaimMapper;
import de.cuioss.jwt.validation.jwks.JwksLoader;
import de.cuioss.jwt.validation.jwks.JwksLoaderFactory;
import de.cuioss.jwt.validation.jwks.LoaderStatus;
import de.cuioss.jwt.validation.jwks.http.HttpJwksLoaderConfig;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.security.SignatureAlgorithmPreferences;
import de.cuioss.tools.base.Preconditions;
import de.cuioss.tools.logging.CuiLogger;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

import java.util.*;

/**
 * Configuration class for issuer settings.
 * It aggregates all information needed to validate a JWT token.
 * <p>
 * This class contains the issuer URL, expected audience, expected client ID,
 * configuration for JwksLoader and {@link SignatureAlgorithmPreferences}.
 * </p>
 * <p>
 * The JwksLoader is initialized through the {@link #initSecurityEventCounter(SecurityEventCounter)} method
 * and can be accessed through the {@link #jwksLoader} field.
 * </p>
 * <p>
 * This class is immutable after construction and thread-safe once the JwksLoader is initialized.
 * </p>
 * <p>
 * Usage example:
 * <pre>
 * // Create an issuer configuration with HTTP-based JWKS loading (well-known discovery)
 * IssuerConfig issuerConfig = IssuerConfig.builder()
 *     .expectedAudience("my-client")
 *     .httpJwksLoaderConfig(HttpJwksLoaderConfig.builder()
 *         .wellKnownUrl("https://example.com/.well-known/openid-configuration")
 *         .refreshIntervalSeconds(60)
 *         .build())
 *     .build(); // Validation happens automatically during build()
 *
 * // Initialize the security event counter -> This is usually done by TokenValidator
 * issuerConfig.initJWKSLoader(new SecurityEventCounter());
 *
 * // Issuer identifier is dynamically obtained from well-known discovery
 * String issuer = issuerConfig.getIssuerIdentifier();
 * </pre>
 * <p>
 * Implements requirements:
 * <ul>
 *   <li><a href="https://github.com/cuioss/cui-jwt/tree/main/doc/Requirements.adoc#CUI-JWT-3">CUI-JWT-3: Multi-Issuer Support</a></li>
 *   <li><a href="https://github.com/cuioss/cui-jwt/tree/main/doc/Requirements.adoc#CUI-JWT-4">CUI-JWT-4: Key Management</a></li>
 *   <li><a href="https://github.com/cuioss/cui-jwt/tree/main/doc/Requirements.adoc#CUI-JWT-8.4">CUI-JWT-8.4: Claims Validation</a></li>
 * </ul>
 * <p>
 * For more detailed specifications, see the
 * <a href="https://github.com/cuioss/cui-jwt/tree/main/doc/specification/technical-components.adoc#_issuerconfig_and_multi_issuer_support">Technical Components Specification - IssuerConfig and Multi-Issuer Support</a>
 *
 * @since 1.0
 */
@Getter
@EqualsAndHashCode
@ToString
public class IssuerConfig implements HealthStatusProvider {

    private static final CuiLogger LOGGER = new CuiLogger(IssuerConfig.class);

    /**
     * Whether this issuer configuration is enabled.
     * <p>
     * When set to {@code false}, this issuer configuration will be ignored during
     * token validation and will not attempt to use the underlying {@link JwksLoader}.
     * This allows for easy enabling/disabling of specific issuers without removing
     * their configuration.
     * <p>
     * Default value is {@code true}.
     */
    boolean enabled;

    /**
     * The issuer identifier for token validation.
     * <p>
     * This field is required for all JWKS loading variants except well-known discovery.
     * For well-known discovery, the issuer identifier is automatically extracted from
     * the discovery document and this field is optional.
     * </p>
     * <p>
     * This identifier must match the "iss" claim in validated tokens.
     * </p>
     */
    String issuerIdentifier;

    /**
     * Set of expected audience values.
     * These values are matched against the "aud" claim in the token.
     * If the token's audience claim matches any of these values, it is considered valid.
     */
    Set<String> expectedAudience;

    /**
     * Set of expected client ID values.
     * These values are matched against the "azp" or "client_id" claim in the token.
     * If the token's client ID claim matches any of these values, it is considered valid.
     */
    Set<String> expectedClientId;

    /**
     * Whether the "sub" (subject) claim is optional for this issuer.
     * <p>
     * When set to {@code true}, the mandatory claims validator will not require the "sub" claim
     * to be present in tokens from this issuer. This provides a workaround for identity providers
     * that don't include the subject claim in access tokens by default.
     * </p>
     * <p>
     * <strong>Warning:</strong> Setting this to {@code true} relaxes RFC 7519 compliance.
     * According to RFC 7519 Section 4.1.2, the "sub" claim is required for ACCESS_TOKEN and ID_TOKEN types.
     * Use this option only when necessary and ensure appropriate alternative validation mechanisms.
     * </p>
     * <p>
     * Default value is {@code false} (subject claim is mandatory, RFC compliant).
     * </p>
     *
     * @see de.cuioss.jwt.validation.domain.claim.ClaimName#SUBJECT
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.2">RFC 7519 - 4.1.2. "sub" (Subject) Claim</a>
     */
    boolean claimSubOptional;

    SignatureAlgorithmPreferences algorithmPreferences;

    /**
     * Custom claim mappers that take precedence over the default ones.
     * The key is the claim name, and the value is the mapper to use for that claim.
     */
    Map<String, ClaimMapper> claimMappers;

    /**
     * The JwksLoader instance used for loading JWKS keys.
     * This must be provided during construction and will be initialized
     * with the SecurityEventCounter by the TokenValidator.
     */
    @NonNull
    JwksLoader jwksLoader;

    /**
     * Initializes the JwksLoader if it's not already initialized and the issuer is enabled.
     * <p>
     * This method should be called by TokenValidator before using the JwksLoader.
     * It will initialize the JwksLoader based on the first available configuration in the following order:
     * <ol>
     *   <li>HTTP JwksLoader (httpJwksLoaderConfig)</li>
     *   <li>File JwksLoader (jwksFilePath)</li>
     *   <li>In-memory JwksLoader (jwksContent)</li>
     * </ol>
     * <p>
     * If the issuer is disabled ({@link #enabled} is {@code false}), this method will not
     * initialize the JwksLoader and will leave it as {@code null}.
     * <p>
     * <strong>Important:</strong> This method assumes the configuration has already been validated
     * during construction via the {@link IssuerConfigBuilder#build()} method. It focuses solely on
     * initializing the JwksLoader instances with the provided SecurityEventCounter.
     * <p>
     * This method is not thread-safe and should be called before the object is shared between threads.
     *
     * @param securityEventCounter the counter for security events, must not be null
     * @throws NullPointerException if securityEventCounter is null
     */
    public void initSecurityEventCounter(@NonNull SecurityEventCounter securityEventCounter) {
        // Skip initialization if the issuer is disabled
        if (!enabled) {
            return;
        }

        // Initialize the JwksLoader with the SecurityEventCounter
        jwksLoader.initJWKSLoader(securityEventCounter);
    }


    /**
     * Gets the issuer identifier for token validation.
     * <p>
     * This method provides the issuer identifier that should be used for token validation.
     * The resolution logic prioritizes dynamic issuer identification (for well-known discovery)
     * over static configuration:
     * </p>
     * <p>
     * The resolution logic is:
     * <ol>
     *   <li>If the JwksLoader is initialized and healthy, delegate to its issuer identifier first</li>
     *   <li>If the JwksLoader returns empty (for non-well-known cases), use the configured issuerIdentifier</li>
     *   <li>Throws an exception if neither is available (validation ensures this never happens)</li>
     * </ol>
     *
     * @return the issuer identifier, never null
     * @since 1.0
     */
    @NonNull
    public String getIssuerIdentifier() {
        // First try to get issuer identifier from JwksLoader (for well-known discovery)
        if (jwksLoader.isHealthy() == LoaderStatus.OK) {
            Optional<String> jwksLoaderIssuer = jwksLoader.getIssuerIdentifier();
            if (jwksLoaderIssuer.isPresent()) {
                return jwksLoaderIssuer.get();
            }
        }

        // Fall back to configured issuer identifier (for file-based, in-memory, etc.)
        Preconditions.checkState(issuerIdentifier != null,
                "issuerIdentifier is null - this indicates a bug in validation logic. " +
                        "Non-well-known JWKS loaders should have been validated to require issuerIdentifier during initialization.");
        return issuerIdentifier;
    }

    /**
     * Checks the health status of this issuer configuration.
     * <p>
     * This method provides a unified view of both configuration state (enabled) and runtime state (healthy).
     * The health check process:
     * <ol>
     *   <li>Returns {@link LoaderStatus#UNDEFINED} immediately if the issuer is disabled</li>
     *   <li>Returns {@link LoaderStatus#UNDEFINED} if the JwksLoader is not initialized</li>
     *   <li>Delegates to the underlying {@link JwksLoader#isHealthy()} method</li>
     * </ol>
     * <p>
     * For HTTP-based loaders, this may trigger lazy loading of JWKS content if not already loaded.
     * The method is designed to be fail-fast and thread-safe.
     * <p>
     * The status reflects the combined state of configuration and runtime health:
     * <ul>
     *   <li>{@link LoaderStatus#UNDEFINED} - Issuer is disabled or JwksLoader not initialized</li>
     *   <li>{@link LoaderStatus#OK} - Issuer is enabled and JwksLoader is healthy</li>
     *   <li>{@link LoaderStatus#ERROR} - Issuer is enabled but JwksLoader has errors</li>
     * </ul>
     *
     * @return the current health status of this issuer configuration
     * @since 1.0
     */
    @Override
    public LoaderStatus isHealthy() {
        // Return UNDEFINED if the issuer is disabled
        if (!enabled) {
            return LoaderStatus.UNDEFINED;
        }
        // Delegate to the underlying JwksLoader
        return jwksLoader.isHealthy();
    }

    /**
     * Creates a new builder for IssuerConfig.
     *
     * @return a new IssuerConfigBuilder instance
     */
    public static IssuerConfigBuilder builder() {
        return new IssuerConfigBuilder();
    }

    /**
     * Custom builder class that includes validation in the build() method.
     * <p>
     * This builder provides a fluent API for constructing {@link IssuerConfig} instances with proper validation.
     * It supports various JWKS loading methods including HTTP-based discovery, file-based loading, and in-memory content.
     * </p>
     * <p>
     * The builder validates configuration consistency during the {@link #build()} method call, ensuring that:
     * <ul>
     *   <li>At least one JWKS loading method is configured for enabled issuers</li>
     *   <li>Issuer identifier is provided when required (not needed for well-known discovery)</li>
     *   <li>Algorithm preferences and claim mappers are properly initialized</li>
     * </ul>
     */
    public static class IssuerConfigBuilder {
        // Lombok-generated fields
        private boolean enabled = true;
        private String issuerIdentifier;
        private Set<String> expectedAudience;
        private Set<String> expectedClientId;
        private boolean claimSubOptional = false;
        private SignatureAlgorithmPreferences algorithmPreferences = new SignatureAlgorithmPreferences();
        private Map<String, ClaimMapper> claimMappers;
        private JwksLoader jwksLoader;

        private HttpJwksLoaderConfig httpJwksLoaderConfig;
        private String jwksFilePath;
        private String jwksContent;

        /**
         * Sets whether this issuer configuration is enabled.
         * <p>
         * When set to {@code false}, this issuer configuration will be ignored during token validation
         * and will not attempt to use the underlying {@link JwksLoader}. This allows for easy
         * enabling/disabling of specific issuers without removing their configuration.
         * </p>
         * <p>
         * Default value is {@code true}.
         * </p>
         *
         * @param enabled {@code true} to enable this issuer configuration, {@code false} to disable it
         * @return this builder instance for method chaining
         */
        public IssuerConfigBuilder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        /**
         * Sets the issuer identifier for token validation.
         * <p>
         * This identifier must match the "iss" claim in validated tokens. It is required for all
         * JWKS loading variants except well-known discovery, where the issuer identifier is
         * automatically extracted from the discovery document.
         * </p>
         * <p>
         * Examples:
         * <ul>
         *   <li>{@code "https://auth.example.com"} - Standard OIDC issuer URL</li>
         *   <li>{@code "https://accounts.google.com"} - Google OAuth2 issuer</li>
         *   <li>{@code "internal-service"} - Internal service identifier</li>
         * </ul>
         *
         * @param issuerIdentifier the issuer identifier that must match the "iss" claim in tokens
         * @return this builder instance for method chaining
         * @see <a href="https://tools.ietf.org/html/rfc7519#section-4.1.1">RFC 7519 - "iss" (Issuer) Claim</a>
         */
        public IssuerConfigBuilder issuerIdentifier(String issuerIdentifier) {
            this.issuerIdentifier = issuerIdentifier;
            return this;
        }

        /**
         * Adds a single expected audience value for token validation.
         * <p>
         * This value will be matched against the "aud" claim in tokens. If the token's audience
         * claim matches any of the configured expected audiences, it is considered valid.
         * Multiple audience values can be added by calling this method multiple times.
         * </p>
         * <p>
         * Examples:
         * <ul>
         *   <li>{@code "my-client-app"} - Client application identifier</li>
         *   <li>{@code "https://api.example.com"} - API endpoint URL</li>
         *   <li>{@code "urn:my-service"} - URN-based service identifier</li>
         * </ul>
         *
         * @param expectedAudience the audience value that tokens must match
         * @return this builder instance for method chaining
         * @see <a href="https://tools.ietf.org/html/rfc7519#section-4.1.3">RFC 7519 - "aud" (Audience) Claim</a>
         */
        public IssuerConfigBuilder expectedAudience(String expectedAudience) {
            if (this.expectedAudience == null) {
                this.expectedAudience = new LinkedHashSet<>();
            }
            this.expectedAudience.add(expectedAudience);
            return this;
        }

        /**
         * Sets the complete set of expected audience values for token validation.
         * <p>
         * This replaces any previously configured audience values. The token's "aud" claim
         * must match at least one of these values for the token to be considered valid.
         * </p>
         *
         * @param expectedAudience the set of audience values that tokens must match
         * @return this builder instance for method chaining
         * @see <a href="https://tools.ietf.org/html/rfc7519#section-4.1.3">RFC 7519 - "aud" (Audience) Claim</a>
         */
        public IssuerConfigBuilder expectedAudience(Set<String> expectedAudience) {
            this.expectedAudience = expectedAudience;
            return this;
        }

        /**
         * Adds a single expected client ID value for token validation.
         * <p>
         * This value will be matched against the "azp" (authorized party) or "client_id" claims in tokens.
         * If the token's client ID claim matches any of the configured expected client IDs,
         * it is considered valid. Multiple client ID values can be added by calling this method multiple times.
         * </p>
         * <p>
         * Examples:
         * <ul>
         *   <li>{@code "web-app-client"} - Web application client identifier</li>
         *   <li>{@code "mobile-app-android"} - Mobile application client identifier</li>
         *   <li>{@code "service-to-service"} - Service-to-service client identifier</li>
         * </ul>
         *
         * @param expectedClientId the client ID value that tokens must match
         * @return this builder instance for method chaining
         * @see <a href="https://tools.ietf.org/html/rfc7519#section-4.1.4">RFC 7519 - Registered Claim Names</a>
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#IDToken">OpenID Connect Core - ID Token</a>
         */
        public IssuerConfigBuilder expectedClientId(String expectedClientId) {
            if (this.expectedClientId == null) {
                this.expectedClientId = new LinkedHashSet<>();
            }
            this.expectedClientId.add(expectedClientId);
            return this;
        }

        /**
         * Sets the complete set of expected client ID values for token validation.
         * <p>
         * This replaces any previously configured client ID values. The token's "azp" or "client_id"
         * claim must match at least one of these values for the token to be considered valid.
         * </p>
         *
         * @param expectedClientId the set of client ID values that tokens must match
         * @return this builder instance for method chaining
         * @see <a href="https://tools.ietf.org/html/rfc7519#section-4.1.4">RFC 7519 - Registered Claim Names</a>
         * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html#IDToken">OpenID Connect Core - ID Token</a>
         */
        public IssuerConfigBuilder expectedClientId(Set<String> expectedClientId) {
            this.expectedClientId = expectedClientId;
            return this;
        }

        /**
         * Sets whether the "sub" (subject) claim is optional for this issuer.
         * <p>
         * When set to {@code true}, the mandatory claims validator will not require the "sub" claim
         * to be present in tokens from this issuer. This provides a workaround for identity providers
         * that don't include the subject claim in access tokens by default.
         * </p>
         * <p>
         * <strong>Warning:</strong> Setting this to {@code true} relaxes RFC 7519 compliance.
         * According to RFC 7519 Section 4.1.2, the "sub" claim is required for ACCESS_TOKEN and ID_TOKEN types.
         * Use this option only when necessary and ensure appropriate alternative validation mechanisms.
         * </p>
         * <p>
         * Default value is {@code false} (subject claim is mandatory, RFC compliant).
         * </p>
         *
         * @param claimSubOptional {@code true} to make the subject claim optional, {@code false} to require it (RFC compliant)
         * @return this builder instance for method chaining
         * @see de.cuioss.jwt.validation.domain.claim.ClaimName#SUBJECT
         * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519#section-4.1.2">RFC 7519 - 4.1.2. "sub" (Subject) Claim</a>
         */
        public IssuerConfigBuilder claimSubOptional(boolean claimSubOptional) {
            this.claimSubOptional = claimSubOptional;
            return this;
        }

        /**
         * Sets the signature algorithm preferences for token validation.
         * <p>
         * This configuration controls which signature algorithms are preferred and allowed
         * during token validation. It can be used to enforce security policies, such as
         * requiring stronger algorithms or blacklisting weak ones.
         * </p>
         * <p>
         * If not explicitly set, default algorithm preferences will be used that include
         * commonly used secure algorithms like RS256, RS384, RS512, ES256, ES384, ES512, PS256, PS384, and PS512.
         * </p>
         * <p>
         * Example usage:
         * <pre>
         * SignatureAlgorithmPreferences preferences = SignatureAlgorithmPreferences.builder()
         *     .preferredAlgorithms(List.of("RS256", "ES256"))
         *     .allowedAlgorithms(List.of("RS256", "RS384", "ES256", "ES384"))
         *     .build();
         * builder.algorithmPreferences(preferences);
         * </pre>
         *
         * @param algorithmPreferences the signature algorithm preferences to use
         * @return this builder instance for method chaining
         */
        public IssuerConfigBuilder algorithmPreferences(SignatureAlgorithmPreferences algorithmPreferences) {
            this.algorithmPreferences = algorithmPreferences;
            return this;
        }

        /**
         * Adds a custom claim mapper for a specific claim name.
         * <p>
         * Claim mappers allow custom processing of JWT claims during token validation.
         * They can be used to transform claim values, validate custom claim formats,
         * or extract nested information from complex claim structures.
         * </p>
         * <p>
         * Custom claim mappers take precedence over the default claim processing logic.
         * Multiple mappers can be added by calling this method multiple times with different claim names.
         * </p>
         * <p>
         * Example usage:
         * <pre>
         * ClaimMapper customScopeMapper = new CustomScopeMapper();
         * builder.claimMapper("scope", customScopeMapper);
         *
         * ClaimMapper rolesMapper = new RolesMapper();
         * builder.claimMapper("roles", rolesMapper);
         * </pre>
         *
         * @param key the name of the claim to apply the custom mapper to
         * @param claimMapper the custom mapper implementation for processing the claim
         * @return this builder instance for method chaining
         */
        public IssuerConfigBuilder claimMapper(String key, ClaimMapper claimMapper) {
            if (this.claimMappers == null) {
                this.claimMappers = new LinkedHashMap<>();
            }
            this.claimMappers.put(key, claimMapper);
            return this;
        }

        /**
         * Sets the complete map of custom claim mappers.
         * <p>
         * This replaces any previously configured claim mappers. Each mapper in the map
         * will be used to process the corresponding claim during token validation.
         * </p>
         *
         * @param claimMappers a map where keys are claim names and values are the custom mappers
         * @return this builder instance for method chaining
         */
        public IssuerConfigBuilder claimMappers(Map<String, ClaimMapper> claimMappers) {
            this.claimMappers = claimMappers;
            return this;
        }

        /**
         * Sets a custom JwksLoader implementation.
         * <p>
         * This method allows providing a pre-configured custom {@link JwksLoader} instead of
         * using one of the built-in factory methods. This is useful for advanced use cases
         * where custom JWKS loading logic is required.
         * </p>
         * <p>
         * When using a custom JwksLoader, ensure that:
         * <ul>
         *   <li>The loader implements proper error handling and retry logic</li>
         *   <li>The loader's {@link JwksLoader#getJwksType()} method returns an appropriate type</li>
         *   <li>If the loader doesn't provide issuer identification, set {@link #issuerIdentifier(String)}</li>
         * </ul>
         * <p>
         * Note: If a custom JwksLoader is provided, the other JWKS configuration methods
         * ({@link #httpJwksLoaderConfig}, {@link #jwksFilePath}, {@link #jwksContent}) will be ignored.
         * </p>
         *
         * @param jwksLoader the custom JwksLoader implementation to use
         * @return this builder instance for method chaining
         */
        public IssuerConfigBuilder jwksLoader(JwksLoader jwksLoader) {
            this.jwksLoader = jwksLoader;
            return this;
        }

        // Configuration methods for different JWKS types
        /**
         * Sets the HTTP JWKS loader configuration for remote JWKS loading.
         * <p>
         * This method configures the issuer to load JWKS (JSON Web Key Set) from a remote HTTP endpoint.
         * This is the most common configuration for production environments where JWKS are served
         * by an identity provider or authorization server.
         * </p>
         * <p>
         * The HTTP loader supports several loading methods:
         * <ul>
         *   <li><strong>Direct JWKS URL:</strong> Load directly from a JWKS endpoint</li>
         *   <li><strong>Well-Known Discovery:</strong> Use OpenID Connect discovery to find the JWKS endpoint</li>
         *   <li><strong>Background Refresh:</strong> Automatically refresh JWKS in the background</li>
         *   <li><strong>Caching and ETags:</strong> Efficient caching with HTTP ETags support</li>
         * </ul>
         * <p>
         * Example configurations:
         * <pre>
         * // Direct JWKS URL
         * HttpJwksLoaderConfig directConfig = HttpJwksLoaderConfig.builder()
         *     .jwksUrl("https://auth.example.com/.well-known/jwks.json")
         *     .refreshIntervalSeconds(300) // 5 minutes
         *     .build();
         *
         * // Well-Known Discovery
         * HttpJwksLoaderConfig discoveryConfig = HttpJwksLoaderConfig.builder()
         *     .wellKnownUrl("https://auth.example.com/.well-known/openid-configuration")
         *     .refreshIntervalSeconds(600) // 10 minutes
         *     .build();
         *
         * builder.httpJwksLoaderConfig(directConfig);
         * </pre>
         * <p>
         * <strong>Important:</strong> When using well-known discovery, the {@link #issuerIdentifier(String)}
         * is optional as it will be automatically extracted from the discovery document. For direct JWKS URLs,
         * the issuer identifier should typically be provided.
         * </p>
         *
         * @param httpJwksLoaderConfig the HTTP JWKS loader configuration
         * @return this builder instance for method chaining
         * @see HttpJwksLoaderConfig
         * @see <a href="https://openid.net/specs/openid-connect-discovery-1_0.html">OpenID Connect Discovery</a>
         */
        public IssuerConfigBuilder httpJwksLoaderConfig(HttpJwksLoaderConfig httpJwksLoaderConfig) {
            this.httpJwksLoaderConfig = httpJwksLoaderConfig;
            return this;
        }

        /**
         * Sets the file path for loading JWKS from a local file.
         * <p>
         * This method configures the issuer to load JWKS (JSON Web Key Set) from a local file.
         * This is useful for development environments, testing, or scenarios where JWKS are
         * distributed as part of the application deployment.
         * </p>
         * <p>
         * The file should contain a valid JWKS JSON structure with public keys only.
         * The file will be read during the first key access and cached in memory.
         * </p>
         * <p>
         * Example usage:
         * <pre>
         * // Absolute path
         * builder.jwksFilePath("/etc/security/jwks.json");
         *
         * // Relative path (relative to application working directory)
         * builder.jwksFilePath("config/jwks.json");
         *
         * // Classpath resource (if using resource loading utilities)
         * builder.jwksFilePath("classpath:jwks/public-keys.json");
         * </pre>
         * <p>
         * <strong>Required:</strong> When using file-based JWKS loading, the {@link #issuerIdentifier(String)}
         * must be explicitly provided as it cannot be determined from the file content.
         * </p>
         * <p>
         * <strong>Security Note:</strong> Ensure the JWKS file contains only public keys and is properly
         * secured with appropriate file system permissions.
         * </p>
         *
         * @param jwksFilePath the path to the JWKS file (absolute or relative)
         * @return this builder instance for method chaining
         */
        public IssuerConfigBuilder jwksFilePath(String jwksFilePath) {
            this.jwksFilePath = jwksFilePath;
            return this;
        }

        /**
         * Sets the JWKS content directly as a JSON string.
         * <p>
         * This method configures the issuer to use JWKS (JSON Web Key Set) provided directly
         * as a JSON string. This is useful for testing, embedded configurations, or scenarios
         * where JWKS are generated or provided programmatically.
         * </p>
         * <p>
         * The content should be a valid JWKS JSON structure containing public keys only.
         * The content will be parsed during the first key access and cached in memory.
         * </p>
         * <p>
         * Example usage:
         * <pre>
         * String jwksJson = """{
         *   "keys": [
         *     {
         *       "kty": "RSA",
         *       "kid": "key-1",
         *       "n": "...",
         *       "e": "AQAB",
         *       "alg": "RS256"
         *     }
         *   ]
         * }""";
         *
         * builder.jwksContent(jwksJson);
         * </pre>
         * <p>
         * <strong>Required:</strong> When using in-memory JWKS content, the {@link #issuerIdentifier(String)}
         * must be explicitly provided as it cannot be determined from the content.
         * </p>
         * <p>
         * <strong>Security Note:</strong> Ensure the JWKS content contains only public keys and never
         * include private key material in the JWKS content.
         * </p>
         *
         * @param jwksContent the JWKS content as a JSON string
         * @return this builder instance for method chaining
         */
        public IssuerConfigBuilder jwksContent(String jwksContent) {
            this.jwksContent = jwksContent;
            return this;
        }

        /**
         * Builds and validates the IssuerConfig instance.
         * <p>
         * This method performs comprehensive validation of the configuration and creates the final
         * {@link IssuerConfig} instance. It ensures that all required fields are properly set
         * and that the configuration is internally consistent.
         * </p>
         * <p>
         * Validation includes:
         * <ul>
         *   <li><strong>JWKS Configuration:</strong> At least one JWKS loading method must be configured for enabled issuers</li>
         *   <li><strong>Issuer Identifier:</strong> Required for file-based and in-memory JWKS loading (optional for well-known discovery)</li>
         *   <li><strong>Algorithm Preferences:</strong> Initialized with secure defaults if not explicitly set</li>
         *   <li><strong>Claim Mappers:</strong> Initialized as empty map if not explicitly set</li>
         * </ul>
         * <p>
         * The method automatically creates the appropriate {@link JwksLoader} based on the configuration:
         * <ol>
         *   <li>Custom {@link JwksLoader} (if provided via {@link #jwksLoader(JwksLoader)})</li>
         *   <li>HTTP-based loader (if {@link #httpJwksLoaderConfig(HttpJwksLoaderConfig)} is configured)</li>
         *   <li>File-based loader (if {@link #jwksFilePath(String)} is configured)</li>
         *   <li>In-memory loader (if {@link #jwksContent(String)} is configured)</li>
         * </ol>
         * <p>
         * <strong>Note:</strong> The {@link SecurityEventCounter} is not initialized during build.
         * It must be set later via {@link IssuerConfig#initSecurityEventCounter(SecurityEventCounter)}
         * before the configuration can be used for token validation.
         * </p>
         *
         * @return a fully configured and validated {@link IssuerConfig} instance
         * @throws IllegalArgumentException if the configuration is invalid or incomplete
         */
        public IssuerConfig build() {
            // If enabled, validate and create JwksLoader if needed
            if (enabled) {
                validateConfiguration();
                createJwksLoaderIfNeeded();
            }

            // Warn about RFC compliance when subject claim is made optional
            if (claimSubOptional) {
                LOGGER.warn("IssuerConfig for issuer '%s' has claimSubOptional=true. " +
                        "This is not conform to RFC 7519 which requires the 'sub' claim for ACCESS_TOKEN and ID_TOKEN types. " +
                        "Use this setting only when necessary and ensure appropriate alternative validation mechanisms.",
                        issuerIdentifier != null ? issuerIdentifier : "unknown");
            }

            return new IssuerConfig(enabled, issuerIdentifier, expectedAudience, expectedClientId,
                    claimSubOptional, algorithmPreferences, claimMappers, jwksLoader);
        }

        private void validateConfiguration() {
            // Validate that at least one JWKS loading method is configured
            if (httpJwksLoaderConfig == null && jwksFilePath == null &&
                    jwksContent == null && jwksLoader == null) {
                throw new IllegalArgumentException("No JwksLoader configuration is present for enabled issuer. " +
                        "One of httpJwksLoaderConfig, jwksFilePath, jwksContent, or a custom jwksLoader must be provided.");
            }

            // Validate issuerIdentifier requirements based on JWKS loading method
            if (jwksLoader != null) {
                // For custom JwksLoaders, issuerIdentifier is required unless it's a well-known type
                if (issuerIdentifier == null && !jwksLoader.getJwksType().providesIssuerIdentifier()) {
                    throw new IllegalArgumentException("issuerIdentifier is required for custom JwksLoader unless it provides its own issuer identifier");
                }
            } else {
                // For built-in JWKS loading methods, validate issuerIdentifier requirements
                if ((jwksFilePath != null || jwksContent != null) && issuerIdentifier == null) {
                    throw new IllegalArgumentException("issuerIdentifier is required for file-based and in-memory JWKS loading");
                }
                // For HTTP well-known discovery, issuerIdentifier is optional (will be extracted from discovery)
            }
        }

        private void createJwksLoaderIfNeeded() {
            if (jwksLoader == null) {
                // Create JwksLoader based on the first available configuration
                // SecurityEventCounter will be set later via initJWKSLoader()
                if (httpJwksLoaderConfig != null) {
                    jwksLoader = JwksLoaderFactory.createHttpLoader(httpJwksLoaderConfig);
                } else if (jwksFilePath != null) {
                    jwksLoader = JwksLoaderFactory.createFileLoader(jwksFilePath);
                } else if (jwksContent != null) {
                    jwksLoader = JwksLoaderFactory.createInMemoryLoader(jwksContent);
                }
            }
        }
    }

    /**
     * Constructor for the validated IssuerConfig.
     * This is called only by the builder after validation.
     */
    @SuppressWarnings("java:S107") // ok for private constructor
    private IssuerConfig(boolean enabled, String issuerIdentifier, Set<String> expectedAudience,
                         Set<String> expectedClientId, boolean claimSubOptional, SignatureAlgorithmPreferences algorithmPreferences,
                         Map<String, ClaimMapper> claimMappers, @NonNull JwksLoader jwksLoader) {
        this.enabled = enabled;
        this.issuerIdentifier = issuerIdentifier;
        this.expectedAudience = expectedAudience != null ? expectedAudience : Set.of();
        this.expectedClientId = expectedClientId != null ? expectedClientId : Set.of();
        this.claimSubOptional = claimSubOptional;
        this.algorithmPreferences = algorithmPreferences != null ? algorithmPreferences : new SignatureAlgorithmPreferences();
        this.claimMappers = claimMappers != null ? claimMappers : Map.of();
        this.jwksLoader = jwksLoader;
    }

}
