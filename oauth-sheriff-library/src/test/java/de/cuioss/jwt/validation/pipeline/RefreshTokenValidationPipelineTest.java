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
package de.cuioss.sheriff.oauth.library.pipeline;

import de.cuioss.sheriff.oauth.library.domain.token.RefreshTokenContent;
import de.cuioss.sheriff.oauth.library.security.SecurityEventCounter;
import de.cuioss.sheriff.oauth.library.test.TestTokenHolder;
import de.cuioss.sheriff.oauth.library.test.generator.TestTokenGenerators;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link RefreshTokenValidationPipeline}.
 * <p>
 * Tests minimal validation for refresh tokens.
 */
@DisplayName("RefreshTokenValidationPipeline Tests")
class RefreshTokenValidationPipelineTest {

    private RefreshTokenValidationPipeline pipeline;
    private SecurityEventCounter securityEventCounter;
    private NonValidatingJwtParser jwtParser;

    @BeforeEach
    void setUp() {
        securityEventCounter = new SecurityEventCounter();
        jwtParser = NonValidatingJwtParser.builder()
                .securityEventCounter(securityEventCounter)
                .build();
        pipeline = new RefreshTokenValidationPipeline(jwtParser);
    }

    @Test
    @DisplayName("Should validate JWT refresh token and extract claims")
    void shouldValidateJwtRefreshTokenAndExtractClaims() {
        // Use a valid JWT token
        TestTokenHolder tokenHolder = TestTokenGenerators.refreshTokens().next();
        String tokenString = tokenHolder.getRawToken();

        RefreshTokenContent result = pipeline.validate(tokenString);

        assertNotNull(result);
        assertEquals(tokenString, result.getRawToken());
        assertFalse(result.getClaims().isEmpty(), "Should have extracted claims from JWT");
    }

    @Test
    @DisplayName("Should handle opaque refresh token gracefully")
    void shouldHandleOpaqueRefreshTokenGracefully() {
        // Opaque refresh tokens are not JWTs
        String opaqueToken = "opaque_refresh_token_12345";

        RefreshTokenContent result = pipeline.validate(opaqueToken);

        assertNotNull(result);
        assertEquals(opaqueToken, result.getRawToken());
        assertTrue(result.getClaims().isEmpty(), "Opaque tokens should have empty claims");
    }

    @Test
    @DisplayName("Should ignore JWT parsing failures for opaque tokens")
    void shouldIgnoreJwtParsingFailuresForOpaqueTokens() {
        // Invalid JWT format should not throw exception for refresh tokens
        String invalidJwt = "not.a.valid.jwt.token";

        RefreshTokenContent result = pipeline.validate(invalidJwt);

        assertNotNull(result);
        assertEquals(invalidJwt, result.getRawToken());
        assertTrue(result.getClaims().isEmpty());
    }

    @Test
    @DisplayName("Should handle JWT with minimal claims")
    void shouldHandleJwtWithMinimalClaims() {
        // JWT with minimal claims (just issuer and subject)
        TestTokenHolder tokenHolder = TestTokenGenerators.refreshTokens().next();
        String tokenString = tokenHolder.getRawToken();

        assertDoesNotThrow(() -> {
            RefreshTokenContent result = pipeline.validate(tokenString);
            assertNotNull(result);
        });
    }
}
