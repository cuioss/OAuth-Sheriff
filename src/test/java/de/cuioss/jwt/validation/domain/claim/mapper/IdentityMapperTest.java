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
package de.cuioss.jwt.validation.domain.claim.mapper;

import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import de.cuioss.jwt.validation.domain.claim.ClaimValueType;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger
@DisplayName("Tests IdentityMapper functionality")
class IdentityMapperTest {

    private static final String CLAIM_NAME = "testClaim";
    private final IdentityMapper underTest = new IdentityMapper();

    @Test
    @DisplayName("Should correctly map a regular string")
    void shouldMapRegularString() {
        // Given
        String input = "some-regular-string";
        JsonObject jsonObject = createJsonObjectWithStringClaim(CLAIM_NAME, input);

        // When
        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);

        // Then
        assertNotNull(result, "Result should not be null");
        assertEquals(input, result.getOriginalString(), "Original string should be preserved");
        assertEquals(ClaimValueType.STRING, result.getType(), "Type should be STRING");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    @DisplayName("Should handle null, empty, and whitespace inputs")
    void shouldHandleSpecialInputs(String input) {
        // Given
        JsonObject jsonObject = input == null
                ? createJsonObjectWithNullClaim(CLAIM_NAME)
                : createJsonObjectWithStringClaim(CLAIM_NAME, input);

        // When
        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);

        // Then
        assertNotNull(result, "Result should not be null");
        assertEquals(input, result.getOriginalString(), "Original string should be preserved");
        assertEquals(ClaimValueType.STRING, result.getType(), "Type should be STRING");
    }

    @Test
    @DisplayName("Should handle special characters")
    void shouldHandleSpecialCharacters() {
        // Given
        String input = "!@#$%^&*()_+{}|:<>?~`-=[]\\;',./";
        JsonObject jsonObject = createJsonObjectWithStringClaim(CLAIM_NAME, input);

        // When
        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);

        // Then
        assertNotNull(result, "Result should not be null");
        assertEquals(input, result.getOriginalString(), "Original string should be preserved");
        assertEquals(ClaimValueType.STRING, result.getType(), "Type should be STRING");
    }

    @Test
    @DisplayName("Should handle very long strings")
    void shouldHandleVeryLongStrings() {
        // Given
        String longString = "a".repeat(1000);
        JsonObject jsonObject = createJsonObjectWithStringClaim(CLAIM_NAME, longString);

        // When
        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);

        // Then
        assertNotNull(result, "Result should not be null");
        assertEquals(longString, result.getOriginalString(), "Original string should be preserved");
        assertEquals(ClaimValueType.STRING, result.getType(), "Type should be STRING");
    }

    @Test
    @DisplayName("Should handle numeric values")
    void shouldHandleNumericValues() {
        // Given
        int input = 12345;
        JsonObject jsonObject = Json.createObjectBuilder()
                .add(CLAIM_NAME, input)
                .build();

        // When
        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);

        // Then
        assertNotNull(result, "Result should not be null");
        assertEquals(String.valueOf(input), result.getOriginalString(), "Original string should be the string representation of the number");
        assertEquals(ClaimValueType.STRING, result.getType(), "Type should be STRING");
    }

    @Test
    @DisplayName("Should handle boolean values")
    void shouldHandleBooleanValues() {
        // Given
        boolean input = true;
        JsonObject jsonObject = Json.createObjectBuilder()
                .add(CLAIM_NAME, input)
                .build();

        // When
        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);

        // Then
        assertNotNull(result, "Result should not be null");
        assertEquals(String.valueOf(input), result.getOriginalString(), "Original string should be the string representation of the boolean");
        assertEquals(ClaimValueType.STRING, result.getType(), "Type should be STRING");
    }

    @Test
    @DisplayName("Should handle missing claim")
    void shouldHandleMissingClaim() {
        // Given
        JsonObject jsonObject = Json.createObjectBuilder().build();

        // When
        ClaimValue result = underTest.map(jsonObject, CLAIM_NAME);

        // Then
        assertNotNull(result, "Result should not be null");
        assertNull(result.getOriginalString(), "Original string should be null");
        assertEquals(ClaimValueType.STRING, result.getType(), "Type should be STRING");
    }

    @Test
    @DisplayName("Should handle empty JsonObject")
    void shouldHandleEmptyJsonObject() {
        // Given
        JsonObject emptyJsonObject = Json.createObjectBuilder().build();

        // When
        ClaimValue result = underTest.map(emptyJsonObject, CLAIM_NAME);

        // Then
        assertNotNull(result, "Result should not be null");
        assertNull(result.getOriginalString(), "Original string should be null");
        assertEquals(ClaimValueType.STRING, result.getType(), "Type should be STRING");
    }

    // Helper methods

    private JsonObject createJsonObjectWithStringClaim(String claimName, String value) {
        JsonObjectBuilder builder = Json.createObjectBuilder();
        if (value != null) {
            builder.add(claimName, value);
        } else {
            builder.addNull(claimName);
        }
        return builder.build();
    }

    private JsonObject createJsonObjectWithNullClaim(String claimName) {
        return Json.createObjectBuilder()
                .addNull(claimName)
                .build();
    }
}
