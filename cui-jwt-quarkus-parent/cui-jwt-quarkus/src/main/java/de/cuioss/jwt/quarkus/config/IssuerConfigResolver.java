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
package de.cuioss.jwt.quarkus.config;

import de.cuioss.http.client.retry.RetryStrategies;
import de.cuioss.http.client.retry.RetryStrategy;
import de.cuioss.jwt.validation.IssuerConfig;
import de.cuioss.jwt.validation.domain.claim.mapper.KeycloakDefaultGroupsMapper;
import de.cuioss.jwt.validation.domain.claim.mapper.KeycloakDefaultRolesMapper;
import de.cuioss.jwt.validation.jwks.http.HttpJwksLoaderConfig;
import de.cuioss.jwt.validation.security.SignatureAlgorithmPreferences;
import de.cuioss.tools.logging.CuiLogger;
import lombok.NonNull;
import org.eclipse.microprofile.config.Config;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages.INFO;

/**
 * Resolver for creating {@link IssuerConfig} instances from Quarkus configuration properties.
 * <p>
 * This class handles the complex logic of discovering issuer configurations from properties,
 * validating mutual exclusivity constraints, and creating properly configured IssuerConfig instances.
 * It uses the template-based property key system for dynamic issuer configuration.
 * </p>
 * <p>
 * The resolver supports all JWKS source types:
 * <ul>
 *   <li>HTTP JWKS URL - Direct JWKS endpoint</li>
 *   <li>Well-known discovery - OpenID Connect discovery</li>
 *   <li>File path - Local JWKS file</li>
 *   <li>Inline content - JWKS content as string</li>
 * </ul>
 * <p>
 * Configuration validation is delegated to the underlying builders, ensuring consistency
 * with the core validation logic without duplication.
 * </p>
 *
 * @since 1.0
 */
public class IssuerConfigResolver {

    private static final CuiLogger LOGGER = new CuiLogger(IssuerConfigResolver.class);

    private final Config config;
    private final RetryStrategy retryStrategy;

    /**
     * Creates a new IssuerConfigResolver with the specified configuration.
     *
     * @param config the configuration instance to use for property resolution
     */
    public IssuerConfigResolver(@NonNull Config config) {
        this(config, RetryStrategies.exponentialBackoff());
    }

    /**
     * Creates a new IssuerConfigResolver with the specified configuration and retry strategy.
     *
     * @param config the configuration instance to use for property resolution
     * @param retryStrategy the retry strategy to use for HTTP operations
     */
    public IssuerConfigResolver(@NonNull Config config, @NonNull RetryStrategy retryStrategy) {
        this.config = config;
        this.retryStrategy = retryStrategy;
    }

    /**
     * Resolves all enabled issuer configurations from properties.
     * <p>
     * This method discovers all configured issuers by scanning properties with the
     * issuer template pattern, validates each configuration, and creates IssuerConfig
     * instances using the appropriate builders.
     * </p>
     * <p>
     * Validation is performed by the underlying builders, ensuring that configuration
     * errors are caught early with appropriate error messages.
     * </p>
     *
     * @return list of valid, enabled IssuerConfig instances
     * @throws IllegalStateException if no enabled issuers are found or configuration is invalid
     */
    @NonNull
    public List<IssuerConfig> resolveIssuerConfigs() {
        LOGGER.info(INFO.RESOLVING_ISSUER_CONFIGURATIONS::format);

        Set<String> issuerNames = discoverIssuerNames();
        if (issuerNames.isEmpty()) {
            throw new IllegalStateException("No issuer configurations found in properties");
        }

        List<IssuerConfig> enabledIssuers = new ArrayList<>();
        for (String issuerName : issuerNames) {
            LOGGER.debug("Resolving issuer configuration for %s", issuerName);
            if (isIssuerEnabled(issuerName)) {
                IssuerConfig issuerConfig = createIssuerConfig(issuerName);
                enabledIssuers.add(issuerConfig);
                LOGGER.info(INFO.RESOLVED_ISSUER_CONFIGURATION.format(issuerName));
            } else {
                LOGGER.debug("Skipping disabled issuer: %s", issuerName);
            }
        }

        if (enabledIssuers.isEmpty()) {
            throw new IllegalStateException("No enabled issuer configurations found");
        }

        LOGGER.info(INFO.RESOLVED_ENABLED_ISSUER_CONFIGURATIONS.format(enabledIssuers.size()));
        return enabledIssuers;
    }

