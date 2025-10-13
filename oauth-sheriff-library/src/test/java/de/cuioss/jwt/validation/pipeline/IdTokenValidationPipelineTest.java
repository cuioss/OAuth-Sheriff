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
package de.cuioss.sheriff.oauth.library.pipeline;

import de.cuioss.sheriff.oauth.library.JWTValidationLogMessages;
import de.cuioss.sheriff.oauth.library.TokenType;
import de.cuioss.sheriff.oauth.library.TokenValidator;
import de.cuioss.sheriff.oauth.library.domain.token.IdTokenContent;
import de.cuioss.sheriff.oauth.library.exception.TokenValidationException;
import de.cuioss.sheriff.oauth.library.security.SecurityEventCounter;
import de.cuioss.sheriff.oauth.library.test.TestTokenHolder;
import de.cuioss.sheriff.oauth.library.test.generator.ClaimControlParameter;
import de.cuioss.sheriff.oauth.library.test.generator.TestTokenGenerators;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link IdTokenValidationPipeline} via {@link TokenValidator}.
 * <p>
 * Tests error paths in ID token validation that may not be exercised by other tests.
 * Uses TokenValidator as the entry point for more realistic testing.
 */
@EnableTestLogger
@DisplayName("IdTokenValidationPipeline Tests")
class IdTokenValidationPipelineTest {

    private TokenValidator tokenValidator;

    @BeforeEach
    void setUp() {
        // Create a token validator with default configuration
        TestTokenHolder tokenHolder = TestTokenGenerators.idTokens().next();
        tokenValidator = TokenValidator.builder()
                .issuerConfig(tokenHolder.getIssuerConfig())
                .build();
    }

    @Test
    @DisplayName("Should successfully validate a valid ID token")
    void shouldValidateValidIdToken() {
        // Given
        TestTokenHolder tokenHolder = TestTokenGenerators.idTokens().next();
        String tokenString = tokenHolder.getRawToken();

        // When
        IdTokenContent result = tokenValidator.createIdToken(tokenString);

        // Then
        assertNotNull(result);
        assertEquals(tokenString, result.getRawToken());
    }

    @Test
    @DisplayName("Should throw TokenValidationException when issuer claim is missing")
    void shouldThrowExceptionWhenIssuerClaimIsMissing() {
        // Given - create a token without issuer claim
        ClaimControlParameter params = ClaimControlParameter.builder()
                .missingIssuer(true)
                .build();
        TestTokenHolder tokenHolder = new TestTokenHolder(TokenType.ID_TOKEN, params);
        String tokenString = tokenHolder.getRawToken();

        // When/Then
        TokenValidationException exception = assertThrows(
                TokenValidationException.class,
                () -> tokenValidator.createIdToken(tokenString),
                "Should throw TokenValidationException for missing issuer"
        );

        // Verify exception details
        assertEquals(SecurityEventCounter.EventType.MISSING_CLAIM, exception.getEventType());
        assertTrue(exception.getMessage().contains("Missing required issuer (iss) claim"));

        // Verify logging
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                JWTValidationLogMessages.WARN.MISSING_CLAIM.resolveIdentifierString());
    }

    @Test
    @DisplayName("Should throw TokenValidationException when issuer claim is missing - alternative token")
    void shouldThrowExceptionForMissingIssuerWithDifferentToken() {
        // Given - create another token without issuer claim
        ClaimControlParameter params = ClaimControlParameter.builder()
                .missingIssuer(true)
                .build();
        TestTokenHolder tokenHolder = new TestTokenHolder(TokenType.ID_TOKEN, params);
        String tokenString = tokenHolder.getRawToken();

        // When/Then
        assertThrows(
                TokenValidationException.class,
                () -> tokenValidator.createIdToken(tokenString),
                "Should consistently throw TokenValidationException for missing issuer"
        );
    }
}
