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
package de.cuioss.jwt.validation;

import de.cuioss.jwt.validation.domain.claim.ClaimName;
import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.security.AlgorithmPreferences;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.test.InMemoryJWKSFactory;
import de.cuioss.jwt.validation.test.JwtTokenTamperingUtil;
import de.cuioss.jwt.validation.test.TestTokenHolder;
import de.cuioss.jwt.validation.test.junit.TestTokenSource;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the security event counting functionality in {@link TokenValidator}.
 * <p>
 * Verifies requirements:
 * <ul>
 *   <li>CUI-JWT-7.1: Security Event Tracking</li>
 *   <li>CUI-JWT-7.2: Monitoring of Token Validation Failures</li>
 *   <li>CUI-JWT-7.3: Detection of Potential Security Incidents</li>
 * </ul>
 *
 * @author Oliver Wolff
 * @see <a href="https://github.com/cuioss/cui-jwt-validation/tree/main/doc/specification/security.adoc">Security Specification</a>
 */
@EnableTestLogger
@DisplayName("Tests TokenValidator security event counting")
class TokenValidatorSecurityEventTest {

    private static final String ISSUER = "Token-Test-testIssuer";
    private static final String AUDIENCE = "test-client";
    private static final String CLIENT_ID = "test-client";

    private TokenValidator tokenValidator;

    @BeforeEach
    void setUp() {
        // Create a JWKSKeyLoader with the default JWKS content
        String jwksContent = InMemoryJWKSFactory.createDefaultJwks();

        // Create issuer config
        IssuerConfig issuerConfig = IssuerConfig.builder()
                .issuer(ISSUER)
                .expectedAudience(AUDIENCE)
                .expectedClientId(CLIENT_ID)
                .jwksContent(jwksContent)
                .algorithmPreferences(new AlgorithmPreferences())
                .build();

        // Create validation factory
        tokenValidator = new TokenValidator(issuerConfig);
    }

    @Test
    @DisplayName("Should count empty validation events")
    void shouldCountEmptyTokenEvents() {
        // Get initial count
        long initialCount = tokenValidator.getSecurityEventCounter().getCount(SecurityEventCounter.EventType.TOKEN_EMPTY);

        // Process empty validation - expect exception
        TokenValidationException exception = assertThrows(TokenValidationException.class,
                () -> tokenValidator.createAccessToken(""),
                "Empty token should throw TokenValidationException");

        // Verify exception has the correct event type
        assertEquals(SecurityEventCounter.EventType.TOKEN_EMPTY, exception.getEventType(),
                "Exception should have TOKEN_EMPTY event type");

        // Verify count increased
        assertEquals(initialCount + 1, tokenValidator.getSecurityEventCounter().getCount(SecurityEventCounter.EventType.TOKEN_EMPTY));

        // Process another empty validation - expect exception
        exception = assertThrows(TokenValidationException.class,
                () -> tokenValidator.createRefreshToken("   "),
                "Empty token should throw TokenValidationException");

        // Verify exception has the correct event type
        assertEquals(SecurityEventCounter.EventType.TOKEN_EMPTY, exception.getEventType(),
                "Exception should have TOKEN_EMPTY event type");

        // Verify count increased again
        assertEquals(initialCount + 2, tokenValidator.getSecurityEventCounter().getCount(SecurityEventCounter.EventType.TOKEN_EMPTY));
    }

    @Test
    @DisplayName("Should count invalid JWT format events")
    void shouldCountFailedToDecodeJwtEvents() {
        // Get initial count
        long initialCount = tokenValidator.getSecurityEventCounter().getCount(SecurityEventCounter.EventType.INVALID_JWT_FORMAT);

        // Process invalid validation - expect exception
        TokenValidationException exception = assertThrows(TokenValidationException.class,
                () -> tokenValidator.createAccessToken("invalid-validation"),
                "Invalid token should throw TokenValidationException");

        // Verify exception has the correct event type
        assertEquals(SecurityEventCounter.EventType.INVALID_JWT_FORMAT, exception.getEventType(),
                "Exception should have INVALID_JWT_FORMAT event type");

        // Verify count increased
        assertEquals(initialCount + 1, tokenValidator.getSecurityEventCounter().getCount(SecurityEventCounter.EventType.INVALID_JWT_FORMAT));
    }

