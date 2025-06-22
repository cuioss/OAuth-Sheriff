/**
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
import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.jwt.validation.TokenType;
import de.cuioss.jwt.validation.domain.claim.ClaimName;
import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import de.cuioss.jwt.validation.domain.token.TokenContent;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.test.TestTokenHolder;
import de.cuioss.jwt.validation.test.generator.TestTokenGenerators;
import de.cuioss.jwt.validation.test.junit.TestTokenSource;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for {@link TokenClaimValidator}.
 * <p>
 * Verifies requirements:
 * <ul>
 *   <li><a href="https://github.com/cuioss/cui-jwt/blob/main/doc/Requirements.adoc#CUI-JWT-1.3">CUI-JWT-1.3: Signature Validation</a></li>
 *   <li><a href="https://github.com/cuioss/cui-jwt/blob/main/doc/Requirements.adoc#CUI-JWT-2.1">CUI-JWT-2.1: Base Token Functionality</a></li>
 *   <li><a href="https://github.com/cuioss/cui-jwt/blob/main/doc/Requirements.adoc#CUI-JWT-3.1">CUI-JWT-3.1: Issuer Configuration</a></li>
 *   <li><a href="https://github.com/cuioss/cui-jwt/blob/main/doc/Requirements.adoc#CUI-JWT-3.3">CUI-JWT-3.3: Issuer Validation</a></li>
 *   <li><a href="https://github.com/cuioss/cui-jwt/blob/main/doc/Requirements.adoc#CUI-JWT-7.1">CUI-JWT-7.1: Log Levels</a></li>
 *   <li><a href="https://github.com/cuioss/cui-jwt/blob/main/doc/Requirements.adoc#CUI-JWT-7.3">CUI-JWT-7.3: Security Events</a></li>
 *   <li><a href="https://github.com/cuioss/cui-jwt/blob/main/doc/Requirements.adoc#CUI-JWT-8.4">CUI-JWT-8.4: Claims Validation</a></li>
 * </ul>
 * 
 * @author Oliver Wolff
 */
@EnableTestLogger
@EnableGeneratorController
class TokenClaimValidatorTest {

    private static final SecurityEventCounter SECURITY_EVENT_COUNTER = new SecurityEventCounter();

    private TokenClaimValidator createValidator(IssuerConfig issuerConfig) {
        return new TokenClaimValidator(issuerConfig, SECURITY_EVENT_COUNTER);
    }

    private IssuerConfig createDefaultIssuerConfig() {
        // Use TestTokenHolder's built-in configuration generation
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        return tokenHolder.getIssuerConfig();
    }

    @Nested
    class ConstructorTests {
        @Test
        @DisplayName("Create validator with all recommended elements")
        void shouldCreateValidatorWithAllRecommendedElements() {
            var issuerConfig = IssuerConfig.builder()
                    .issuer("test-issuer")
                    .expectedAudience(Set.of(TestTokenHolder.TEST_AUDIENCE))
                    .expectedClientId(TestTokenHolder.TEST_CLIENT_ID)
                    .build();

            TokenClaimValidator validator = createValidator(issuerConfig);

            assertNotNull(validator, "Validator should not be null");
            assertNotNull(validator.getExpectedAudience(), "Expected audience should not be null");
            assertNotNull(validator.getExpectedClientId(), "Expected client ID should not be null");
        }

        @Test
        @DisplayName("Log warning when missing expected audience")
        void shouldLogWarningWhenMissingExpectedAudience() {
            long initialCount = SECURITY_EVENT_COUNTER.getCount(SecurityEventCounter.EventType.MISSING_RECOMMENDED_ELEMENT);

            var issuerConfig = IssuerConfig.builder()
                    .issuer("test-issuer")
                    .expectedClientId(TestTokenHolder.TEST_CLIENT_ID)
                    .build();

            TokenClaimValidator validator = createValidator(issuerConfig);

            assertNotNull(validator, "Validator should not be null");
            assertTrue(validator.getExpectedAudience().isEmpty(), "Expected audience should be empty");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, JWTValidationLogMessages.WARN.MISSING_RECOMMENDED_ELEMENT.resolveIdentifierString());
            assertEquals(initialCount + 1, SECURITY_EVENT_COUNTER.getCount(SecurityEventCounter.EventType.MISSING_RECOMMENDED_ELEMENT));
        }

