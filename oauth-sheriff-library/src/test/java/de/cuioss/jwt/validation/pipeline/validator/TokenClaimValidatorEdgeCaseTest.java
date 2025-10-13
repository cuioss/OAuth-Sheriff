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
package de.cuioss.sheriff.oauth.library.pipeline.validator;

import de.cuioss.sheriff.oauth.library.IssuerConfig;
import de.cuioss.sheriff.oauth.library.JWTValidationLogMessages;
import de.cuioss.sheriff.oauth.library.domain.claim.ClaimName;
import de.cuioss.sheriff.oauth.library.domain.claim.ClaimValue;
import de.cuioss.sheriff.oauth.library.domain.context.ValidationContext;
import de.cuioss.sheriff.oauth.library.domain.token.TokenContent;
import de.cuioss.sheriff.oauth.library.exception.TokenValidationException;
import de.cuioss.sheriff.oauth.library.jwks.JwksType;
import de.cuioss.sheriff.oauth.library.jwks.key.JWKSKeyLoader;
import de.cuioss.sheriff.oauth.library.jwks.key.KeyInfo;
import de.cuioss.sheriff.oauth.library.pipeline.DecodedJwt;
import de.cuioss.sheriff.oauth.library.pipeline.NonValidatingJwtParser;
import de.cuioss.sheriff.oauth.library.security.JwkAlgorithmPreferences;
import de.cuioss.sheriff.oauth.library.security.SecurityEventCounter;
import de.cuioss.sheriff.oauth.library.security.SignatureAlgorithmPreferences;
import de.cuioss.sheriff.oauth.library.test.TestTokenHolder;
import de.cuioss.sheriff.oauth.library.test.generator.ClaimControlParameter;
import de.cuioss.sheriff.oauth.library.test.generator.TestTokenGenerators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import lombok.NonNull;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Edge case tests for {@link TokenClaimValidator}.
 * This class focuses on testing edge cases around validation expiration, clock skew,
 * and network failures.
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("Tests TokenClaimValidator edge cases")
class TokenClaimValidatorEdgeCaseTest {
    private final SecurityEventCounter securityEventCounter = new SecurityEventCounter();
    private final ValidationContext validationContext = new ValidationContext(60);

    // Helper method to create a TokenClaimValidator with the shared SecurityEventCounter
    private TokenClaimValidator createValidator(IssuerConfig issuerConfig) {
        return new TokenClaimValidator(issuerConfig, securityEventCounter);
    }

    @Nested
    @DisplayName("Token Expiration Edge Cases")
    class TokenExpirationEdgeCaseTests {

        @Test
        @DisplayName("Should validate token that is about to expire")
        void shouldValidateTokenThatIsAboutToExpire() {
            // Given a validator
            var issuerConfig = IssuerConfig.builder()
                    .issuerIdentifier("test-issuer")
                    .expectedAudience(TestTokenHolder.TEST_AUDIENCE)
                    .expectedClientId(TestTokenHolder.TEST_CLIENT_ID)
                    .jwksContent("{\"keys\":[]}")
                    .build();
            var validator = createValidator(issuerConfig);

            // When validating a token that is about to expire (5 seconds from now)
            TokenContent tokenAboutToExpire = createTokenWithExpirationTime(OffsetDateTime.now().plusSeconds(5));

            // Then the validation should pass (no exception thrown)
            TokenContent result = validator.validate(tokenAboutToExpire, validationContext);
            // If we get here, the validation passed
            assertFalse(result.isExpired(validationContext), "Token should be valid when about to expire but not yet expired");
        }

