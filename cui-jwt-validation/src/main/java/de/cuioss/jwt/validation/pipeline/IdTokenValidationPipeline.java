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
package de.cuioss.jwt.validation.pipeline;

import de.cuioss.jwt.validation.IssuerConfig;
import de.cuioss.jwt.validation.IssuerConfigResolver;
import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.jwt.validation.domain.context.ValidationContext;
import de.cuioss.jwt.validation.domain.token.IdTokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.pipeline.validator.TokenClaimValidator;
import de.cuioss.jwt.validation.pipeline.validator.TokenHeaderValidator;
import de.cuioss.jwt.validation.pipeline.validator.TokenSignatureValidator;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.tools.logging.CuiLogger;

import java.util.Map;

/**
 * Pipeline for validating ID tokens.
 * <p>
 * ID tokens require full validation but are typically validated only once during
 * login flows, so they are not cached. This pipeline performs complete cryptographic
 * validation and claims checking.
 * <p>
 * <strong>Validation Steps:</strong>
 * <ol>
 *   <li>Parse token into header and payload</li>
 *   <li>Extract and validate issuer claim</li>
 *   <li>Resolve issuer configuration</li>
 *   <li>Validate JWT header</li>
 *   <li>Validate JWT signature (cryptographic verification)</li>
 *   <li>Build typed IdTokenContent object</li>
 *   <li>Validate token claims (expiration, audience, etc.)</li>
 * </ol>
 * <p>
 * <strong>Note:</strong> TokenStringValidator has already validated that the token
 * is non-null, non-blank, and within size limits before this pipeline is called.
 * <p>
 * <strong>No Metrics Instrumentation:</strong> ID token validation uses
 * {@code NoOpMetricsTicker} and does not record performance metrics.
 * <p>
 * <strong>No Caching:</strong> ID tokens are not cached.
 * <p>
 * This class is thread-safe after construction. All validators are pre-created
 * and cached in immutable maps for optimal performance.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public class IdTokenValidationPipeline {

    private static final CuiLogger LOGGER = new CuiLogger(IdTokenValidationPipeline.class);

    private final NonValidatingJwtParser jwtParser;
    private final IssuerConfigResolver issuerConfigResolver;
    private final Map<String, TokenSignatureValidator> signatureValidators;
    private final Map<String, TokenBuilder> tokenBuilders;
    private final Map<String, TokenClaimValidator> claimValidators;
    private final Map<String, TokenHeaderValidator> headerValidators;
    private final SecurityEventCounter securityEventCounter;

    /**
     * Creates a new IdTokenValidationPipeline.
     *
     * @param jwtParser the JWT parser for decoding tokens
     * @param issuerConfigResolver the resolver for issuer configurations
     * @param signatureValidators pre-created signature validators keyed by issuer
     * @param tokenBuilders pre-created token builders keyed by issuer
     * @param claimValidators pre-created claim validators keyed by issuer
     * @param headerValidators pre-created header validators keyed by issuer
     * @param securityEventCounter the security event counter for tracking operations
     */
    public IdTokenValidationPipeline(NonValidatingJwtParser jwtParser,
            IssuerConfigResolver issuerConfigResolver,
            Map<String, TokenSignatureValidator> signatureValidators,
            Map<String, TokenBuilder> tokenBuilders,
            Map<String, TokenClaimValidator> claimValidators,
            Map<String, TokenHeaderValidator> headerValidators,
            SecurityEventCounter securityEventCounter) {
        this.jwtParser = jwtParser;
        this.issuerConfigResolver = issuerConfigResolver;
        this.signatureValidators = signatureValidators;
        this.tokenBuilders = tokenBuilders;
        this.claimValidators = claimValidators;
        this.headerValidators = headerValidators;
        this.securityEventCounter = securityEventCounter;
    }

    /**
     * Validates and returns an ID token.
     * <p>
     * Performs complete validation including parsing, signature verification,
     * and claims validation. All validation steps must succeed for the token
     * to be considered valid.
     *
     * @param tokenString the token string to validate (guaranteed non-null, non-blank, within size limits)
     * @return the validated ID token content
     * @throws TokenValidationException if any validation step fails
     */
   
    public IdTokenContent validate(String tokenString) {
        LOGGER.debug("Validating ID token");

        // TokenStringValidator has already checked: null, blank, size

        // 1. Parse token
        DecodedJwt decodedJwt = jwtParser.decode(tokenString);

        // 2. Extract issuer
        String issuerString = decodedJwt.getIssuer().orElseThrow(() -> {
            LOGGER.warn(JWTValidationLogMessages.WARN.MISSING_CLAIM, "iss");
            securityEventCounter.increment(SecurityEventCounter.EventType.MISSING_CLAIM);
            return new TokenValidationException(
                    SecurityEventCounter.EventType.MISSING_CLAIM,
                    "Missing required issuer (iss) claim in token"
            );
        });

        // 3. Resolve issuer config
        IssuerConfig issuerConfig = issuerConfigResolver.resolveConfig(issuerString);

        // 4. Validate header
        TokenHeaderValidator headerValidator = headerValidators.get(issuerConfig.getIssuerIdentifier());
        headerValidator.validate(decodedJwt);

        // 5. Validate signature
        // Note: signatureValidator is guaranteed to exist because TokenValidator
        // creates validators for all configured issuers during construction
        TokenSignatureValidator signatureValidator = signatureValidators.get(issuerConfig.getIssuerIdentifier());
        signatureValidator.validateSignature(decodedJwt);

        // 6. Build token
        // Note: tokenBuilder is guaranteed to exist because TokenValidator
        // creates builders for all configured issuers during construction
        TokenBuilder tokenBuilder = tokenBuilders.get(issuerConfig.getIssuerIdentifier());
        IdTokenContent token = tokenBuilder.createIdToken(decodedJwt)
                .orElseThrow(() -> {
                    LOGGER.debug("ID token building failed");
                    return new TokenValidationException(
                            SecurityEventCounter.EventType.MISSING_CLAIM,
                            "Failed to build ID token from decoded JWT"
                    );
                });

        // 7. Validate claims
        // Create ValidationContext with cached current time to eliminate synchronous OffsetDateTime.now() calls
        // Use clock skew of 60 seconds as per ExpirationValidator.CLOCK_SKEW_SECONDS
        // Note: claimValidator is guaranteed to exist because TokenValidator
        // creates validators for all configured issuers during construction
        ValidationContext context = new ValidationContext(60);
        TokenClaimValidator claimValidator = claimValidators.get(issuerConfig.getIssuerIdentifier());
        IdTokenContent validatedToken = (IdTokenContent) claimValidator.validate(token, context);

        LOGGER.debug("Successfully validated ID token");

        return validatedToken;
    }
}
