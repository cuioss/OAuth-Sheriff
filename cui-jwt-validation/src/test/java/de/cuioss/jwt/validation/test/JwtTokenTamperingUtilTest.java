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
package de.cuioss.jwt.validation.test;

import de.cuioss.jwt.validation.IssuerConfig;
import de.cuioss.jwt.validation.ParserConfig;
import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.domain.token.IdTokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.security.SignatureAlgorithmPreferences;
import de.cuioss.jwt.validation.test.JwtTokenTamperingUtil.TamperingStrategy;
import de.cuioss.jwt.validation.test.generator.TestTokenGenerators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for {@link JwtTokenTamperingUtil}.
 * Demonstrates how to use the utility with TestTokenGenerators.
 */
@EnableGeneratorController
@EnableTestLogger
class JwtTokenTamperingUtilTest {

    private static final String ISSUER = "Token-Test-testIssuer";

    private TokenValidator tokenValidator;

    @BeforeEach
    void setUp() {
        // Create validation factory with default configuration
        ParserConfig config = ParserConfig.builder().build();
        IssuerConfig issuerConfig = IssuerConfig.builder()
                .issuerIdentifier(ISSUER)
                .expectedAudience(TestTokenHolder.TEST_AUDIENCE)
                .expectedClientId(TestTokenHolder.TEST_CLIENT_ID)
                .jwksContent(InMemoryJWKSFactory.createDefaultJwks())
                .algorithmPreferences(new SignatureAlgorithmPreferences())
                .build();
        tokenValidator = new TokenValidator(config, issuerConfig);

    }

    @Test
    @DisplayName("Should validate untampered access token")
    void shouldValidateUntamperedAccessToken() {

        String token = TestTokenGenerators.accessTokens().next().getRawToken();
        AccessTokenContent result = tokenValidator.createAccessToken(token);
        assertNotNull(result, "Untampered token should be valid");
    }

    @Test
    @DisplayName("Should validate untampered ID-Token")
    void shouldValidateUntamperedIdToken() {

        String token = TestTokenGenerators.idTokens().next().getRawToken();
        IdTokenContent result = tokenValidator.createIdToken(token);
        assertNotNull(result, "Untampered token should be valid");
    }

    @ParameterizedTest
    @EnumSource(TamperingStrategy.class)
    @DisplayName("Should reject tampered access token")
    void shouldRejectTamperedAccessToken(TamperingStrategy strategy) {

        String originalToken = TestTokenGenerators.accessTokens().next().getRawToken();
        String tamperedToken = JwtTokenTamperingUtil.applyTamperingStrategy(originalToken, strategy);

        // Verify that the validation was actually tampered
        assertNotEquals(originalToken, tamperedToken,
                "Token should be tampered using strategy: " + strategy.getDescription());
        TokenValidationException exception = assertThrows(TokenValidationException.class,
                () -> tokenValidator.createAccessToken(tamperedToken),
                "Tampered token should be rejected. Strategy: " + strategy.getDescription());

        // Verify the exception has a valid event type
        assertNotNull(exception.getEventType(),
                "Exception should have an event type. Strategy: " + strategy.getDescription());
    }

    @ParameterizedTest
    @EnumSource(TamperingStrategy.class)
    @DisplayName("Should reject tampered ID-Token")
    void shouldRejectTamperedIdToken(TamperingStrategy strategy) {

        String originalToken = TestTokenGenerators.idTokens().next().getRawToken();
        String tamperedToken = JwtTokenTamperingUtil.applyTamperingStrategy(originalToken, strategy);

        // Verify that the validation was actually tampered
        assertNotEquals(originalToken, tamperedToken,
                "Token should be tampered using strategy: " + strategy.getDescription());
        TokenValidationException exception = assertThrows(TokenValidationException.class,
                () -> tokenValidator.createIdToken(tamperedToken),
                "Tampered token should be rejected. Strategy: " + strategy.getDescription());

        // Verify the exception has a valid event type
        assertNotNull(exception.getEventType(),
                "Exception should have an event type. Strategy: " + strategy.getDescription());
    }

    @Test
    @DisplayName("Should apply all tampering strategies to a validation")
    void shouldApplyAllTamperingStrategiesToToken() {

        String originalToken = TestTokenGenerators.accessTokens().next().getRawToken();
        for (TamperingStrategy strategy : TamperingStrategy.values()) {
            String tamperedToken = JwtTokenTamperingUtil.applyTamperingStrategy(originalToken, strategy);
            assertNotEquals(originalToken, tamperedToken,
                    "Token should be tampered using strategy: " + strategy.getDescription());
        }
    }
}
