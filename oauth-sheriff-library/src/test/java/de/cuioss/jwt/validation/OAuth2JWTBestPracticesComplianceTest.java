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
package de.cuioss.sheriff.oauth.library;

import de.cuioss.sheriff.oauth.library.domain.claim.ClaimName;
import de.cuioss.sheriff.oauth.library.domain.claim.ClaimValue;
import de.cuioss.sheriff.oauth.library.domain.context.ValidationContext;
import de.cuioss.sheriff.oauth.library.domain.token.AccessTokenContent;
import de.cuioss.sheriff.oauth.library.exception.TokenValidationException;
import de.cuioss.sheriff.oauth.library.pipeline.validator.TokenSignatureValidator;
import de.cuioss.sheriff.oauth.library.security.SecurityEventCounter;
import de.cuioss.sheriff.oauth.library.security.SignatureAlgorithmPreferences;
import de.cuioss.sheriff.oauth.library.test.InMemoryJWKSFactory;
import de.cuioss.sheriff.oauth.library.test.JwtTokenTamperingUtil;
import de.cuioss.sheriff.oauth.library.test.TestTokenHolder;
import de.cuioss.sheriff.oauth.library.test.generator.TestTokenGenerators;
import de.cuioss.sheriff.oauth.library.test.junit.TestTokenSource;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests compliance with the OAuth 2.0 JWT Best Current Practices.
 * <p>
 * Verifies requirements:
 * <ul>
 *   <li>CUI-JWT-3.1: OAuth2 JWT Best Practices</li>
 *   <li>CUI-JWT-3.2: Audience Validation</li>
 *   <li>CUI-JWT-3.3: Issuer Validation</li>
 *   <li>CUI-JWT-6.1: Token Size Validation</li>
 * </ul>
 *
 * @author Oliver Wolff
 * @see <a href="https://datatracker.ietf.org/doc/html/draft-ietf-oauth-jwt-bcp-09">OAuth 2.0 JWT Best Current Practices</a>
 */
@EnableGeneratorController
@DisplayName("OAuth 2.0 JWT Best Practices Compliance Tests")
class OAuth2JWTBestPracticesComplianceTest {
    private TokenValidator tokenValidator;
    private ValidationContext validationContext;

    @BeforeEach
    void setUp() {
        // Get the default JWKS content
        String jwksContent = InMemoryJWKSFactory.createDefaultJwks();

        // Create issuer config with explicit audience validation
        IssuerConfig issuerConfig = IssuerConfig.builder()
                .issuerIdentifier(TestTokenHolder.TEST_ISSUER)
                .expectedAudience(TestTokenHolder.TEST_AUDIENCE)
                .expectedClientId(TestTokenHolder.TEST_CLIENT_ID)
                .jwksContent(jwksContent)
                .build();

        // Create validation factory
        tokenValidator = TokenValidator.builder().issuerConfig(issuerConfig).build();

        // Initialize validation context
        validationContext = new ValidationContext(60);
    }

    @Nested
    @DisplayName("Section 3.1: Validation of Audience")
    class AudienceValidationTests {

        @Test
        @DisplayName("3.1: Validate audience claim")
        void shouldValidateAudienceClaim() {

            String token = TestTokenGenerators.accessTokens().next().getRawToken();
            AccessTokenContent result = tokenValidator.createAccessToken(token);
            assertNotNull(result, "Token should be parsed successfully");
            assertTrue(result.getAudience().isPresent(), "Audience claim should be present");
            assertTrue(result.getAudience().get().contains(TestTokenHolder.TEST_AUDIENCE),
                    "Audience claim should contain the expected value");
        }

        @Test
        @DisplayName("3.1: Reject validation with incorrect audience")
        void shouldRejectTokenWithIncorrectAudience() {

            // Create a token with wrong audience
            TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
            tokenHolder.withAudience(List.of("wrong-audience"));
            String token = tokenHolder.getRawToken();
            TokenValidationException exception = assertThrows(TokenValidationException.class,
                    () -> tokenValidator.createAccessToken(token),
                    "Token with incorrect audience should be rejected");

            // Verify the exception has the correct event type
            assertEquals(SecurityEventCounter.EventType.AUDIENCE_MISMATCH, exception.getEventType(),
                    "Exception should have AUDIENCE_MISMATCH event type");
        }
    }

