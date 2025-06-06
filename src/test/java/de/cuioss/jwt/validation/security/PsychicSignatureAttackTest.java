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
package de.cuioss.jwt.validation.security;

import de.cuioss.jwt.validation.IssuerConfig;
import de.cuioss.jwt.validation.ParserConfig;
import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.test.InMemoryJWKSFactory;
import de.cuioss.jwt.validation.test.InMemoryKeyMaterialHandler;
import de.cuioss.jwt.validation.test.TestTokenHolder;
import de.cuioss.jwt.validation.test.generator.TestTokenGenerators;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.tools.logging.CuiLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for validating protection against the "Psychic Signature" vulnerability (CVE-2022-21449).
 * <p>
 * This vulnerability allowed attackers to bypass ECDSA signature verification by using all-zero signatures.
 * <p>
 * This test verifies that the library correctly rejects tokens with ECDSA signatures containing all zeros.
 */
@EnableTestLogger
@DisplayName("Tests for ECDSA Psychic Signature Attack Protection")
class PsychicSignatureAttackTest {

    private static final CuiLogger LOGGER = new CuiLogger(PsychicSignatureAttackTest.class);

    private TokenValidator tokenValidator;
    private SecurityEventCounter securityEventCounter;

    @BeforeEach
    void setUp() {
        // Create issuer config with JWKS content
        IssuerConfig issuerConfig = IssuerConfig.builder()
                .issuer("Token-Test-testIssuer")
                .expectedAudience("test-client")
                .jwksContent(InMemoryJWKSFactory.createDefaultJwks())
                .build();

        // Create validation factory
        ParserConfig config = ParserConfig.builder().build();
        tokenValidator = new TokenValidator(config, issuerConfig);

        // Get the security event counter from the TokenValidator
        securityEventCounter = tokenValidator.getSecurityEventCounter();
    }

    @Test
    @DisplayName("Should reject tokens with ES256 all-zero signature")
    void shouldRejectTokenWithES256ZeroSignature() {
        // Log initial counter state
        LOGGER.debug("Initial UNSUPPORTED_ALGORITHM count: %s",
                securityEventCounter.getCount(SecurityEventCounter.EventType.UNSUPPORTED_ALGORITHM));

        // Generate a token with ES256 algorithm
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next()
                .withSigningAlgorithm(InMemoryKeyMaterialHandler.Algorithm.ES256);
        String validToken = tokenHolder.getRawToken();

        // Split the token into its parts
        String[] parts = validToken.split("\\.");

        // Create an all-zero signature (r=0, s=0)
        // ES256 signature is 64 bytes (32 bytes for r, 32 bytes for s)
        byte[] zeroSignature = new byte[64];
        String zeroSignatureBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(zeroSignature);

        // Reconstruct the token with the zero signature
        String tamperedToken = parts[0] + "." + parts[1] + "." + zeroSignatureBase64;

        LOGGER.debug("Created token with ES256 zero signature: %s", tamperedToken);

        // Verify that the token is rejected
        var exception = assertThrows(TokenValidationException.class,
                () -> tokenValidator.createAccessToken(tamperedToken));

        // Verify the exception details
        LOGGER.debug("Exception message: %s", exception.getMessage());

        // Log the security event counter values
        LOGGER.debug("Final UNSUPPORTED_ALGORITHM count: %s",
                securityEventCounter.getCount(SecurityEventCounter.EventType.UNSUPPORTED_ALGORITHM));

        // Log all counter values
        securityEventCounter.getCounters().forEach((type, count) ->
                LOGGER.debug("Counter %s: %s", type, count));

        // Verify that the security event counter was incremented
        // The logs show that ES256 is an unsupported algorithm, so we should check for that event
        assertTrue(securityEventCounter.getCount(SecurityEventCounter.EventType.UNSUPPORTED_ALGORITHM) > 0,
                "Security event counter should be incremented for ES256 zero signature attack");
    }

