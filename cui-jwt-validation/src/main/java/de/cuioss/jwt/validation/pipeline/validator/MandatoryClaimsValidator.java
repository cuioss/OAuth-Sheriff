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

import de.cuioss.jwt.validation.IssuerConfig;
import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.jwt.validation.domain.claim.ClaimName;
import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import de.cuioss.jwt.validation.domain.token.TokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.tools.logging.CuiLogger;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Validator for mandatory JWT claims based on token type.
 * <p>
 * This class validates that all mandatory claims for a specific token type are present and properly set.
 * The mandatory claims are defined by the {@link de.cuioss.jwt.validation.TokenType} and vary between
 * access tokens, ID tokens, and refresh tokens.
 * <p>
 * The validator checks both claim presence and claim value validity. For certain claims like "sub" (subject),
 * the validation can be configured per-issuer to accommodate identity providers that don't include the
 * subject claim in access tokens by default.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@RequiredArgsConstructor
public class MandatoryClaimsValidator {

    private static final CuiLogger LOGGER = new CuiLogger(MandatoryClaimsValidator.class);

    @NonNull
    private final IssuerConfig issuerConfig;

    @NonNull
    private final SecurityEventCounter securityEventCounter;

    /**
     * Validates whether all mandatory claims for the current token type are present and set.
     *
     * @param tokenContent the token content to validate
     * @throws TokenValidationException if any mandatory claims are missing
     */
    public void validateMandatoryClaims(TokenContent tokenContent) {
        var mandatoryNames = tokenContent.getTokenType().getMandatoryClaims().stream()
                .map(ClaimName::getName)
                .collect(Collectors.toSet());

        LOGGER.debug("%s, verifying mandatory claims: %s", tokenContent.getTokenType(), mandatoryNames);

        SortedSet<String> missingClaims = collectMissingClaims(tokenContent, mandatoryNames);

        if (missingClaims.isEmpty()) {
            LOGGER.debug("All mandatory claims are present and set as expected");
            return;
        }

        handleMissingClaims(tokenContent, missingClaims);
    }

    private SortedSet<String> collectMissingClaims(TokenContent tokenContent, Set<String> mandatoryNames) {
        SortedSet<String> missingClaims = new TreeSet<>();

        for (var claimName : mandatoryNames) {
            // Check if this claim should be skipped based on issuer configuration
            if (shouldSkipClaimValidation(claimName)) {
                LOGGER.debug("Skipping validation for claim '%s' due to issuer configuration (claimSubOptional=true)", claimName);
                continue;
            }

            if (isClaimMissing(tokenContent, claimName)) {
                missingClaims.add(claimName);
            }
        }

        return missingClaims;
    }

    /**
     * Determines if validation for a specific claim should be skipped based on issuer configuration.
     * <p>
     * Currently supports making the "sub" (subject) claim optional when claimSubOptional is enabled.
     * This provides a workaround for identity providers that don't include the subject claim in
     * access tokens by default.
     * </p>
     *
     * @param claimName the name of the claim to check
     * @return {@code true} if validation should be skipped, {@code false} otherwise
     */
    private boolean shouldSkipClaimValidation(String claimName) {
        // Skip validation for "sub" claim if issuer configuration allows it
        return ClaimName.SUBJECT.getName().equals(claimName) && issuerConfig.isClaimSubOptional();
    }

    private boolean isClaimMissing(TokenContent tokenContent, String claimName) {
        if (!tokenContent.getClaims().containsKey(claimName)) {
            return true;
        }

        ClaimValue claimValue = tokenContent.getClaims().get(claimName);
        if (!claimValue.isPresent()) {
            logMissingClaimValue(claimName, claimValue);
            return true;
        }

        return false;
    }

    private void logMissingClaimValue(String claimName, ClaimValue claimValue) {
        var claimNameEnum = ClaimName.fromString(claimName);
        if (claimNameEnum.isPresent()) {
            LOGGER.debug("Claim %s is present but not set as expected: %s. Specification: %s",
                    claimName, claimValue, claimNameEnum.get().getSpec());
        } else {
            LOGGER.debug("Claim %s is present but not set as expected: %s", claimName, claimValue);
        }
    }

    private void handleMissingClaims(TokenContent tokenContent, SortedSet<String> missingClaims) {
        LOGGER.warn(JWTValidationLogMessages.WARN.MISSING_CLAIM.format(missingClaims));
        securityEventCounter.increment(SecurityEventCounter.EventType.MISSING_CLAIM);

        String errorMessage = buildErrorMessage(tokenContent, missingClaims);

        throw new TokenValidationException(
                SecurityEventCounter.EventType.MISSING_CLAIM,
                errorMessage
        );
    }

    private String buildErrorMessage(TokenContent tokenContent, SortedSet<String> missingClaims) {
        StringBuilder errorMessage = new StringBuilder("Missing mandatory claims: ").append(missingClaims);

        String claimSpecs = buildClaimSpecifications(missingClaims);
        if (!claimSpecs.isEmpty()) {
            errorMessage.append("\n\nClaim specifications:").append(claimSpecs);
        }

        errorMessage.append("\n\nAvailable claims: ").append(tokenContent.getClaims().keySet())
                .append(". Please ensure the token includes all required claims.");

        return errorMessage.toString();
    }

    private String buildClaimSpecifications(SortedSet<String> missingClaims) {
        StringBuilder claimSpecs = new StringBuilder();

        for (String missingClaim : missingClaims) {
            var claimNameEnum = ClaimName.fromString(missingClaim);
            claimNameEnum.ifPresent(claimName ->
                    claimSpecs.append("\n- ").append(missingClaim).append(": ").append(claimName.getSpec())
            );
        }

        return claimSpecs.toString();
    }
}