    /**
     * Discovers all configured issuer names by scanning properties.
     * <p>
     * Scans all property names for the issuer template pattern and extracts
     * the issuer names from matching properties.
     * </p>
     *
     * @return set of discovered issuer names
     */
    private Set<String> discoverIssuerNames() {
        Set<String> issuerNames = new HashSet<>();
        String basePrefix = JwtPropertyKeys.PREFIX + ".issuers.";

        for (String propertyName : config.getPropertyNames()) {
            if (propertyName.startsWith(basePrefix)) {
                String remainder = propertyName.substring(basePrefix.length());
                int firstDot = remainder.indexOf('.');
                if (firstDot > 0) {
                    String issuerName = remainder.substring(0, firstDot);
                    issuerNames.add(issuerName);
                }
            }
        }

        LOGGER.debug("Discovered issuer names: %s", issuerNames);
        return issuerNames;
    }

    /**
     * Checks if an issuer is enabled in the configuration.
     *
     * @param issuerName the issuer name
     * @return true if the issuer is enabled, false otherwise
     */
    private boolean isIssuerEnabled(String issuerName) {
        return config.getOptionalValue(
                JwtPropertyKeys.ISSUERS.ENABLED.formatted(issuerName),
                Boolean.class
        ).orElse(true); // Default to enabled if not explicitly disabled
    }

    /**
     * Creates an IssuerConfig for the specified issuer name.
     * <p>
     * This method builds the IssuerConfig using the builder pattern, allowing
     * the builder to perform all validation logic. Configuration errors are
     * wrapped in more descriptive exceptions for Quarkus context.
     * </p>
     *
     * @param issuerName the issuer name
     * @return configured IssuerConfig instance
     * @throws IllegalArgumentException if configuration is invalid
     */
    private IssuerConfig createIssuerConfig(String issuerName) {
        IssuerConfig.IssuerConfigBuilder builder = IssuerConfig.builder()
                .enabled(isIssuerEnabled(issuerName));

        // Configure core properties
        configureIssuerIdentifier(builder, issuerName);
        configureAudience(builder, issuerName);
        configureClientId(builder, issuerName);
        configureAlgorithmPreferences(builder, issuerName);
        configureClaimSubOptional(builder, issuerName);

        // Configure JWKS source (mutually exclusive)
        configureJwksSource(builder, issuerName);

        // Configure Keycloak mappers if enabled
        configureKeycloakMappers(builder, issuerName);

        // Let the builder validate and create the instance
        return builder.build();
    }

    /**
     * Configures the issuer identifier from properties.
     * <p>
     * The issuer identifier is required for most JWKS sources except well-known discovery.
     * The builder will validate this requirement.
     * </p>
     */
    private void configureIssuerIdentifier(IssuerConfig.IssuerConfigBuilder builder, String issuerName) {
        Optional<String> issuerIdentifier = config.getOptionalValue(
                JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted(issuerName),
                String.class
        );

        if (issuerIdentifier.isPresent()) {
            builder.issuerIdentifier(issuerIdentifier.get());
            LOGGER.debug("Set issuer identifier for %s: %s", issuerName, issuerIdentifier.get());
        }
    }

    /**
     * Configures expected audience values from properties.
     */
    private void configureAudience(IssuerConfig.IssuerConfigBuilder builder, String issuerName) {
        Optional<String> audienceString = config.getOptionalValue(
                JwtPropertyKeys.ISSUERS.EXPECTED_AUDIENCE.formatted(issuerName),
                String.class
        );

        if (audienceString.isPresent()) {
            Set<String> audiences = Arrays.stream(audienceString.get().split(","))
                    .map(String::trim)
                    .collect(Collectors.toSet());
            audiences.forEach(builder::expectedAudience);
            LOGGER.debug("Set expected audiences for %s: %s", issuerName, audiences);
        }
    }