        @Test
        @DisplayName("Should fail validation for token that has just expired")
        void shouldFailValidationForTokenThatHasJustExpired() {
            // Given a validator
            var issuerConfig = IssuerConfig.builder()
                    .issuerIdentifier("test-issuer")
                    .expectedAudience(TestTokenHolder.TEST_AUDIENCE)
                    .expectedClientId(TestTokenHolder.TEST_CLIENT_ID)
                    .jwksContent("{\"keys\":[]}")
                    .build();
            var validator = createValidator(issuerConfig);

            // When validating a token that has just expired (5 seconds ago)
            TokenContent tokenJustExpired = createTokenWithExpirationTime(OffsetDateTime.now().minusSeconds(5));

            // Then the validation should fail with a TokenValidationException
            assertThrows(TokenValidationException.class, () -> validator.validate(tokenJustExpired, validationContext),
                    "Token should be invalid when just expired");

            // Verify that the appropriate warning is logged
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, JWTValidationLogMessages.WARN.TOKEN_EXPIRED.resolveIdentifierString());
        }
    }

    @Nested
    @DisplayName("Not Before Time Edge Cases")
    class NotBeforeTimeEdgeCaseTests {

        @Test
        @DisplayName("Should validate token with not before time in the past")
        void shouldValidateTokenWithNotBeforeTimeInThePast() {
            // Given a validator
            var issuerConfig = IssuerConfig.builder()
                    .issuerIdentifier("test-issuer")
                    .expectedAudience(TestTokenHolder.TEST_AUDIENCE)
                    .expectedClientId(TestTokenHolder.TEST_CLIENT_ID)
                    .jwksContent("{\"keys\":[]}")
                    .build();
            var validator = createValidator(issuerConfig);

            // When validating a token with a not before time in the past
            TokenContent tokenWithPastNotBefore = createTokenWithNotBeforeTime(OffsetDateTime.now().minusMinutes(5));

            // Then the validation should pass (no exception thrown)
            TokenContent result = validator.validate(tokenWithPastNotBefore, validationContext);
            // If we get here, the validation passed
            assertNotNull(result, "Token should be valid with not before time in the past");
        }

        @Test
        @DisplayName("Should validate token with not before time slightly in the future (within clock skew)")
        void shouldValidateTokenWithNotBeforeTimeSlightlyInTheFuture() {
            // Given a validator
            var issuerConfig = IssuerConfig.builder()
                    .issuerIdentifier("test-issuer")
                    .expectedAudience(TestTokenHolder.TEST_AUDIENCE)
                    .expectedClientId(TestTokenHolder.TEST_CLIENT_ID)
                    .jwksContent("{\"keys\":[]}")
                    .build();
            var validator = createValidator(issuerConfig);

            // When validating a token with a not before time slightly in the future (30 seconds)
            // This should be within the allowed clock skew (60 seconds)
            TokenContent tokenWithFutureNotBefore = createTokenWithNotBeforeTime(OffsetDateTime.now().plusSeconds(30));

            // Then the validation should pass (no exception thrown)
            TokenContent result = validator.validate(tokenWithFutureNotBefore, validationContext);
            // If we get here, the validation passed
            assertNotNull(result, "Token should be valid with not before time slightly in the future (within clock skew)");
        }

        @Test
        @DisplayName("Should fail validation for token with not before time far in the future (beyond clock skew)")
        void shouldFailValidationForTokenWithNotBeforeTimeFarInTheFuture() {
            // Given a validator
            var issuerConfig = IssuerConfig.builder()
                    .issuerIdentifier("test-issuer")
                    .expectedAudience(TestTokenHolder.TEST_AUDIENCE)
                    .expectedClientId(TestTokenHolder.TEST_CLIENT_ID)
                    .jwksContent("{\"keys\":[]}")
                    .build();
            var validator = createValidator(issuerConfig);

            // When validating a token with a not before time far in the future (90 seconds)
            // This should be beyond the allowed clock skew (60 seconds)
            TokenContent tokenWithFarFutureNotBefore = createTokenWithNotBeforeTime(OffsetDateTime.now().plusSeconds(90));

            // Then the validation should fail with a TokenValidationException
            assertThrows(TokenValidationException.class, () -> validator.validate(tokenWithFarFutureNotBefore, validationContext),
                    "Token should be invalid with not before time far in the future (beyond clock skew)");

            // Verify that the appropriate warning is logged
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, JWTValidationLogMessages.WARN.TOKEN_NBF_FUTURE.resolveIdentifierString());
        }
    }

    @Nested
    @DisplayName("Network Failure Simulation Tests")
    class NetworkFailureSimulationTests {

        @Test
        @DisplayName("Should handle network failures during key retrieval")
        void shouldHandleNetworkFailuresDuringKeyRetrieval() {
            // This test simulates a network failure during key retrieval
            // by using a JwksKeyLoader that throws an exception

            // Given an IssuerConfig with empty JWKS content
            var issuerConfig = IssuerConfig.builder()
                    .issuerIdentifier("test-issuer")
                    .expectedAudience(TestTokenHolder.TEST_AUDIENCE)
                    .expectedClientId(TestTokenHolder.TEST_CLIENT_ID)
                    .jwksContent("{}")  // Empty JWKS content
                    .build();

            // Initialize the JwksLoader
            issuerConfig.initSecurityEventCounter(securityEventCounter);

            // Create a TokenSignatureValidator with a custom JwksLoader that simulates network failure
            var signatureValidator = new TokenSignatureValidator(new FailingJwksKeyLoader(), securityEventCounter, new SignatureAlgorithmPreferences());

            // Create a valid validation
            TokenContent validToken = createValidToken();
            // Use NonValidatingJwtParser to decode the raw token
            DecodedJwt decodedJwt = NonValidatingJwtParser.builder()
                    .securityEventCounter(securityEventCounter)
                    .build()
                    .decode(validToken.getRawToken());

            // When validating the signature, it should throw a TokenValidationException
            assertThrows(TokenValidationException.class, () -> signatureValidator.validateSignature(decodedJwt),
                    "Signature validation should throw an exception when network error occurs");
        }
    }

    /**
     * Creates a validation with a specific expiration time.
     *
     * @param expirationTime the expiration time to set
     * @return a TokenContent with the specified expiration time
     */
    private TokenContent createTokenWithExpirationTime(OffsetDateTime expirationTime) {
        // Create a valid validation first
        TokenContent validToken = createValidToken();

        // Create a new claims map with the modified expiration time
        Map<String, ClaimValue> claims = new HashMap<>(validToken.getClaims());
        claims.put(ClaimName.EXPIRATION.getName(), ClaimValue.forDateTime(
                String.valueOf(expirationTime.toEpochSecond()), expirationTime));

        // Create a custom TokenContent with the modified claims
        return new CustomTokenContent(validToken, claims);
    }

    /**
     * Creates a validation with a specific not-before time.
     *
     * @param notBeforeTime the not-before time to set
     * @return a TokenContent with the specified not-before time
     */
    private TokenContent createTokenWithNotBeforeTime(OffsetDateTime notBeforeTime) {
        // Create a valid validation first
        TokenContent validToken = createValidToken();

        // Create a new claims map with the modified not-before time
        Map<String, ClaimValue> claims = new HashMap<>(validToken.getClaims());
        claims.put(ClaimName.NOT_BEFORE.getName(), ClaimValue.forDateTime(
                String.valueOf(notBeforeTime.toEpochSecond()), notBeforeTime));

        // Create a custom TokenContent with the modified claims
        return new CustomTokenContent(validToken, claims);
    }

    /**
     * Creates a valid validation using the TestTokenGenerators factory.
     *
     * @return a valid TokenContent
     */
    private TokenContent createValidToken() {
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next();
        // Set the authorized party to match the expected client ID
        tokenHolder.withClaim("azp", ClaimValue.forPlainString(TestTokenHolder.TEST_CLIENT_ID));
        return tokenHolder;
    }

    /**
     * Custom TokenContent implementation that allows overriding claims.
     */
    private static class CustomTokenContent extends TestTokenHolder {
        private final Map<String, ClaimValue> customClaims;

        public CustomTokenContent(TokenContent original, Map<String, ClaimValue> customClaims) {
            super(original.getTokenType(), ClaimControlParameter.defaultForTokenType(original.getTokenType()));
            this.customClaims = customClaims;
        }

        @Override
        public @NonNull Map<String, ClaimValue> getClaims() {
            return customClaims;
        }

        @Override
        public String getRawToken() {
            return super.getRawToken();
        }
    }

    /**
     * A JwksKeyLoader implementation that simulates network failures.
     */
    private static class FailingJwksKeyLoader extends JWKSKeyLoader {
        public FailingJwksKeyLoader() {
            super("{}", null, new JwkAlgorithmPreferences(), JwksType.MEMORY); // Empty JWKS
            initJWKSLoader(new SecurityEventCounter());
        }

        @Override
        public Optional<KeyInfo> getKeyInfo(String kid) {
            // Simulate a network failure by returning an empty Optional
            return Optional.empty();
        }

        // Removed overrides for methods that no longer exist in JwksLoader interface
    }
}
