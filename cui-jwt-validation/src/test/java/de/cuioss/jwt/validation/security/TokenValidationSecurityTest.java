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
import de.cuioss.jwt.validation.TokenType;
import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.domain.token.AccessTokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.test.InMemoryJWKSFactory;
import de.cuioss.jwt.validation.test.JwtTokenTamperingUtil;
import de.cuioss.jwt.validation.test.JwtTokenTamperingUtil.TamperingStrategy;
import de.cuioss.jwt.validation.test.TestTokenHolder;
import de.cuioss.jwt.validation.test.generator.TestTokenGenerators;
import de.cuioss.jwt.validation.test.junit.TestTokenSource;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the security aspects of token validation.
 * <p>
 * These tests verify that the token validation library correctly rejects
 * tampered tokens and is resistant to various token cracking attempts.
 * <p>
 * What is tested:
 * <ul>
 *   <li>Rejection of tokens with tampered headers</li>
 *   <li>Rejection of tokens with tampered payloads</li>
 *   <li>Rejection of tokens with tampered signatures</li>
 *   <li>Rejection of tokens with invalid algorithms</li>
 *   <li>Rejection of tokens with missing required claims</li>
 * </ul>
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("Token Validation Security Tests")
class TokenValidationSecurityTest {

    private TokenValidator tokenValidator;

    @BeforeEach
    void setUp() {
        // Create issuer config with JWKS content
        IssuerConfig issuerConfig = IssuerConfig.builder()
                .issuer("Token-Test-testIssuer")
                .expectedAudience(TestTokenHolder.TEST_AUDIENCE)
                .jwksContent(InMemoryJWKSFactory.createDefaultJwks())
                .build();

        // Create validation factory
        ParserConfig config = ParserConfig.builder().build();
        tokenValidator = new TokenValidator(config, issuerConfig);
    }

    @ParameterizedTest
    @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 3)
    @DisplayName("Should reject tokens with tampered payloads")
    void shouldRejectTokensWithTamperedPayloads(TestTokenHolder tokenHolder) {
        String validToken = tokenHolder.getRawToken();

        String[] parts = validToken.split("\\.");
        String payload = parts[1];
        byte[] payloadBytes = Base64.getUrlDecoder().decode(payload);
        String payloadJson = new String(payloadBytes);

        String tamperedPayloadJson = payloadJson.replaceAll("\"sub\":\"[^\"]*\"", "\"sub\":\"tampered-subject\"");
        String tamperedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(tamperedPayloadJson.getBytes());
        String tamperedToken = parts[0] + "." + tamperedPayload + ".";

        assertThrows(TokenValidationException.class, () ->
                        tokenValidator.createAccessToken(tamperedToken),
                "Should reject token with tampered payload");
    }

    @ParameterizedTest
    @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 3)
    @DisplayName("Should reject tokens with tampered signatures")
    void shouldRejectTokensWithTamperedSignatures(TestTokenHolder tokenHolder) {
        String validToken = tokenHolder.getRawToken();
        String tamperedToken = JwtTokenTamperingUtil.applyTamperingStrategy(validToken, TamperingStrategy.MODIFY_SIGNATURE_RANDOM_CHAR);

        assertThrows(TokenValidationException.class, () ->
                        tokenValidator.createAccessToken(tamperedToken),
                "Should reject token with tampered signature");
    }

    @ParameterizedTest
    @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 3)
    @DisplayName("Should reject tokens with algorithm 'none'")
    void shouldRejectTokensWithAlgorithmNone(TestTokenHolder tokenHolder) {
        String validToken = tokenHolder.getRawToken();
        String tamperedToken = JwtTokenTamperingUtil.applyTamperingStrategy(validToken, TamperingStrategy.ALGORITHM_NONE);

        assertThrows(TokenValidationException.class, () ->
                        tokenValidator.createAccessToken(tamperedToken),
                "Should reject token with 'none' algorithm");
    }

    @Test
    @DisplayName("Should reject tokens with missing required claims")
    void shouldRejectTokensWithMissingRequiredClaims() {
        // Generate a valid token
        String validToken = TestTokenGenerators.accessTokens().next().getRawToken();

        // Split the token into its parts
        String[] parts = validToken.split("\\.");

        // Decode the payload
        String payload = parts[1];
        byte[] payloadBytes = Base64.getUrlDecoder().decode(payload);
        String payloadJson = new String(payloadBytes);

        // Remove the 'iss' claim
        String tamperedPayloadJson = payloadJson.replaceAll("\"iss\":\"[^\"]*\",?", "");

        // Encode the tampered payload
        String tamperedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(tamperedPayloadJson.getBytes());

        // Reconstruct the token (without signature since it would be invalid)
        String tamperedToken = parts[0] + "." + tamperedPayload + ".";

        // Verify that the tampered token is rejected
        assertThrows(TokenValidationException.class, () ->
                tokenValidator.createAccessToken(tamperedToken));
    }

    @ParameterizedTest
    @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 5)
    @DisplayName("Should accept valid tokens")
    void shouldAcceptValidTokens(TestTokenHolder tokenHolder) {
        String validToken = tokenHolder.getRawToken();
        AccessTokenContent tokenContent = tokenValidator.createAccessToken(validToken);

        assertNotNull(tokenContent, "Token content should not be null");
        assertEquals("Token-Test-testIssuer", tokenContent.getIssuer(), "Issuer should match expected");
    }

    @ParameterizedTest
    @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 3)
    @DisplayName("Should reject tokens with algorithm downgrade")
    void shouldRejectTokensWithAlgorithmDowngrade(TestTokenHolder tokenHolder) {
        String validToken = tokenHolder.getRawToken();
        String tamperedToken = JwtTokenTamperingUtil.applyTamperingStrategy(validToken, TamperingStrategy.ALGORITHM_DOWNGRADE);

        assertThrows(TokenValidationException.class, () ->
                        tokenValidator.createAccessToken(tamperedToken),
                "Should reject token with downgraded algorithm");
    }

    @ParameterizedTest
    @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 3)
    @DisplayName("Should reject tokens with invalid key ID")
    void shouldRejectTokensWithInvalidKeyId(TestTokenHolder tokenHolder) {
        String validToken = tokenHolder.getRawToken();
        String tamperedToken = JwtTokenTamperingUtil.applyTamperingStrategy(validToken, TamperingStrategy.INVALID_KID);

        assertThrows(TokenValidationException.class, () ->
                        tokenValidator.createAccessToken(tamperedToken),
                "Should reject token with invalid key ID");
    }
}
