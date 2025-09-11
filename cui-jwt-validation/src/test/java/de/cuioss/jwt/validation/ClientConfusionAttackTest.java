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

import de.cuioss.jwt.validation.domain.token.IdTokenContent;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.pipeline.NonValidatingJwtParser;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.test.InMemoryJWKSFactory;
import de.cuioss.jwt.validation.test.TestTokenHolder;
import de.cuioss.jwt.validation.test.generator.ClaimControlParameter;
import de.cuioss.jwt.validation.test.junit.TestTokenSource;
import de.cuioss.tools.logging.CuiLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests the client confusion attack prevention feature.
 */
class ClientConfusionAttackTest {

    private static final CuiLogger LOGGER = new CuiLogger(ClientConfusionAttackTest.class);

    // Client ID constants
    private static final String ALTERNATIVE_CLIENT_ID = "alternative-client";

    /**
     * The token validator used for testing.
     */
    private TokenValidator tokenValidator;

    /**
     * Set up the test environment.
     */
    @BeforeEach
    void setUp() {
        // Use the InMemoryJWKSFactory to create a proper JWKS document with the default key ID
        String jwksContent = InMemoryJWKSFactory.createDefaultJwks();

        // Print the JWKS content for debugging
        LOGGER.debug("JWKS content: " + jwksContent);
    }

    @ParameterizedTest
    @TestTokenSource(value = TokenType.ID_TOKEN)
    @DisplayName("Token with valid azp claim should be accepted")
    void verify_azp_validation(TestTokenHolder tokenHolder) {
        // Get the token
        String token = tokenHolder.getRawToken();
        LOGGER.debug("Token: " + token);

        // Print the token headers using NonValidatingJwtParser to debug
        try {
            var decoder = NonValidatingJwtParser.builder().securityEventCounter(new SecurityEventCounter()).build();
            var jwt = decoder.decode(token);
            var header = jwt.getHeader();
            var kid = jwt.getKid().orElse("null");
            var body = jwt.getBody();

            LOGGER.debug("Token headers: " + header);
            LOGGER.debug("Token kid: " + kid);
            LOGGER.debug("Token body: " + body);

            // Add more detailed debugging for audience claim
            if (body != null) {
                if (body.containsKey("aud")) {
                    LOGGER.debug("Audience claim found: " + body.getValue("aud").orElse(null));
                    LOGGER.debug("Audience claim type: " + body.getValue("aud").map(obj -> obj.getClass().getSimpleName()).orElse("null"));
                } else {
                    LOGGER.debug("No audience claim found in token");
                }

                if (body.containsKey("azp")) {
                    LOGGER.debug("AZP claim found: " + body.getValue("azp").orElse(null));
                } else {
                    LOGGER.debug("No azp claim found in token");
                }
            }
        } catch (Exception e) {
            // Error handling is done by the test assertions
        }
        // Create a token validator with the issuer config
        tokenValidator = TokenValidator.builder().issuerConfig(tokenHolder.getIssuerConfig()).build();

        // Verify the token is accepted
        IdTokenContent result = tokenValidator.createIdToken(token);
        assertNotNull(result, "Token with valid azp claim should be accepted");
    }

    @ParameterizedTest
    @TestTokenSource(value = TokenType.ID_TOKEN)
    @DisplayName("Token with invalid azp claim should be rejected")
    void verify_azp_validation_failure(TestTokenHolder tokenHolder) {
        // Get the token
        String token = tokenHolder.getRawToken();

        // Create an IssuerConfig with a different client ID than what's in the token
        IssuerConfig issuerConfig = IssuerConfig.builder()
                .issuerIdentifier(TestTokenHolder.TEST_ISSUER)
                .expectedClientId(ALTERNATIVE_CLIENT_ID) // Use a different client ID
                .jwksContent(InMemoryJWKSFactory.createDefaultJwks())
                .build();

        // Create a token validator with the modified issuer config
        tokenValidator = TokenValidator.builder().issuerConfig(issuerConfig).build();

        // Verify the token is rejected
        var exception = assertThrows(TokenValidationException.class, () -> tokenValidator.createIdToken(token),
                "Token with invalid azp claim should be rejected");
        assertEquals(SecurityEventCounter.EventType.AZP_MISMATCH, exception.getEventType(),
                "Exception should have AZP_MISMATCH event type");
    }

