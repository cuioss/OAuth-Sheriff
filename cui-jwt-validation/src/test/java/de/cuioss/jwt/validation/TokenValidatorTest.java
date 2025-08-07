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

import de.cuioss.jwt.validation.domain.claim.ClaimName;
import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.test.InMemoryJWKSFactory;
import de.cuioss.jwt.validation.test.TestTokenHolder;
import de.cuioss.jwt.validation.test.generator.TestTokenGenerators;
import de.cuioss.jwt.validation.test.junit.TestTokenSource;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for {@link TokenValidator}.
 * <p>
 * Verifies requirements:
 * <ul>
 *   <li><a href="https://github.com/cuioss/cui-jwt/blob/main/doc/Requirements.adoc#CUI-JWT-1.1">CUI-JWT-1.1: Token Structure</a></li>
 *   <li><a href="https://github.com/cuioss/cui-jwt/blob/main/doc/Requirements.adoc#CUI-JWT-1.2">CUI-JWT-1.2: Token Types</a></li>
 *   <li><a href="https://github.com/cuioss/cui-jwt/blob/main/doc/Requirements.adoc#CUI-JWT-1.3">CUI-JWT-1.3: Signature Validation</a></li>
 *   <li><a href="https://github.com/cuioss/cui-jwt/blob/main/doc/Requirements.adoc#CUI-JWT-3.1">CUI-JWT-3.1: Issuer Configuration</a></li>
 *   <li><a href="https://github.com/cuioss/cui-jwt/blob/main/doc/Requirements.adoc#CUI-JWT-3.2">CUI-JWT-3.2: Issuer Selection</a></li>
 *   <li><a href="https://github.com/cuioss/cui-jwt/blob/main/doc/Requirements.adoc#CUI-JWT-3.3">CUI-JWT-3.3: Issuer Validation</a></li>
 *   <li><a href="https://github.com/cuioss/cui-jwt/blob/main/doc/Requirements.adoc#CUI-JWT-5.1">CUI-JWT-5.1: Token Parsing Methods</a></li>
 *   <li><a href="https://github.com/cuioss/cui-jwt/blob/main/doc/Requirements.adoc#CUI-JWT-6.1">CUI-JWT-6.1: Configuration Flexibility</a></li>
 *   <li><a href="https://github.com/cuioss/cui-jwt/blob/main/doc/Requirements.adoc#CUI-JWT-7.1">CUI-JWT-7.1: Log Levels</a></li>
 *   <li><a href="https://github.com/cuioss/cui-jwt/blob/main/doc/Requirements.adoc#CUI-JWT-7.2">CUI-JWT-7.2: Log Content</a></li>
 *   <li><a href="https://github.com/cuioss/cui-jwt/blob/main/doc/Requirements.adoc#CUI-JWT-8.1">CUI-JWT-8.1: Token Size Limits</a></li>
 *   <li><a href="https://github.com/cuioss/cui-jwt/blob/main/doc/Requirements.adoc#CUI-JWT-8.2">CUI-JWT-8.2: Safe Parsing</a></li>
 * </ul>
 *
 * @author Oliver Wolff
 */
@EnableTestLogger
@EnableGeneratorController
class TokenValidatorTest {
    private TokenValidator tokenValidator;
    private IssuerConfig issuerConfig;

