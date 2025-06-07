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
package de.cuioss.jwt.validation;

import de.cuioss.jwt.validation.domain.claim.ClaimName;
import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import de.cuioss.jwt.validation.domain.token.IdTokenContent;
import de.cuioss.jwt.validation.security.AlgorithmPreferences;
import de.cuioss.jwt.validation.test.InMemoryJWKSFactory;
import de.cuioss.jwt.validation.test.TestTokenHolder;
import de.cuioss.jwt.validation.test.generator.TestTokenGenerators;
import de.cuioss.jwt.validation.test.junit.TestTokenSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests compliance with the OpenID Connect Core 1.0 specification.
 * This test class verifies that the library correctly implements the requirements
 * specified in OpenID Connect Core 1.0 for ID tokens.
 * 
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html">OpenID Connect Core 1.0</a>
 */
@DisplayName("OpenID Connect Compliance Tests")
class OpenIDConnectComplianceTest {

    private static final String ISSUER = "Token-Test-testIssuer";
    private static final String AUDIENCE = "test-client";
    private static final String CLIENT_ID = "test-client";

    private TokenValidator tokenValidator;

    @BeforeEach
    void setUp() {
        // Get the default JWKS content
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

    @Nested
    @DisplayName("Section 2: ID Token")
    class IdTokenTests {

        @Test
        @DisplayName("2.2: Required Claims - 'iss' (Issuer) Claim")
        void shouldHandleIssuerClaim() {
            // Given
            String token = TestTokenGenerators.idTokens().next().getRawToken();

            // When
            IdTokenContent result = tokenValidator.createIdToken(token);

            // Then
            assertEquals(ISSUER, result.getIssuer(),
                    "Issuer claim should match the expected value");
        }

        @ParameterizedTest
        @TestTokenSource(value = TokenType.ID_TOKEN, count = 5)
        @DisplayName("2.2: Required Claims - 'sub' (Subject) Claim")
        void shouldHandleSubjectClaim(TestTokenHolder tokenHolder) {
            // Given
            String subject = "test-subject";

            // Set a specific subject
            tokenHolder.withClaim(ClaimName.SUBJECT.getName(), ClaimValue.forPlainString(subject));

            String token = tokenHolder.getRawToken();

            // When
            IdTokenContent result = new TokenValidator(tokenHolder.getIssuerConfig()).createIdToken(token);

            // Then
            assertEquals(subject, result.getSubject(),
                    "Subject claim should match the expected value");
        }

        @Test
        @DisplayName("2.2: Required Claims - 'aud' (Audience) Claim")
        void shouldHandleAudienceClaim() {
            // Given
            String token = TestTokenGenerators.idTokens().next().getRawToken();

            // When
            IdTokenContent result = tokenValidator.createIdToken(token);

            // Then
            assertEquals(List.of(CLIENT_ID), result.getAudience(),
                    "Audience claim should match the expected value");
        }

        @Test
        @DisplayName("2.2: Required Claims - 'exp' (Expiration Time) Claim")
        void shouldHandleExpirationTimeClaim() {
            // Given
            String token = TestTokenGenerators.idTokens().next().getRawToken();

            // When
            IdTokenContent result = tokenValidator.createIdToken(token);

            // Then
            assertNotNull(result.getExpirationTime(),
                    "Expiration time claim should be present");
            assertFalse(result.isExpired(),
                    "Token should not be expired");
        }

        @Test
        @DisplayName("2.2: Required Claims - 'iat' (Issued At) Claim")
        void shouldHandleIssuedAtClaim() {
            // Given
            String token = TestTokenGenerators.idTokens().next().getRawToken();

            // When
            IdTokenContent result = tokenValidator.createIdToken(token);

            // Then
            assertNotNull(result.getIssuedAtTime(),
                    "Issued At claim should be present");
        }

        @ParameterizedTest
        @TestTokenSource(value = TokenType.ID_TOKEN, count = 5)
        @DisplayName("2.2: Optional Claims - 'auth_time' (Authentication Time) Claim")
        void shouldHandleAuthTimeClaim(TestTokenHolder tokenHolder) {
            // Given
            Instant authTime = Instant.now().minus(5, ChronoUnit.MINUTES);

            // Add auth_time claim
            tokenHolder.withClaim("auth_time", ClaimValue.forPlainString(String.valueOf(authTime.getEpochSecond())));

            String token = tokenHolder.getRawToken();

            // When
            IdTokenContent result = new TokenValidator(tokenHolder.getIssuerConfig()).createIdToken(token);

            // Then
            assertTrue(result.getClaims().containsKey("auth_time"),
                    "Authentication Time claim should be present");
        }

        @ParameterizedTest
        @TestTokenSource(value = TokenType.ID_TOKEN, count = 5)
        @DisplayName("2.2: Optional Claims - 'nonce' Claim")
        void shouldHandleNonceClaim(TestTokenHolder tokenHolder) {
            // Given
            String nonce = "test-nonce";

            // Add nonce claim
            tokenHolder.withClaim("nonce", ClaimValue.forPlainString(nonce));

            String token = tokenHolder.getRawToken();

            // When
            IdTokenContent result = new TokenValidator(tokenHolder.getIssuerConfig()).createIdToken(token);

            // Then
            assertTrue(result.getClaims().containsKey("nonce"),
                    "Nonce claim should be present");
            assertEquals(nonce, result.getClaims().get("nonce").getOriginalString(),
                    "Nonce claim should match the expected value");
        }

        @Test
        @DisplayName("2.2: Optional Claims - 'azp' (Authorized Party) Claim")
        void shouldHandleAuthorizedPartyClaim() {
            // Given
            String token = TestTokenGenerators.idTokens().next().getRawToken();

            // When
            IdTokenContent result = tokenValidator.createIdToken(token);

            // Then
            assertTrue(result.getClaimOption(ClaimName.AUTHORIZED_PARTY).isPresent(),
                    "Authorized Party claim should be present");
            assertEquals(CLIENT_ID, result.getClaimOption(ClaimName.AUTHORIZED_PARTY).get().getOriginalString(),
                    "Authorized Party claim should match the expected value");
        }
    }

    @Nested
    @DisplayName("Section 5: Standard Claims")
    class StandardClaimsTests {

        @ParameterizedTest
        @TestTokenSource(value = TokenType.ID_TOKEN, count = 5)
        @DisplayName("5.1: Standard Claims - 'name' Claim")
        void shouldHandleNameClaim(TestTokenHolder tokenHolder) {
            // Given
            String name = "Test User";

            // Add name claim
            tokenHolder.withClaim(ClaimName.NAME.getName(), ClaimValue.forPlainString(name));

            String token = tokenHolder.getRawToken();

            // When
            IdTokenContent result = new TokenValidator(tokenHolder.getIssuerConfig()).createIdToken(token);

            // Then
            assertEquals(name, result.getName().orElse(null),
                    "Name claim should match the expected value");
        }

        @ParameterizedTest
        @TestTokenSource(value = TokenType.ID_TOKEN, count = 5)
        @DisplayName("5.1: Standard Claims - 'email' Claim")
        void shouldHandleEmailClaim(TestTokenHolder tokenHolder) {
            // Given
            String email = "test@example.com";

            // Add email claim
            tokenHolder.withClaim(ClaimName.EMAIL.getName(), ClaimValue.forPlainString(email));

            String token = tokenHolder.getRawToken();

            // When
            IdTokenContent result = new TokenValidator(tokenHolder.getIssuerConfig()).createIdToken(token);

            // Then
            assertEquals(email, result.getEmail().orElse(null),
                    "Email claim should match the expected value");
        }

        @ParameterizedTest
        @TestTokenSource(value = TokenType.ID_TOKEN, count = 5)
        @DisplayName("5.1: Standard Claims - 'preferred_username' Claim")
        void shouldHandlePreferredUsernameClaim(TestTokenHolder tokenHolder) {
            // Given
            String preferredUsername = "testuser";

            // Add preferred_username claim
            tokenHolder.withClaim(ClaimName.PREFERRED_USERNAME.getName(), ClaimValue.forPlainString(preferredUsername));

            String token = tokenHolder.getRawToken();

            // When
            IdTokenContent result = new TokenValidator(tokenHolder.getIssuerConfig()).createIdToken(token);

            // Then
            assertTrue(result.getClaimOption(ClaimName.PREFERRED_USERNAME).isPresent(),
                    "Preferred Username claim should be present");
            assertEquals(preferredUsername, result.getClaimOption(ClaimName.PREFERRED_USERNAME).get().getOriginalString(),
                    "Preferred Username claim should match the expected value");
        }
    }
}
