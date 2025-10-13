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
package de.cuioss.jwt.validation.pipeline.validator;

import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.jwt.validation.TokenType;
import de.cuioss.jwt.validation.domain.claim.ClaimName;
import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import de.cuioss.jwt.validation.domain.claim.ClaimValueType;
import de.cuioss.jwt.validation.domain.token.TokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.tools.logging.CuiLogger;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.Set;

/**
 * Validator for JWT audience claims.
 * <p>
 * This class validates the audience (aud) claim according to OAuth 2.0 and OpenID Connect specifications.
 * It handles both string and string array audience claims and provides fallback to the authorized party (azp) claim.
 * <p>
 * Validation rules:
 * <ul>
 *   <li>ID tokens require an audience claim</li>
 *   <li>Access tokens can have optional audience claims</li>
 *   <li>If audience is missing, azp claim can serve as fallback</li>
 *   <li>At least one token audience must match an expected audience</li>
 * </ul>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@RequiredArgsConstructor
public class AudienceValidator {

    private static final CuiLogger LOGGER = new CuiLogger(AudienceValidator.class);
    private static final String AUDIENCE_MATCHES_EXPECTED_AUDIENCE_S = "Token audience matches expected audience: %s";


    private final Set<String> expectedAudience;


    private final SecurityEventCounter securityEventCounter;

    /**
     * Validates the audience claim of the token.
     *
     * @param token the token to validate
     * @throws TokenValidationException if audience validation fails
     */
    public void validateAudience(TokenContent token) {
        if (expectedAudience.isEmpty()) {
            LOGGER.debug("no expected audience is provided, skip validation");
            return;
        }

        var audienceClaim = token.getClaimOption(ClaimName.AUDIENCE);

        if (audienceClaim.isEmpty() || audienceClaim.get().isNotPresentForClaimValueType()) {
            handleMissingAudience(token);
            return;
        }

        validateAudienceClaim(audienceClaim.get());
    }

    private void handleMissingAudience(TokenContent token) {
        if (isAzpClaimMatchingExpectedAudience(token)) {
            return;
        }

        if (TokenType.ID_TOKEN.equals(token.getTokenType())) {
            LOGGER.warn(JWTValidationLogMessages.WARN.MISSING_CLAIM, ClaimName.AUDIENCE.getName());
            securityEventCounter.increment(SecurityEventCounter.EventType.MISSING_CLAIM);
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.MISSING_CLAIM,
                    "Missing required audience claim in ID token. Expected audience: " + expectedAudience + ", Available claims: " + token.getClaims().keySet()
            );
        } else {
            LOGGER.debug("Audience claim is optional for access tokens, so if it's not present, validation passes");
        }
    }

    private boolean isAzpClaimMatchingExpectedAudience(TokenContent token) {
        var azpClaim = token.getClaimOption(ClaimName.AUTHORIZED_PARTY);
        if (azpClaim.isPresent() && !azpClaim.get().isEmpty()) {
            String azp = azpClaim.get().getOriginalString();
            if (expectedAudience.contains(azp)) {
                LOGGER.debug("Audience claim is missing but azp claim matches expected audience: %s", azp);
                return true;
            }
        }
        return false;
    }

    private void validateAudienceClaim(ClaimValue claim) {
        if (claim.getType() == ClaimValueType.STRING_LIST) {
            validateStringListAudience(claim.getAsList());
        } else if (claim.getType() == ClaimValueType.STRING) {
            validateStringAudience(claim.getOriginalString());
        } else {
            LOGGER.warn(JWTValidationLogMessages.WARN.AUDIENCE_MISMATCH, claim.getOriginalString(), expectedAudience);
            securityEventCounter.increment(SecurityEventCounter.EventType.AUDIENCE_MISMATCH);
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.AUDIENCE_MISMATCH,
                    "Unexpected audience claim type: " + claim.getType() + ". Expected STRING or STRING_LIST. Audience value: " + claim.getOriginalString() + ", Expected audience: " + expectedAudience
            );
        }
    }

    private void validateStringListAudience(List<String> audienceList) {
        // Use streaming operations to avoid temporary collections and improve performance
        boolean hasMatch = audienceList.stream()
                .anyMatch(audience -> {
                    if (expectedAudience.contains(audience)) {
                        LOGGER.debug(AUDIENCE_MATCHES_EXPECTED_AUDIENCE_S, audience);
                        return true;
                    }
                    return false;
                });

        if (hasMatch) {
            return;
        }

        LOGGER.warn(JWTValidationLogMessages.WARN.AUDIENCE_MISMATCH, audienceList, expectedAudience);
        securityEventCounter.increment(SecurityEventCounter.EventType.AUDIENCE_MISMATCH);
        throw new TokenValidationException(
                SecurityEventCounter.EventType.AUDIENCE_MISMATCH,
                "Audience mismatch: token audience " + audienceList + " does not match any expected audience " + expectedAudience
        );
    }

    private void validateStringAudience(String singleAudience) {
        if (expectedAudience.contains(singleAudience)) {
            LOGGER.debug(AUDIENCE_MATCHES_EXPECTED_AUDIENCE_S, singleAudience);
            return;
        }

        LOGGER.warn(JWTValidationLogMessages.WARN.AUDIENCE_MISMATCH, singleAudience, expectedAudience);
        securityEventCounter.increment(SecurityEventCounter.EventType.AUDIENCE_MISMATCH);
        throw new TokenValidationException(
                SecurityEventCounter.EventType.AUDIENCE_MISMATCH,
                "Audience mismatch: token audience '" + singleAudience + "' does not match any expected audience " + expectedAudience + ". Please verify the client_id or audience configuration."
        );
    }
}