    private IssuerConfig createDefaultIssuerConfig() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        return tokenHolder.getIssuerConfig();
    }

    @BeforeEach
    void setUp() {
        issuerConfig = createDefaultIssuerConfig();
        tokenValidator = TokenValidator.builder().issuerConfig(issuerConfig).build();
    }

    @Nested
    class TokenCreationTests {

        @ParameterizedTest
        @TestTokenSource(value = TokenType.REFRESH_TOKEN, count = 3)
        @DisplayName("Create refresh token successfully")
        void shouldCreateRefreshToken(TestTokenHolder tokenHolder) {
            var token = tokenHolder.getRawToken();

            var parsedToken = tokenValidator.createRefreshToken(token);

            assertNotNull(parsedToken, "Parsed token should not be null");
            assertEquals(token, parsedToken.getRawToken(), "Raw token should match");
            assertNotNull(parsedToken.getClaims(), "Claims should not be null");
            assertFalse(parsedToken.getClaims().isEmpty(), "Claims should not be empty");
            assertTrue(parsedToken.getClaims().containsKey(ClaimName.SUBJECT.getName()), "Should contain 'sub' claim");
            assertTrue(parsedToken.getClaims().containsKey(ClaimName.ISSUER.getName()), "Should contain 'iss' claim");
        }

        @Test
        @DisplayName("Create refresh token with empty claims for non-JWT")
        void shouldCreateRefreshTokenWithEmptyClaimsForNonJwtToken() {
            var token = "not-a-jwt-validation";
            var parsedToken = tokenValidator.createRefreshToken(token);

            assertNotNull(parsedToken, "Parsed token should not be null");
            assertEquals(token, parsedToken.getRawToken(), "Raw token should match");
            assertNotNull(parsedToken.getClaims(), "Claims should not be null");
            assertTrue(parsedToken.getClaims().isEmpty(), "Claims should be empty for non-JWT");
        }

        @ParameterizedTest
        @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 2)
        @DisplayName("Fail access token validation with invalid issuer")
        void shouldFailAccessTokenValidationWithInvalidIssuer(TestTokenHolder tokenHolder) {
            tokenHolder.withClaim(ClaimName.ISSUER.getName(), ClaimValue.forPlainString("invalid-issuer"));
            var token = tokenHolder.getRawToken();

            assertThrows(TokenValidationException.class, () -> tokenValidator.createAccessToken(token),
                    "Should throw exception for invalid issuer");
        }

        @ParameterizedTest
        @TestTokenSource(value = TokenType.ID_TOKEN, count = 2)
        @DisplayName("Fail ID token validation with invalid issuer")
        void shouldFailIdTokenValidationWithInvalidIssuer(TestTokenHolder tokenHolder) {
            tokenHolder.withClaim(ClaimName.ISSUER.getName(), ClaimValue.forPlainString("invalid-issuer"));
            var token = tokenHolder.getRawToken();

            assertThrows(TokenValidationException.class, () -> tokenValidator.createIdToken(token),
                    "Should throw exception for invalid issuer");
        }
    }

    @Nested
    class TokenSizeValidationTests {

        @Test
        @DisplayName("Respect custom token size limits")
        void shouldRespectCustomTokenSizeLimits() {
            int customMaxSize = 1024;
            String largeToken = "a".repeat(customMaxSize + 1);

            ParserConfig customConfig = ParserConfig.builder()
                    .maxTokenSize(customMaxSize)
                    .build();
            var factory = TokenValidator.builder().parserConfig(customConfig).issuerConfig(issuerConfig).build();

            var exception = assertThrows(TokenValidationException.class,
                    () -> factory.createAccessToken(largeToken));

            assertEquals(SecurityEventCounter.EventType.TOKEN_SIZE_EXCEEDED, exception.getEventType(),
                    "Should indicate token size exceeded");
        }

        @ParameterizedTest
        @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 2)
        @DisplayName("Respect custom payload size limits")
        void shouldRespectCustomPayloadSizeLimits(TestTokenHolder tokenHolder) {
            ParserConfig customConfig = ParserConfig.builder()
                    .maxPayloadSize(100)
                    .build();
            var factory = TokenValidator.builder().parserConfig(customConfig).issuerConfig(issuerConfig).build();

            tokenHolder.withClaim("large-claim", ClaimValue.forPlainString("a".repeat(200)));
            String token = tokenHolder.getRawToken();

            var exception = assertThrows(TokenValidationException.class,
                    () -> factory.createAccessToken(token));

            assertEquals(SecurityEventCounter.EventType.DECODED_PART_SIZE_EXCEEDED, exception.getEventType(),
                    "Should indicate payload size exceeded");
        }
    }

    @Nested
    class TokenValidationErrorTests {

        @Test
        @DisplayName("Handle empty or blank token strings")
        void shouldHandleEmptyOrBlankTokenStrings() {
            var emptyException = assertThrows(TokenValidationException.class,
                    () -> tokenValidator.createAccessToken(""));
            assertEquals(SecurityEventCounter.EventType.TOKEN_EMPTY, emptyException.getEventType(),
                    "Should indicate empty token");

            var blankException = assertThrows(TokenValidationException.class,
                    () -> tokenValidator.createAccessToken("   "));
            assertEquals(SecurityEventCounter.EventType.TOKEN_EMPTY, blankException.getEventType(),
                    "Should indicate empty token");
        }

        @Test
        @DisplayName("Handle invalid token format")
        void shouldHandleInvalidTokenFormat() {
            var invalidToken = Generators.letterStrings(10, 20).next();

            var exception = assertThrows(TokenValidationException.class,
                    () -> tokenValidator.createAccessToken(invalidToken));

            assertEquals(SecurityEventCounter.EventType.INVALID_JWT_FORMAT, exception.getEventType(),
                    "Should indicate invalid JWT format");
        }

        @ParameterizedTest
        @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 2)
        @DisplayName("Handle unknown issuer")
        void shouldHandleUnknownIssuer(TestTokenHolder tokenHolder) {
            tokenHolder.withClaim(ClaimName.ISSUER.getName(), ClaimValue.forPlainString("https://unknown-issuer.com"));
            String token = tokenHolder.getRawToken();

            var exception = assertThrows(TokenValidationException.class,
                    () -> tokenValidator.createAccessToken(token));

            assertEquals(SecurityEventCounter.EventType.NO_ISSUER_CONFIG, exception.getEventType(),
                    "Should indicate no issuer config");
        }
    }

    @Nested
    class TokenLoggingTests {
        private static final String INVALID_TOKEN = "invalid.validation.string";
        private static final String EMPTY_TOKEN = "";

        @Test
        @DisplayName("Log warning when token is empty")
        void shouldLogWarningWhenTokenIsEmpty() {
            assertThrows(TokenValidationException.class,
                    () -> tokenValidator.createAccessToken(EMPTY_TOKEN));

            LogAsserts.assertLogMessagePresent(TestLogLevel.WARN, JWTValidationLogMessages.WARN.TOKEN_IS_EMPTY.format());
        }

        @Test
        @DisplayName("Log warning when token format is invalid")
        void shouldLogWarningWhenTokenFormatIsInvalid() {
            assertThrows(TokenValidationException.class,
                    () -> tokenValidator.createAccessToken(INVALID_TOKEN));

            LogAsserts.assertLogMessagePresent(TestLogLevel.WARN, JWTValidationLogMessages.WARN.FAILED_TO_DECODE_JWT.format());
        }

        @ParameterizedTest
        @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 2)
        @DisplayName("Log warning when token validation fails")
        void shouldLogWarningWhenTokenValidationFails(TestTokenHolder tokenHolder) {
            tokenHolder.withClaim(ClaimName.ISSUER.getName(), ClaimValue.forPlainString("unknown-issuer"));
            String token = tokenHolder.getRawToken();

            assertThrows(TokenValidationException.class,
                    () -> tokenValidator.createAccessToken(token));

            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "No configuration found for issuer");
        }

        @ParameterizedTest
        @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 2)
        @DisplayName("Log warning when token is missing claims")
        void shouldLogWarningWhenTokenIsMissingClaims(TestTokenHolder tokenHolder) {
            tokenHolder.withoutClaim(ClaimName.SCOPE.getName());
            String validToken = tokenHolder.getRawToken();

            var exception = assertThrows(TokenValidationException.class,
                    () -> tokenValidator.createAccessToken(validToken));

            assertEquals(SecurityEventCounter.EventType.MISSING_CLAIM, exception.getEventType(),
                    "Should indicate missing claim");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "missing required claim");
        }

        @ParameterizedTest
        @TestTokenSource(value = TokenType.ID_TOKEN, count = 2)
        @DisplayName("Log warning when ID token is missing claims")
        void shouldLogWarningWhenIdTokenIsMissingClaims(TestTokenHolder tokenHolder) {
            tokenHolder.withoutClaim(ClaimName.AUDIENCE.getName());
            String validToken = tokenHolder.getRawToken();

            var exception = assertThrows(TokenValidationException.class,
                    () -> tokenValidator.createIdToken(validToken));

            assertEquals(SecurityEventCounter.EventType.MISSING_CLAIM, exception.getEventType(),
                    "Should indicate missing claim");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "missing required claim");
        }

        @ParameterizedTest
        @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 2)
        @DisplayName("Log warning when key is not found")
        void shouldLogWarningWhenKeyIsNotFound(TestTokenHolder tokenHolder) {
            String token = tokenHolder.getRawToken();

            String issuer = TestTokenHolder.TEST_ISSUER;
            if (tokenHolder.getClaims().containsKey(ClaimName.ISSUER.getName())) {
                issuer = tokenHolder.getClaims().get(ClaimName.ISSUER.getName()).getOriginalString();
            }

            String jwksContent = InMemoryJWKSFactory.createValidJwksWithKeyId("different-key-id");

            IssuerConfig newIssuerConfig = IssuerConfig.builder()
                    .issuerIdentifier(issuer)
                    .jwksContent(jwksContent)
                    .expectedAudience(TestTokenHolder.TEST_AUDIENCE)
                    .expectedClientId(TestTokenHolder.TEST_CLIENT_ID)
                    .build();

            TokenValidator newTokenValidator = TokenValidator.builder().issuerConfig(newIssuerConfig).build();

            var exception = assertThrows(TokenValidationException.class,
                    () -> newTokenValidator.createAccessToken(token));

            assertEquals(SecurityEventCounter.EventType.KEY_NOT_FOUND, exception.getEventType(),
                    "Should indicate key not found");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "key");
        }

    }

}
