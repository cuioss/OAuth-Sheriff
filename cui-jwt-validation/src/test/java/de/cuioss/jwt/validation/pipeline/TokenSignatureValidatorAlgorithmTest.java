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

import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.jwks.JwksLoader;
import de.cuioss.jwt.validation.jwks.JwksLoaderFactory;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.test.InMemoryJWKSFactory;
import de.cuioss.jwt.validation.test.InMemoryKeyMaterialHandler;
import de.cuioss.jwt.validation.test.JwtTokenTamperingUtil;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

// Replaced static import with constant
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the {@link TokenSignatureValidator} class focusing on different algorithms.
 */
@EnableTestLogger(rootLevel = TestLogLevel.DEBUG)
@DisplayName("Tests TokenSignatureValidator with different algorithms")
class TokenSignatureValidatorAlgorithmTest {
    private static final String ISSUER = "Token-Test-testIssuer";

    private NonValidatingJwtParser jwtParser;
    private SecurityEventCounter securityEventCounter;
    private TokenSignatureValidator validator;

    @BeforeEach
    void setUp() {
        // Create a security event counter
        securityEventCounter = new SecurityEventCounter();

        // Create a real JWT parser using the builder
        jwtParser = NonValidatingJwtParser.builder().securityEventCounter(securityEventCounter).build();

        // Create an in-memory JwksLoader with a valid key
        String jwksContent = InMemoryJWKSFactory.createMultiAlgorithmJwks();
        JwksLoader jwksLoader = JwksLoaderFactory.createInMemoryLoader(jwksContent);
        jwksLoader.initSecurityEventCounter(securityEventCounter);

        // Create the validator with the in-memory JwksLoader and security event counter
        validator = new TokenSignatureValidator(jwksLoader, securityEventCounter);
    }

    /**
     * Creates a token signed with the specified algorithm.
     *
     * @param algorithm the algorithm to use for signing
     * @return a signed JWT token
     */
    private String createToken(InMemoryKeyMaterialHandler.Algorithm algorithm) {
        Instant now = Instant.now();
        Instant expiration = now.plus(1, ChronoUnit.HOURS);

        return Jwts.builder().subject("test-subject")

                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .header().add("kid", algorithm.name()).and()
                .signWith(InMemoryKeyMaterialHandler.getDefaultPrivateKey(algorithm), algorithm.getAlgorithm())
                .compact();
    }

    @ParameterizedTest
    // Note: EC algorithms (ES256, ES384, ES512) are excluded because InMemoryKeyMaterialHandler uses dummy x and y coordinates
    // in the JWKS representation, which causes validation failures. The TokenSignatureValidator itself supports EC algorithms,
    // but we can't properly test them without extracting real EC key coordinates.
    @EnumSource(value = InMemoryKeyMaterialHandler.Algorithm.class, names = {"RS256", "RS384", "RS512", "PS256", "PS384", "PS512"})
    @DisplayName("Should validate token with valid signature for different algorithms")
    void shouldValidateTokenWithValidSignature(InMemoryKeyMaterialHandler.Algorithm algorithm) {
        // Create a valid token with the specified algorithm
        String token = createToken(algorithm);

        // Parse the token
        DecodedJwt decodedJwt = jwtParser.decode(token);
        assertNotNull(decodedJwt, "Token should be decoded successfully");

        // Validate the signature - should not throw an exception
        assertDoesNotThrow(() -> validator.validateSignature(decodedJwt),
                "Token with valid " + algorithm + " signature should be validated without exceptions");
    }

    @ParameterizedTest
    // Note: EC algorithms (ES256, ES384, ES512) are excluded because InMemoryKeyMaterialHandler uses dummy x and y coordinates
    // in the JWKS representation, which causes validation failures. The TokenSignatureValidator itself supports EC algorithms,
    // but we can't properly test them without extracting real EC key coordinates.
    @EnumSource(value = InMemoryKeyMaterialHandler.Algorithm.class, names = {"RS256", "RS384", "RS512", "PS256", "PS384", "PS512"})
    @DisplayName("Should reject token with tampered signature for different algorithms")
    void shouldRejectTokenWithTamperedSignature(InMemoryKeyMaterialHandler.Algorithm algorithm) {
        // Get initial count
        long initialCount = securityEventCounter.getCount(SecurityEventCounter.EventType.SIGNATURE_VALIDATION_FAILED);

        // Create a valid token with the specified algorithm
        String validToken = createToken(algorithm);

        // Tamper with the signature
        String tamperedToken = JwtTokenTamperingUtil.applyTamperingStrategy(
                validToken, JwtTokenTamperingUtil.TamperingStrategy.MODIFY_SIGNATURE_LAST_CHAR);

        // Parse the tampered token
        DecodedJwt decodedJwt = jwtParser.decode(tamperedToken);
        assertNotNull(decodedJwt, "Tampered token should be decoded successfully");

        // Validate the signature - should throw an exception
        TokenValidationException exception = assertThrows(TokenValidationException.class,
                () -> validator.validateSignature(decodedJwt),
                "Token with tampered " + algorithm + " signature should be rejected");

        // Verify the exception has the correct event type
        assertEquals(SecurityEventCounter.EventType.SIGNATURE_VALIDATION_FAILED, exception.getEventType(),
                "Exception should have SIGNATURE_VALIDATION_FAILED event type");

        // Verify log message
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "Invalid signature");

        // Verify security event was recorded
        assertTrue(securityEventCounter.getCount(SecurityEventCounter.EventType.SIGNATURE_VALIDATION_FAILED) > initialCount,
                "SIGNATURE_VALIDATION_FAILED event should be incremented");
    }
}