        @Test
        @DisplayName("Log warning when missing expected client ID")
        void shouldLogWarningWhenMissingExpectedClientId() {
            long initialCount = SECURITY_EVENT_COUNTER.getCount(SecurityEventCounter.EventType.MISSING_RECOMMENDED_ELEMENT);

            var issuerConfig = IssuerConfig.builder()
                    .issuer("test-issuer")
                    .expectedAudience(TestTokenHolder.TEST_AUDIENCE)
                    .build();

            TokenClaimValidator validator = createValidator(issuerConfig);

            assertNotNull(validator, "Validator should not be null");
            assertTrue(validator.getExpectedClientId().isEmpty(), "Expected client ID should be empty");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, JWTValidationLogMessages.WARN.MISSING_RECOMMENDED_ELEMENT.resolveIdentifierString());
            assertEquals(initialCount + 1, SECURITY_EVENT_COUNTER.getCount(SecurityEventCounter.EventType.MISSING_RECOMMENDED_ELEMENT));
        }

        @Test
        @DisplayName("Log warnings when missing all recommended elements")
        void shouldLogWarningsWhenMissingAllRecommendedElements() {
            long initialCount = SECURITY_EVENT_COUNTER.getCount(SecurityEventCounter.EventType.MISSING_RECOMMENDED_ELEMENT);

            var issuerConfig = IssuerConfig.builder()
                    .issuer("test-issuer")
                    .build();

            TokenClaimValidator validator = createValidator(issuerConfig);

            assertNotNull(validator, "Validator should not be null");
            assertTrue(validator.getExpectedAudience().isEmpty(), "Expected audience should be empty");
            assertTrue(validator.getExpectedClientId().isEmpty(), "Expected client ID should be empty");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, JWTValidationLogMessages.WARN.MISSING_RECOMMENDED_ELEMENT.resolveIdentifierString());
            assertEquals(initialCount + 2, SECURITY_EVENT_COUNTER.getCount(SecurityEventCounter.EventType.MISSING_RECOMMENDED_ELEMENT));
        }
    }

    @Nested
    class MandatoryClaimsValidationTests {
        @ParameterizedTest
        @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 2)
        @DisplayName("Validate token with all mandatory claims")
        void shouldValidateTokenWithAllMandatoryClaims(TestTokenHolder tokenHolder) {
            var validator = createValidator(tokenHolder.getIssuerConfig());

            TokenContent result = assertDoesNotThrow(() -> validator.validate(tokenHolder));

            assertNotNull(result, "Token content should not be null");
        }
    }

    @Nested
    class TokenTypeValidationTests {
        @Test
        @DisplayName("Validate access token type")
        void shouldValidateAccessTokenType() {
            var validator = createValidator(createDefaultIssuerConfig());
            TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
            tokenHolder.withClaim(ClaimName.AUTHORIZED_PARTY.getName(), ClaimValue.forPlainString(TestTokenHolder.TEST_CLIENT_ID));

            TokenContent result = assertDoesNotThrow(() -> validator.validate(tokenHolder));

            assertNotNull(result, "Token content should not be null");
            assertEquals(TokenType.ACCESS_TOKEN, result.getTokenType(), "Token type should be ACCESS_TOKEN");
        }

        @Test
        @DisplayName("Validate ID token type")
        void shouldValidateIdTokenType() {
            var validator = createValidator(createDefaultIssuerConfig());
            TestTokenHolder tokenHolder = TestTokenGenerators.idTokens().next();

            TokenContent result = assertDoesNotThrow(() -> validator.validate(tokenHolder));

            assertNotNull(result, "Token content should not be null");
            assertEquals(TokenType.ID_TOKEN, result.getTokenType(), "Token type should be ID_TOKEN");
        }
    }
}