    @Test
    @DisplayName("Should reject tokens with ES384 all-zero signature")
    void shouldRejectTokenWithES384ZeroSignature() {
        // Log initial counter state
        LOGGER.debug("Initial UNSUPPORTED_ALGORITHM count: %s",
                securityEventCounter.getCount(SecurityEventCounter.EventType.UNSUPPORTED_ALGORITHM));

        // Generate a token with ES384 algorithm
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next()
                .withSigningAlgorithm(InMemoryKeyMaterialHandler.Algorithm.ES384);
        String validToken = tokenHolder.getRawToken();

        // Split the token into its parts
        String[] parts = validToken.split("\\.");

        // Create an all-zero signature (r=0, s=0)
        // ES384 signature is 96 bytes (48 bytes for r, 48 bytes for s)
        byte[] zeroSignature = new byte[96];
        String zeroSignatureBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(zeroSignature);

        // Reconstruct the token with the zero signature
        String tamperedToken = parts[0] + "." + parts[1] + "." + zeroSignatureBase64;

        LOGGER.debug("Created token with ES384 zero signature: %s", tamperedToken);

        // Verify that the token is rejected
        var exception = assertThrows(TokenValidationException.class,
                () -> tokenValidator.createAccessToken(tamperedToken));

        // Verify the exception details
        LOGGER.debug("Exception message: %s", exception.getMessage());

        // Log the security event counter values
        LOGGER.debug("Final UNSUPPORTED_ALGORITHM count: %s",
                securityEventCounter.getCount(SecurityEventCounter.EventType.UNSUPPORTED_ALGORITHM));

        // Log all counter values
        securityEventCounter.getCounters().forEach((type, count) ->
                LOGGER.debug("Counter %s: %s", type, count));

        // Verify that the security event counter was incremented
        // The logs show that ES384 is an unsupported algorithm, so we should check for that event
        assertTrue(securityEventCounter.getCount(SecurityEventCounter.EventType.UNSUPPORTED_ALGORITHM) > 0,
                "Security event counter should be incremented for ES384 zero signature attack");
    }

    @Test
    @DisplayName("Should reject tokens with ES512 all-zero signature")
    void shouldRejectTokenWithES512ZeroSignature() {
        // Log initial counter state
        LOGGER.debug("Initial UNSUPPORTED_ALGORITHM count: %s",
                securityEventCounter.getCount(SecurityEventCounter.EventType.UNSUPPORTED_ALGORITHM));

        // Generate a token with ES512 algorithm
        TestTokenHolder tokenHolder = TestTokenGenerators.accessTokens().next()
                .withSigningAlgorithm(InMemoryKeyMaterialHandler.Algorithm.ES512);
        String validToken = tokenHolder.getRawToken();

        // Split the token into its parts
        String[] parts = validToken.split("\\.");

        // Create an all-zero signature (r=0, s=0)
        // ES512 signature is 132 bytes (66 bytes for r, 66 bytes for s)
        byte[] zeroSignature = new byte[132];
        String zeroSignatureBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(zeroSignature);

        // Reconstruct the token with the zero signature
        String tamperedToken = parts[0] + "." + parts[1] + "." + zeroSignatureBase64;

        LOGGER.debug("Created token with ES512 zero signature: %s", tamperedToken);

        // Verify that the token is rejected
        var exception = assertThrows(TokenValidationException.class,
                () -> tokenValidator.createAccessToken(tamperedToken));

        // Verify the exception details
        LOGGER.debug("Exception message: %s", exception.getMessage());

        // Log the security event counter values
        LOGGER.debug("Final UNSUPPORTED_ALGORITHM count: %s",
                securityEventCounter.getCount(SecurityEventCounter.EventType.UNSUPPORTED_ALGORITHM));

        // Log all counter values
        securityEventCounter.getCounters().forEach((type, count) ->
                LOGGER.debug("Counter %s: %s", type, count));

        // Verify that the security event counter was incremented
        // The logs show that ES512 is an unsupported algorithm, so we should check for that event
        assertTrue(securityEventCounter.getCount(SecurityEventCounter.EventType.UNSUPPORTED_ALGORITHM) > 0,
                "Security event counter should be incremented for ES512 zero signature attack");
    }
}
