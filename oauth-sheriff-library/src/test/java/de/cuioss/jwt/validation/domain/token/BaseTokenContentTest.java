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
package de.cuioss.sheriff.oauth.library.domain.token;

import de.cuioss.sheriff.oauth.library.TokenType;
import de.cuioss.sheriff.oauth.library.domain.claim.ClaimName;
import de.cuioss.sheriff.oauth.library.domain.claim.ClaimValue;
import de.cuioss.sheriff.oauth.library.json.MapRepresentation;
import de.cuioss.sheriff.oauth.library.test.TestTokenHolder;
import de.cuioss.sheriff.oauth.library.test.generator.TestTokenGenerators;
import de.cuioss.sheriff.oauth.library.test.junit.TestTokenSource;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.valueobjects.junit5.contracts.ShouldHandleObjectContracts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;

import java.io.Serial;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BaseTokenContent}.
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("Tests BaseTokenContent functionality")
class BaseTokenContentTest implements ShouldHandleObjectContracts<BaseTokenContentTest.TestBaseTokenContent> {

    @ParameterizedTest
    @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 3)
    @DisplayName("Create BaseTokenContent with valid parameters")
    void shouldCreateBaseTokenContentWithValidParameters(TestTokenHolder tokenHolder) {
        var baseTokenContent = new TestBaseTokenContent(tokenHolder.getClaims(), tokenHolder.getRawToken(), TokenType.ACCESS_TOKEN, MapRepresentation.empty());

        assertNotNull(baseTokenContent, "BaseTokenContent should not be null");
        assertEquals(tokenHolder.getClaims(), baseTokenContent.getClaims(), "Claims should match");
        assertEquals(tokenHolder.getRawToken(), baseTokenContent.getRawToken(), "Raw token should match");
        assertEquals(TokenType.ACCESS_TOKEN, baseTokenContent.getTokenType(), "Token type should match");
    }

    @ParameterizedTest
    @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 2)
    @DisplayName("Return claim option correctly")
    void shouldReturnClaimOptionCorrectly(TestTokenHolder tokenHolder) {
        ClaimValue claimValue = ClaimValue.forPlainString("test-value");
        tokenHolder.withClaim(ClaimName.ISSUER.getName(), claimValue);
        var baseTokenContent = new TestBaseTokenContent(tokenHolder.getClaims(), tokenHolder.getRawToken(), TokenType.ACCESS_TOKEN, MapRepresentation.empty());

        Optional<ClaimValue> claimOption = baseTokenContent.getClaimOption(ClaimName.ISSUER);

        assertTrue(claimOption.isPresent(), "Claim option should be present");
        assertEquals(claimValue, claimOption.get(), "Claim value should match");
    }

    @ParameterizedTest
    @TestTokenSource(value = TokenType.ACCESS_TOKEN, count = 2)
    @DisplayName("Return empty claim option when claim is not present")
    void shouldReturnEmptyClaimOptionWhenClaimIsNotPresent(TestTokenHolder tokenHolder) {
        tokenHolder.withoutClaim(ClaimName.ISSUER.getName());
        var baseTokenContent = new TestBaseTokenContent(tokenHolder.getClaims(), tokenHolder.getRawToken(), TokenType.ACCESS_TOKEN, MapRepresentation.empty());

        Optional<ClaimValue> claimOption = baseTokenContent.getClaimOption(ClaimName.ISSUER);

        assertTrue(claimOption.isEmpty(), "Claim option should be empty");
    }

    @Override
    public TestBaseTokenContent getUnderTest() {
        var tokenHolder = TestTokenGenerators.accessTokens().next();
        return new TestBaseTokenContent(tokenHolder.getClaims(), tokenHolder.getRawToken(), TokenType.ACCESS_TOKEN, MapRepresentation.empty());
    }

    /**
     * Concrete implementation of BaseTokenContent for testing.
     */
    static class TestBaseTokenContent extends BaseTokenContent {
        @Serial
        private static final long serialVersionUID = 1L;

        TestBaseTokenContent(Map<String, ClaimValue> claims, String rawToken, TokenType tokenType, MapRepresentation rawPayload) {
            super(claims, rawToken, tokenType, rawPayload);
        }
    }
}