    /**
     * Configures expected client ID values from properties.
     */
    private void configureClientId(IssuerConfig.IssuerConfigBuilder builder, String issuerName) {
        Optional<String> clientIdString = config.getOptionalValue(
                JwtPropertyKeys.ISSUERS.EXPECTED_CLIENT_ID.formatted(issuerName),
                String.class
        );

        if (clientIdString.isPresent()) {
            Set<String> clientIds = Arrays.stream(clientIdString.get().split(","))
                    .map(String::trim)
                    .collect(Collectors.toSet());
            clientIds.forEach(builder::expectedClientId);
            LOGGER.debug("Set expected client IDs for %s: %s", issuerName, clientIds);
        }
    }

    /**
     * Configures algorithm preferences from properties.
     */
    private void configureAlgorithmPreferences(IssuerConfig.IssuerConfigBuilder builder, String issuerName) {
        Optional<String> algorithmsString = config.getOptionalValue(
                JwtPropertyKeys.ISSUERS.ALGORITHM_PREFERENCES.formatted(issuerName),
                String.class
        );

        if (algorithmsString.isPresent()) {
            List<String> algorithms = Arrays.asList(algorithmsString.get().split(","));
            algorithms.replaceAll(String::trim);

            SignatureAlgorithmPreferences preferences = new SignatureAlgorithmPreferences(algorithms);

            builder.algorithmPreferences(preferences);
            LOGGER.debug("Set algorithm preferences for %s: %s", issuerName, algorithms);
        }
    }

    /**
     * Configures claim subject optional flag from properties.
     */
    private void configureClaimSubOptional(IssuerConfig.IssuerConfigBuilder builder, String issuerName) {
        Optional<Boolean> claimSubOptional = config.getOptionalValue(
                JwtPropertyKeys.ISSUERS.CLAIM_SUB_OPTIONAL.formatted(issuerName),
                Boolean.class
        );

        if (claimSubOptional.isPresent()) {
            builder.claimSubOptional(claimSubOptional.get());
            LOGGER.debug("Set claim subject optional for %s: %s", issuerName, claimSubOptional.get());
        }
    }

    /**
     * Configures the JWKS source for the issuer.
     * <p>
     * This method handles the mutually exclusive JWKS sources:
     * HTTP URL, well-known discovery, file path, and inline content.
     * The builder will validate mutual exclusivity constraints.
     * </p>
     */
    private void configureJwksSource(IssuerConfig.IssuerConfigBuilder builder, String issuerName) {
        // HTTP JWKS URL
        Optional<String> jwksUrl = config.getOptionalValue(
                JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(issuerName),
                String.class
        );

        // Well-known discovery
        Optional<String> wellKnownUrl = config.getOptionalValue(
                JwtPropertyKeys.ISSUERS.WELL_KNOWN_URL.formatted(issuerName),
                String.class
        );

        // File path
        Optional<String> filePath = config.getOptionalValue(
                JwtPropertyKeys.ISSUERS.JWKS_FILE_PATH.formatted(issuerName),
                String.class
        );

        // Inline content
        Optional<String> content = config.getOptionalValue(
                JwtPropertyKeys.ISSUERS.JWKS_CONTENT.formatted(issuerName),
                String.class
        );

        // Check for HTTP-based JWKS sources (mutually exclusive)
        if (jwksUrl.isPresent() || wellKnownUrl.isPresent()) {
            HttpJwksLoaderConfig httpConfig = createHttpJwksLoaderConfig(
                    issuerName,
                    jwksUrl.orElse(null),
                    wellKnownUrl.orElse(null)
            );
            builder.httpJwksLoaderConfig(httpConfig);

            if (jwksUrl.isPresent()) {
                LOGGER.debug("Configured HTTP JWKS URL for %s: %s", issuerName, jwksUrl.get());
            } else {
                LOGGER.debug("Configured well-known discovery for %s: %s", issuerName, wellKnownUrl.get());
            }

        } else if (filePath.isPresent()) {
            builder.jwksFilePath(filePath.get());
            LOGGER.debug("Configured JWKS file path for %s: %s", issuerName, filePath.get());

        } else if (content.isPresent()) {
            builder.jwksContent(content.get());
            LOGGER.debug("Configured inline JWKS content for %s", issuerName);
        }
    }