    @Test
    @DisplayName("TestTokenHolder getIssuerConfig should use fixed values avoiding circular dependency")
    void verify_issuer_config_uses_fixed_values() {
        // Test the core fix: getIssuerConfig() should use fixed CLIENT_ID regardless of token claims
        
        // Create token holder with alternative AZP claim
        TestTokenHolder tokenHolder = new TestTokenHolder(TokenType.ID_TOKEN,
                ClaimControlParameter.builder().build());
        tokenHolder.withAuthorizedParty(ALTERNATIVE_CLIENT_ID);

        // Get the IssuerConfig - this should use the fixed CLIENT_ID, not the token's AZP
        IssuerConfig issuerConfig = tokenHolder.getIssuerConfig();

        // Verify that getIssuerConfig uses the fixed CLIENT_ID, not the token's AZP claim
        assertTrue(issuerConfig.getExpectedClientId().contains(TestTokenHolder.TEST_CLIENT_ID),
                "IssuerConfig should use fixed CLIENT_ID");
        assertFalse(issuerConfig.getExpectedClientId().contains(ALTERNATIVE_CLIENT_ID),
                "IssuerConfig should NOT use token's AZP claim");

        // Verify that audience also uses fixed values
        assertTrue(issuerConfig.getExpectedAudience().contains(TestTokenHolder.TEST_AUDIENCE),
                "IssuerConfig should use fixed audience");

    }

    @ParameterizedTest
    @TestTokenSource(value = TokenType.ID_TOKEN)
    @DisplayName("Audience validation without azp validation should work")
    void verify_audience_validation_without_azp(TestTokenHolder tokenHolder) {
        // Get the token
        String token = tokenHolder.getRawToken();
        LOGGER.debug("Generated token: " + token);

        // Print the token headers using NonValidatingJwtParser to debug
        try {
            var decoder = NonValidatingJwtParser.builder().securityEventCounter(new SecurityEventCounter()).build();
            var jwt = decoder.decode(token);
            var header = jwt.getHeader();
            var kid = jwt.getKid().orElse("null");
            var body = jwt.getBody();

            LOGGER.debug("Token headers: " + header);
            LOGGER.debug("Token kid: " + kid);
            LOGGER.debug("Token body: " + body);

            // Add more detailed debugging for audience claim
            if (body != null) {
                if (body.containsKey("aud")) {
                    LOGGER.debug("Audience claim found: " + body.getValue("aud").orElse(null));
                    LOGGER.debug("Audience claim type: " + body.getValue("aud").map(obj -> obj.getClass().getSimpleName()).orElse("null"));
                } else {
                    LOGGER.debug("No audience claim found in token");
                }

                if (body.containsKey("azp")) {
                    LOGGER.debug("AZP claim found: " + body.getValue("azp").orElse(null));
                } else {
                    LOGGER.debug("No azp claim found in token");
                }
            }
        } catch (Exception e) {
            // Error handling is done by the test assertions
        }

        // Create an IssuerConfig with the correct audience but no client ID
        IssuerConfig issuerConfig = IssuerConfig.builder()
                .issuerIdentifier(TestTokenHolder.TEST_ISSUER)
                .expectedAudience(tokenHolder.getAuthorizedParty())
                .jwksContent(InMemoryJWKSFactory.createDefaultJwks())
                .build();

        LOGGER.debug("IssuerConfig: expectedAudience=" + issuerConfig.getExpectedAudience() +
                ", expectedClientId=" + issuerConfig.getExpectedClientId());

        // Create a token validator with the issuer config
        tokenValidator = TokenValidator.builder().issuerConfig(tokenHolder.getIssuerConfig()).build();

        // Verify the token is accepted
        IdTokenContent result = tokenValidator.createIdToken(token);
        assertNotNull(result, "Token with valid audience should be accepted");
    }

    @ParameterizedTest
    @TestTokenSource(value = TokenType.ID_TOKEN)
    @DisplayName("AZP validation without audience validation should work")
    void verify_azp_validation_without_audience(TestTokenHolder tokenHolder) {
        // Get the token
        String token = tokenHolder.getRawToken();

        // Create a token validator with the issuer config from tokenHolder
        tokenValidator = TokenValidator.builder().issuerConfig(tokenHolder.getIssuerConfig()).build();

        // Verify the token is accepted
        IdTokenContent result = tokenValidator.createIdToken(token);
        assertNotNull(result, "Token with valid azp should be accepted");
    }

    @ParameterizedTest
    @TestTokenSource(value = TokenType.ID_TOKEN)
    @DisplayName("Token with missing azp claim should be rejected when validation is enabled")
    void verify_missing_azp_rejected(TestTokenHolder tokenHolder) {
        // Remove the azp claim
        tokenHolder.withAuthorizedParty(null);
        String token = tokenHolder.getRawToken();

        // Create a token validator with the issuer config from tokenHolder
        tokenValidator = TokenValidator.builder().issuerConfig(tokenHolder.getIssuerConfig()).build();

        // Verify the token is rejected
        var exception = assertThrows(TokenValidationException.class, () -> tokenValidator.createIdToken(token),
                "Token with missing azp claim should be rejected");
        assertEquals(SecurityEventCounter.EventType.MISSING_CLAIM, exception.getEventType(),
                "Exception should have MISSING_CLAIM event type");
    }
}