    @Nested
    @DisplayName("Section 3.2: Validation of Issuer")
    class IssuerValidationTests {

        @Test
        @DisplayName("3.2: Validate issuer claim")
        void shouldValidateIssuerClaim() {

            String token = TestTokenGenerators.accessTokens().next().getRawToken();
            AccessTokenContent result = tokenValidator.createAccessToken(token);
            assertNotNull(result, "Token should be parsed successfully");
            assertEquals(TestTokenHolder.TEST_ISSUER, result.getIssuer(),
                    "Issuer claim should match the expected value");
        }

        @Test
        @DisplayName("3.2: Reject validation with incorrect issuer")
        void shouldRejectTokenWithIncorrectIssuer() {

            String wrongIssuer = "https://wrong-issuer.com";

            // Create a token with wrong issuer using TestTokenGenerators and withClaim
            var tokenHolder = TestTokenGenerators.accessTokens().next()
                    .withClaim(ClaimName.ISSUER.getName(), ClaimValue.forPlainString(wrongIssuer));

            String token = tokenHolder.getRawToken();
            TokenValidationException exception = assertThrows(TokenValidationException.class,
                    () -> tokenValidator.createAccessToken(token),
                    "Token with incorrect issuer should be rejected");

            // Verify the exception has the correct event type
            assertEquals(SecurityEventCounter.EventType.NO_ISSUER_CONFIG, exception.getEventType(),
                    "Exception should have NO_ISSUER_CONFIG event type");
        }
    }

    @Nested
    @DisplayName("Section 3.3: Validation of Signature")
    @EnableTestLogger(trace = TokenSignatureValidator.class)
    class SignatureValidationTests {

        @Test
        @DisplayName("3.3: Validate validation signature")
        void shouldValidateTokenSignature() {

            String token = TestTokenGenerators.accessTokens().next().getRawToken();
            AccessTokenContent result = tokenValidator.createAccessToken(token);
            assertNotNull(result, "Token with valid signature should be parsed successfully");
        }

        @DisplayName("3.3b: Reject access-validation with invalid signature")
        @ParameterizedTest
        @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 50)
        void shouldRejectAccessTokenWithInvalidSignature(TestTokenHolder tokenHolder) {
            String token = tokenHolder.getRawToken();
            // Tamper with the signature using a specific strategy that modifies the signature
            String tamperedToken = JwtTokenTamperingUtil.applyTamperingStrategy(
                    token,
                    JwtTokenTamperingUtil.TamperingStrategy.MODIFY_SIGNATURE_LAST_CHAR
            );

            assertNotEquals(tamperedToken, token, "Token should be tampered");

            TokenValidator validator = TokenValidator.builder().issuerConfig(tokenHolder.getIssuerConfig()).build();

            TokenValidationException exception = assertThrows(TokenValidationException.class,
                    () -> validator.createAccessToken(tamperedToken),
                    "Token with invalid signature should be rejected, offending validation: " + tamperedToken);

            // Verify the exception has the correct event type
            assertEquals(SecurityEventCounter.EventType.SIGNATURE_VALIDATION_FAILED, exception.getEventType(),
                    "Exception should have SIGNATURE_VALIDATION_FAILED event type");
        }