    @ParameterizedTest
    @TestTokenSource(value = TokenType.ACCESS_TOKEN)
    @DisplayName("Should count missing claim events")
    void shouldCountMissingClaimEvents(TestTokenHolder tokenHolder) {
        // Get initial count
        long initialCount = tokenValidator.getSecurityEventCounter().getCount(SecurityEventCounter.EventType.MISSING_CLAIM);

        // Remove the issuer claim
        tokenHolder.withoutClaim(ClaimName.ISSUER.getName());
        String token = tokenHolder.getRawToken();

        // Process token without issuer - expect exception
        TokenValidationException exception = assertThrows(TokenValidationException.class,
                () -> tokenValidator.createAccessToken(token),
                "Token without issuer should throw TokenValidationException");

        // Verify exception has the correct event type
        assertEquals(SecurityEventCounter.EventType.MISSING_CLAIM, exception.getEventType(),
                "Exception should have MISSING_CLAIM event type");

        // Verify count increased
        assertTrue(tokenValidator.getSecurityEventCounter().getCount(SecurityEventCounter.EventType.MISSING_CLAIM) > initialCount,
                "Missing claim count should increase");
    }

    @ParameterizedTest
    @TestTokenSource(value = TokenType.ACCESS_TOKEN)
    @DisplayName("Should count no issuer config events")
    void shouldCountNoIssuerConfigEvents(TestTokenHolder tokenHolder) {
        // Get initial count
        long initialCount = tokenValidator.getSecurityEventCounter().getCount(SecurityEventCounter.EventType.NO_ISSUER_CONFIG);

        // Set an unknown issuer
        tokenHolder.withClaim(ClaimName.ISSUER.getName(), ClaimValue.forPlainString("https://unknown-issuer.com"));
        String token = tokenHolder.getRawToken();

        // Process token with unknown issuer - expect exception
        TokenValidationException exception = assertThrows(TokenValidationException.class,
                () -> tokenValidator.createAccessToken(token),
                "Token with unknown issuer should throw TokenValidationException");

        // Verify exception has the correct event type
        assertEquals(SecurityEventCounter.EventType.NO_ISSUER_CONFIG, exception.getEventType(),
                "Exception should have NO_ISSUER_CONFIG event type");

        // Verify count increased
        assertEquals(initialCount + 1, tokenValidator.getSecurityEventCounter().getCount(SecurityEventCounter.EventType.NO_ISSUER_CONFIG));
    }

    @ParameterizedTest
    @TestTokenSource(value = TokenType.ACCESS_TOKEN)
    @DisplayName("Should count signature validation failed events")
    void shouldCountSignatureValidationFailedEvents(TestTokenHolder tokenHolder) {
        // Get initial count
        long initialCount = tokenValidator.getSecurityEventCounter().getCount(SecurityEventCounter.EventType.SIGNATURE_VALIDATION_FAILED);

        // Get the valid token and tamper with it
        String validToken = tokenHolder.getRawToken();
        String invalidToken = JwtTokenTamperingUtil.applyTamperingStrategy(
                validToken,
                JwtTokenTamperingUtil.TamperingStrategy.MODIFY_SIGNATURE_LAST_CHAR
        );

        // Process token with invalid signature - expect exception
        TokenValidationException exception = assertThrows(TokenValidationException.class,
                () -> tokenValidator.createAccessToken(invalidToken),
                "Token with invalid signature should throw TokenValidationException");

        // Verify exception has the correct event type
        assertEquals(SecurityEventCounter.EventType.SIGNATURE_VALIDATION_FAILED, exception.getEventType(),
                "Exception should have SIGNATURE_VALIDATION_FAILED event type");

        // Verify count increased
        assertTrue(tokenValidator.getSecurityEventCounter().getCount(SecurityEventCounter.EventType.SIGNATURE_VALIDATION_FAILED) > initialCount,
                "Signature validation failed count should increase");
    }

    @Test
    @DisplayName("Should reset security event counters")
    void shouldResetSecurityEventCounters() {
        // Generate some events - expect exceptions but we don't need to check them here
        assertThrows(TokenValidationException.class, () -> tokenValidator.createAccessToken(""));
        assertThrows(TokenValidationException.class, () -> tokenValidator.createAccessToken("invalid-validation"));

        // Verify counts are non-zero
        assertTrue(tokenValidator.getSecurityEventCounter().getCount(SecurityEventCounter.EventType.TOKEN_EMPTY) > 0);
        assertTrue(tokenValidator.getSecurityEventCounter().getCount(SecurityEventCounter.EventType.INVALID_JWT_FORMAT) > 0);

        // Reset counters
        tokenValidator.getSecurityEventCounter().reset();

        // Verify counts are zero
        assertEquals(0, tokenValidator.getSecurityEventCounter().getCount(SecurityEventCounter.EventType.TOKEN_EMPTY));
        assertEquals(0, tokenValidator.getSecurityEventCounter().getCount(SecurityEventCounter.EventType.INVALID_JWT_FORMAT));
    }
}