    /**
     * Creates an HttpJwksLoaderConfig for HTTP-based JWKS loading.
     * <p>
     * Uses builder defaults for optional values, letting the builder handle validation.
     * </p>
     */
    private HttpJwksLoaderConfig createHttpJwksLoaderConfig(String issuerName, String jwksUrl, String wellKnownUrl) {
        HttpJwksLoaderConfig.HttpJwksLoaderConfigBuilder builder = HttpJwksLoaderConfig.builder();

        // Set the issuer identifier (required for direct JWKS configuration)
        Optional<String> issuerIdentifier = config.getOptionalValue(
                JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted(issuerName),
                String.class
        );
        if (issuerIdentifier.isPresent()) {
            builder.issuerIdentifier(issuerIdentifier.get());
        } else {
            // Use the issuer name as fallback if no explicit identifier is configured
            builder.issuerIdentifier(issuerName);
        }

        // Configure URLs - let the builder handle mutual exclusivity validation
        if (jwksUrl != null) {
            builder.jwksUrl(jwksUrl);
        }
        if (wellKnownUrl != null) {
            builder.wellKnownUrl(wellKnownUrl);
        }

        // Configure HTTP timeouts (use builder defaults if not specified)
        config.getOptionalValue(
                JwtPropertyKeys.ISSUERS.REFRESH_INTERVAL_SECONDS.formatted(issuerName),
                Integer.class
        ).ifPresent(builder::refreshIntervalSeconds);

        config.getOptionalValue(
                JwtPropertyKeys.ISSUERS.CONNECT_TIMEOUT_SECONDS.formatted(issuerName),
                Integer.class
        ).ifPresent(builder::connectTimeoutSeconds);

        config.getOptionalValue(
                JwtPropertyKeys.ISSUERS.READ_TIMEOUT_SECONDS.formatted(issuerName),
                Integer.class
        ).ifPresent(builder::readTimeoutSeconds);

        // Configure key rotation grace period (Issue #110)
        Optional<Integer> gracePeriodSeconds = config.getOptionalValue(
                JwtPropertyKeys.ISSUERS.KEY_ROTATION_GRACE_PERIOD_SECONDS.formatted(issuerName),
                Integer.class
        );
        if (gracePeriodSeconds.isPresent()) {
            builder.keyRotationGracePeriod(Duration.ofSeconds(gracePeriodSeconds.get()));
            LOGGER.debug("Set key rotation grace period for %s: %s seconds", issuerName, gracePeriodSeconds.get());
        }

        Optional<Integer> maxRetiredSets = config.getOptionalValue(
                JwtPropertyKeys.ISSUERS.MAX_RETIRED_KEY_SETS.formatted(issuerName),
                Integer.class
        );
        if (maxRetiredSets.isPresent()) {
            builder.maxRetiredKeySets(maxRetiredSets.get());
            LOGGER.debug("Set max retired key sets for %s: %s", issuerName, maxRetiredSets.get());
        }

        // Set the retry strategy
        builder.retryStrategy(retryStrategy);

        // Let the builder validate and create the instance
        return builder.build();
    }

    /**
     * Configures Keycloak default mappers if enabled for this issuer.
     * <p>
     * Adds Keycloak-specific claim mappers based on per-issuer configuration.
     * This allows different issuers to have different Keycloak mapper settings.
     * </p>
     */
    private void configureKeycloakMappers(IssuerConfig.IssuerConfigBuilder builder, String issuerName) {
        KeycloakMapperConfigResolver keycloakResolver = new KeycloakMapperConfigResolver(config);
        KeycloakMapperConfigResolver.KeycloakMapperConfig keycloakConfig = keycloakResolver.resolve(issuerName);

        if (keycloakConfig.isDefaultRolesEnabled()) {
            builder.claimMapper("roles", new KeycloakDefaultRolesMapper());
            LOGGER.debug("Added Keycloak default roles mapper for issuer: %s", issuerName);
        }

        if (keycloakConfig.isDefaultGroupsEnabled()) {
            builder.claimMapper("groups", new KeycloakDefaultGroupsMapper());
            LOGGER.debug("Added Keycloak default groups mapper for issuer: %s", issuerName);
        }
    }
}