        @DisplayName("3.3b: Reject id-validation with invalid signature")
        @ParameterizedTest
        @TestTokenSource(value = TokenType.ID_TOKEN, count = 50)
        void shouldRejectIDTokenWithInvalidSignature(TestTokenHolder tokenHolder) {
            String token = tokenHolder.getRawToken();

            // Tamper with the signature using a specific strategy that modifies the signature
            String tamperedToken = JwtTokenTamperingUtil.applyTamperingStrategy(
                    token,
                    JwtTokenTamperingUtil.TamperingStrategy.MODIFY_SIGNATURE_LAST_CHAR
            );

            assertNotEquals(tamperedToken, token, "Token should be tampered");
            TokenValidator validator = TokenValidator.builder().issuerConfig(tokenHolder.getIssuerConfig()).build();
            TokenValidationException exception = assertThrows(TokenValidationException.class,
                    () -> validator.createIdToken(tamperedToken),
                    "Token with invalid signature should be rejected, offending validation: " + tamperedToken);

            // Verify the exception has the correct event type
            assertEquals(SecurityEventCounter.EventType.SIGNATURE_VALIDATION_FAILED, exception.getEventType(),
                    "Exception should have SIGNATURE_VALIDATION_FAILED event type");
        }
    }

    @Nested
    @DisplayName("Section 3.8: Token Lifetimes")
    class TokenLifetimeTests {

        @Test
        @DisplayName("3.8: Validate validation expiration")
        void shouldValidateTokenExpiration() {

            String token = TestTokenGenerators.accessTokens().next().getRawToken();
            AccessTokenContent result = tokenValidator.createAccessToken(token);
            assertNotNull(result, "Token should be parsed successfully");
            assertNotNull(result.getExpirationTime(),
                    "Expiration time claim should be present");
            assertFalse(result.isExpired(validationContext),
                    "Token should not be expired");
        }

        @Test
        @DisplayName("3.8: Reject expired validation")
        void shouldRejectExpiredToken() {

            Instant expiredTime = Instant.now().minus(1, ChronoUnit.HOURS);
            OffsetDateTime expiredDateTime = OffsetDateTime.ofInstant(expiredTime, ZoneId.systemDefault());

            // Create token using TestTokenHolder
            TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();

            // Set expired expiration time
            tokenHolder.withClaim(ClaimName.EXPIRATION.getName(),
                    ClaimValue.forDateTime(String.valueOf(expiredDateTime.toEpochSecond()), expiredDateTime));

            String token = tokenHolder.getRawToken();
            TokenValidationException exception = assertThrows(TokenValidationException.class,
                    () -> tokenValidator.createAccessToken(token),
                    "Expired token should be rejected");

            // Verify the exception has the correct event type
            assertEquals(SecurityEventCounter.EventType.TOKEN_EXPIRED, exception.getEventType(),
                    "Exception should have TOKEN_EXPIRED event type");
        }
    }

    @Nested
    @DisplayName("Section 3.11: Maximum Token Size")
    class TokenSizeTests {

        @Test
        @DisplayName("3.11: Validate validation size limits")
        void shouldRespectTokenSizeLimits() {

            int customMaxSize = 1024;
            String largeToken = "a".repeat(customMaxSize + 1);

            // Create TokenValidator with custom validation size limits
            ParserConfig customConfig = ParserConfig.builder()
                    .maxTokenSize(customMaxSize)
                    .build();
            var factory = TokenValidator.builder()
                    .parserConfig(customConfig)
                    .issuerConfig(IssuerConfig.builder()
                            .issuerIdentifier("test-issuer")
                            .expectedAudience(TestTokenHolder.TEST_AUDIENCE)
                            .expectedClientId(TestTokenHolder.TEST_CLIENT_ID)
                            .jwksContent(InMemoryJWKSFactory.createDefaultJwks())
                            .algorithmPreferences(new SignatureAlgorithmPreferences())
                            .build())
                    .build();
            TokenValidationException exception = assertThrows(TokenValidationException.class,
                    () -> factory.createAccessToken(largeToken),
                    "Token exceeding max size should be rejected");

            // Verify the exception has the correct event type
            assertEquals(SecurityEventCounter.EventType.TOKEN_SIZE_EXCEEDED, exception.getEventType(),
                    "Exception should have TOKEN_SIZE_EXCEEDED event type");
        }

        @Test
        @DisplayName("3.11: Default validation size limit should be 8KB")
        void defaultTokenSizeLimitShouldBe8KB() {

            ParserConfig config = ParserConfig.builder().build();
            assertEquals(8192, config.getMaxTokenSize(),
                    "Default validation size limit should be 8KB (8192 bytes)");
        }
    }
}
