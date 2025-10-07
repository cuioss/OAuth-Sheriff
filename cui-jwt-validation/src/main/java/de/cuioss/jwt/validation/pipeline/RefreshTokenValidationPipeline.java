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

import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import de.cuioss.jwt.validation.domain.token.RefreshTokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.json.MapRepresentation;
import de.cuioss.tools.logging.CuiLogger;

import java.util.Map;

/**
 * Pipeline for validating refresh tokens.
 * <p>
 * Refresh tokens require minimal validation compared to access and ID tokens.
 * This pipeline attempts to parse the token as a JWT, but gracefully handles
 * parsing failures since refresh tokens may be opaque strings.
 * <p>
 * <strong>Validation Steps:</strong>
 * <ol>
 *   <li>Attempt to parse token as JWT (failures are allowed)</li>
 *   <li>Extract claims if JWT parsing succeeds, otherwise use empty claims</li>
 *   <li>Return RefreshTokenContent with token string and claims</li>
 * </ol>
 * <p>
 * <strong>Note:</strong> TokenStringValidator has already validated that the token
 * is non-null, non-blank, and within size limits before this pipeline is called.
 * <p>
 * <strong>No Metrics Instrumentation:</strong> Refresh token validation uses
 * {@code NoOpMetricsTicker} and does not record performance metrics.
 * <p>
 * <strong>No Caching:</strong> Refresh tokens are not cached.
 * <p>
 * <strong>No Security Events:</strong> This pipeline does not track security events.
 * The {@code REFRESH_TOKEN_CREATED} event is incremented by the caller (TokenValidator).
 * <p>
 * This class is thread-safe and stateless after construction.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public class RefreshTokenValidationPipeline {

    private static final CuiLogger LOGGER = new CuiLogger(RefreshTokenValidationPipeline.class);

    private final NonValidatingJwtParser jwtParser;

    /**
     * Creates a new RefreshTokenValidationPipeline.
     *
     * @param jwtParser the JWT parser for attempting to parse tokens
     */
    public RefreshTokenValidationPipeline(NonValidatingJwtParser jwtParser) {
        this.jwtParser = jwtParser;
    }

    /**
     * Validates and returns a refresh token.
     * <p>
     * This method attempts to parse the token as a JWT. If parsing succeeds,
     * claims are extracted. If parsing fails (e.g., token is opaque), an empty
     * claims map is used. Parsing failures are expected and do not cause validation
     * to fail.
     *
     * @param tokenString the token string to validate (guaranteed non-null, non-blank, within size limits)
     * @return the validated refresh token content
     */
   
    public RefreshTokenContent validate(String tokenString) {
        LOGGER.debug("Validating refresh token");

        // TokenStringValidator has already checked: null, blank, size

        // Try to parse as JWT (failure is allowed for opaque refresh tokens)
        // Use decodeOpaqueToken() to avoid logging warnings or tracking security events for expected failures
        Map<String, ClaimValue> claims = Map.of();
        try {
            DecodedJwt decoded = jwtParser.decodeOpaqueToken(tokenString);
            MapRepresentation body = decoded.getBody();
            if (!body.isEmpty()) {
                LOGGER.debug("Adding claims, because of being a JWT");
                claims = TokenBuilder.extractClaimsForRefreshToken(body);
            }
        } catch (TokenValidationException e) {
            // Ignore validation exceptions for refresh tokens - they may be opaque
            LOGGER.debug("Ignoring validation exception for refresh token: %s", e.getMessage());
        }

        var refreshToken = new RefreshTokenContent(tokenString, claims);
        LOGGER.debug("Successfully validated refresh token");

        return refreshToken;
    }
}
