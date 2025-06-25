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

import de.cuioss.jwt.validation.ParserConfig;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.security.SecurityEventCounter.EventType;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for {@link NonValidatingJwtParser}
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("Tests NonValidatingJwtParser functionality")
class NonValidatingJwtParserTest {

    // Sample validation parts
    private static final String SAMPLE_HEADER = "{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"test-key-id\"}";
    private static final String SAMPLE_PAYLOAD = "{\"sub\":\"1234567890\",\"name\":\"John Doe\",\"iss\":\"https://example.com\",\"exp\":1735689600}";
    private static final String SAMPLE_SIGNATURE = "signature";
    // Encoded validation parts
    private static final String ENCODED_HEADER = Base64.getUrlEncoder().encodeToString(SAMPLE_HEADER.getBytes(StandardCharsets.UTF_8));
    private static final String ENCODED_PAYLOAD = Base64.getUrlEncoder().encodeToString(SAMPLE_PAYLOAD.getBytes(StandardCharsets.UTF_8));
    private static final String ENCODED_SIGNATURE = Base64.getUrlEncoder().encodeToString(SAMPLE_SIGNATURE.getBytes(StandardCharsets.UTF_8));
    // Complete validation
    private static final String VALID_TOKEN = ENCODED_HEADER + "." + ENCODED_PAYLOAD + "." + ENCODED_SIGNATURE;
    private NonValidatingJwtParser parser;

    @BeforeEach
    void setUp() {
        parser = NonValidatingJwtParser.builder().securityEventCounter(new SecurityEventCounter()).build();
    }

    @Nested
    @DisplayName("Valid Token Tests")
    class ValidTokenTests {

        @Test
        @DisplayName("Should decode valid validation")
        void shouldDecodeValidToken() {
            // When decoding a valid token, it should not throw an exception
            DecodedJwt jwt = parser.decode(VALID_TOKEN);

            // Verify header
            assertTrue(jwt.getHeader().isPresent(), "Header should be present");
            JsonObject header = jwt.getHeader().get();
            assertEquals("RS256", header.getString("alg"), "Algorithm should be RS256");
            assertEquals("JWT", header.getString("typ"), "Type should be JWT");
            assertEquals("test-key-id", header.getString("kid"), "Key ID should match expected");

            // Verify body
            assertTrue(jwt.getBody().isPresent(), "Body should be present");
            JsonObject body = jwt.getBody().get();
            assertEquals("1234567890", body.getString("sub"), "Subject should match expected");
            assertEquals("John Doe", body.getString("name"), "Name should match expected");
            assertEquals("https://example.com", body.getString("iss"), "Issuer should match expected");
            assertEquals(1735689600, body.getInt("exp"), "Expiration should match expected");

            // Verify signature
            assertTrue(jwt.getSignature().isPresent(), "Signature should be present");

            // Verify extracted fields
            assertTrue(jwt.getIssuer().isPresent(), "Issuer should be present");
            assertEquals("https://example.com", jwt.getIssuer().get(), "Issuer should match expected");

            assertTrue(jwt.getKid().isPresent(), "Key ID should be present");
            assertEquals("test-key-id", jwt.getKid().get(), "Key ID should match expected");

            // Verify raw token
            assertEquals(VALID_TOKEN, jwt.rawToken(), "Raw token should match original");
        }
    }

    @Nested
    @DisplayName("Invalid Token Tests")
    class InvalidTokenTests {

        static Stream<Arguments> invalidTokenProvider() {
            return Stream.of(
                    Arguments.of("invalid.validation", "Should not decode a validation with invalid format"),
                    Arguments.of("invalid.base64.encoding", "Should not decode a validation with invalid Base64 encoding")
            );
        }

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("Should handle empty or null validation")
        void shouldHandleEmptyOrNullToken(String token) {
            // When decoding an empty or null token, it should throw a TokenValidationException
            TokenValidationException exception = assertThrows(TokenValidationException.class, () -> parser.decode(token),
                    "Should throw TokenValidationException for empty or null token");

            // Verify the exception has the correct event type
            assertEquals(EventType.TOKEN_EMPTY, exception.getEventType(),
                    "Exception should have TOKEN_EMPTY event type");
        }

        @ParameterizedTest
        @MethodSource("invalidTokenProvider")
        @DisplayName("Should handle invalid validation format")
        void shouldHandleInvalidTokenFormat(String invalidToken, String message) {
            // When decoding an invalid token, it should throw a TokenValidationException
            TokenValidationException exception = assertThrows(TokenValidationException.class, () -> parser.decode(invalidToken),
                    message);

            // Verify the exception has a valid event type
            assertNotNull(exception.getEventType(), "Exception should have an event type");
        }

        @Test
        @DisplayName("Should handle invalid JSON")
        void shouldHandleInvalidJson() {
            String invalidHeader = Base64.getUrlEncoder().encodeToString("not json".getBytes(StandardCharsets.UTF_8));
            String invalidToken = invalidHeader + "." + ENCODED_PAYLOAD + "." + ENCODED_SIGNATURE;

            // When decoding a token with invalid JSON, it should throw a TokenValidationException
            TokenValidationException exception = assertThrows(TokenValidationException.class, () -> parser.decode(invalidToken),
                    "Should throw TokenValidationException for token with invalid JSON");

            // Verify the exception has a valid event type
            assertNotNull(exception.getEventType(), "Exception should have an event type");
        }
    }

    @Nested
    @DisplayName("Missing Field Tests")
    class MissingFieldTests {

        @Test
        @DisplayName("Should handle missing issuer")
        void shouldHandleMissingIssuer() {
            JsonObjectBuilder payloadBuilder = Json.createObjectBuilder()
                    .add("sub", "1234567890")
                    .add("name", "John Doe")
                    .add("exp", 1735689600);

            String payloadWithoutIssuer = payloadBuilder.build().toString();
            String encodedPayloadWithoutIssuer = Base64.getUrlEncoder().encodeToString(payloadWithoutIssuer.getBytes(StandardCharsets.UTF_8));
            String tokenWithoutIssuer = ENCODED_HEADER + "." + encodedPayloadWithoutIssuer + "." + ENCODED_SIGNATURE;

            // When decoding a token without issuer, it should not throw an exception
            DecodedJwt jwt = parser.decode(tokenWithoutIssuer);

            // Verify issuer is not present
            assertFalse(jwt.getIssuer().isPresent(), "Issuer should not be present");
            assertEquals(tokenWithoutIssuer, jwt.rawToken(), "Raw validation should match the original validation");
        }

        @Test
        @DisplayName("Should handle missing kid")
        void shouldHandleMissingKid() {
            JsonObjectBuilder headerBuilder = Json.createObjectBuilder()
                    .add("alg", "RS256")
                    .add("typ", "JWT");

            String headerWithoutKid = headerBuilder.build().toString();
            String encodedHeaderWithoutKid = Base64.getUrlEncoder().encodeToString(headerWithoutKid.getBytes(StandardCharsets.UTF_8));
            String tokenWithoutKid = encodedHeaderWithoutKid + "." + ENCODED_PAYLOAD + "." + ENCODED_SIGNATURE;

            // When decoding a token without kid, it should not throw an exception
            DecodedJwt jwt = parser.decode(tokenWithoutKid);

            // Verify kid is not present
            assertFalse(jwt.getKid().isPresent(), "Key ID should not be present");
            assertEquals(tokenWithoutKid, jwt.rawToken(), "Raw validation should match the original validation");
        }
    }

    @Nested
    @DisplayName("Token Size Tests")
    class TokenSizeTests {

        @Test
        @DisplayName("Should respect max validation size")
        void shouldRespectMaxTokenSize() {
            // Create a validation that exceeds the max size
            String largeToken = "a".repeat(ParserConfig.DEFAULT_MAX_TOKEN_SIZE + 1);

            // When decoding a token that exceeds the max size, it should throw a TokenValidationException
            TokenValidationException exception = assertThrows(TokenValidationException.class, () -> parser.decode(largeToken),
                    "Should throw TokenValidationException for token exceeding max size");

            // Verify the exception has the correct event type
            assertEquals(EventType.TOKEN_SIZE_EXCEEDED, exception.getEventType(),
                    "Exception should have TOKEN_SIZE_EXCEEDED event type");
        }

        @Test
        @DisplayName("Should respect custom max validation size")
        void shouldRespectCustomMaxTokenSize() {
            // Create a validation that exceeds the custom max size but is smaller than the default
            int customMaxSize = 1024;
            String largeToken = "a".repeat(customMaxSize + 1);

            ParserConfig config = ParserConfig.builder()
                    .maxTokenSize(customMaxSize)
                    .build();

            NonValidatingJwtParser customParser = NonValidatingJwtParser.builder().securityEventCounter(new SecurityEventCounter())
                    .config(config)
                    .build();

            // When decoding a token that exceeds the custom max size, it should throw a TokenValidationException
            TokenValidationException exception = assertThrows(TokenValidationException.class, () -> customParser.decode(largeToken),
                    "Should throw TokenValidationException for token exceeding custom max size");

            // Verify the exception has the correct event type
            assertEquals(EventType.TOKEN_SIZE_EXCEEDED, exception.getEventType(),
                    "Exception should have TOKEN_SIZE_EXCEEDED event type");
        }

        @Test
        @DisplayName("Should count DECODED_PART_SIZE_EXCEEDED event")
        void shouldCountDecodedPartSizeExceededEvent() {
            // Create a validation with a very large decoded part (header)
            // Make sure it's larger than the max payload size
            String largeJson = "{\"alg\":\"RS256\",\"typ\":\"JWT\",\"kid\":\"test-key-id\",\"data\":\""
                    + "a".repeat(ParserConfig.DEFAULT_MAX_PAYLOAD_SIZE + 1000) + "\"}";

            byte[] decodedBytes = largeJson.getBytes(StandardCharsets.UTF_8);

            String encodedLargeHeader = Base64.getUrlEncoder().encodeToString(decodedBytes);
            String tokenWithLargeHeader = encodedLargeHeader + "." + ENCODED_PAYLOAD + "." + ENCODED_SIGNATURE;

            // Create a parser with a security event counter we can check and a custom config with a small max payload size
            // but a large max validation size to ensure the validation passes the validation size check
            SecurityEventCounter counter = new SecurityEventCounter();
            ParserConfig config = ParserConfig.builder()
                    .maxPayloadSize(1024) // Use a small max payload size to ensure our test data exceeds it
                    .maxTokenSize(100000) // Use a large max validation size to ensure the validation passes the validation size check
                    .build();
            NonValidatingJwtParser testParser = NonValidatingJwtParser.builder()
                    .securityEventCounter(counter)
                    .config(config)
                    .build();

            // When decoding a token with a large decoded part, it should throw a TokenValidationException
            TokenValidationException exception = assertThrows(TokenValidationException.class, () -> testParser.decode(tokenWithLargeHeader),
                    "Should throw TokenValidationException for token with large decoded part");

            // Verify the exception has the correct event type
            assertEquals(EventType.DECODED_PART_SIZE_EXCEEDED, exception.getEventType(),
                    "Exception should have DECODED_PART_SIZE_EXCEEDED event type");

            // Check the event count

            assertEquals(1, counter.getCount(SecurityEventCounter.EventType.DECODED_PART_SIZE_EXCEEDED),
                    "Should count DECODED_PART_SIZE_EXCEEDED event");
        }
    }

    @Nested
    @DisplayName("Builder Tests")
    class BuilderTests {

        @Test
        @DisplayName("Should create builder with defaults")
        void shouldCreateBuilderWithDefaults() {
            NonValidatingJwtParser defaultParser = NonValidatingJwtParser.builder().securityEventCounter(new SecurityEventCounter()).build();
            assertNotNull(defaultParser, "Should create a parser with default settings");

            // Verify it works with a valid validation
            DecodedJwt result = defaultParser.decode(VALID_TOKEN);
            assertNotNull(result, "Default parser should decode a valid validation");
            assertEquals(VALID_TOKEN, result.rawToken(), "Raw validation should match the original validation");
        }
    }

    @Nested
    @DisplayName("Security Event Tests")
    class SecurityEventTests {

        @Test
        @DisplayName("Should count FAILED_TO_DECODE_JWT event")
        void shouldCountFailedToDecodeJwtEvent() {
            // Create a parser with a security event counter we can check
            SecurityEventCounter counter = new SecurityEventCounter();
            NonValidatingJwtParser testParser = NonValidatingJwtParser.builder()
                    .securityEventCounter(counter)
                    .build();

            // When - try to decode an invalid validation that will cause a general decoding failure
            // Create a validation with 3 parts but invalid Base64 in the middle part to trigger a JSON parsing exception
            String invalidToken = "eyJhbGciOiJIUzI1NiJ9.invalid_base64_payload.signature";

            // When decoding an invalid token, it should throw a TokenValidationException
            TokenValidationException exception = assertThrows(TokenValidationException.class, () -> testParser.decode(invalidToken),
                    "Should throw TokenValidationException for token with invalid Base64 encoding");

            // Verify the exception has the correct event type
            assertEquals(EventType.FAILED_TO_DECODE_JWT, exception.getEventType(),
                    "Exception should have FAILED_TO_DECODE_JWT event type");

            // Verify the counter was incremented
            assertEquals(1, counter.getCount(SecurityEventCounter.EventType.FAILED_TO_DECODE_JWT),
                    "Should count FAILED_TO_DECODE_JWT event");
        }

        @Test
        @DisplayName("Should count TOKEN_SIZE_EXCEEDED event")
        void shouldCountTokenSizeExceededEvent() {
            // Create a parser with a security event counter we can check
            SecurityEventCounter counter = new SecurityEventCounter();
            ParserConfig config = ParserConfig.builder()
                    .maxTokenSize(100) // Use a small max validation size to ensure our test data exceeds it
                    .build();
            NonValidatingJwtParser testParser = NonValidatingJwtParser.builder()
                    .securityEventCounter(counter)
                    .config(config)
                    .build();

            // When - try to decode a validation that exceeds the max size
            String largeToken = "a".repeat(config.getMaxTokenSize() + 1);

            // When decoding a token that exceeds the max size, it should throw a TokenValidationException
            TokenValidationException exception = assertThrows(TokenValidationException.class, () -> testParser.decode(largeToken),
                    "Should throw TokenValidationException for token exceeding max size");

            // Verify the exception has the correct event type
            assertEquals(EventType.TOKEN_SIZE_EXCEEDED, exception.getEventType(),
                    "Exception should have TOKEN_SIZE_EXCEEDED event type");

            // Verify the counter was incremented
            assertEquals(1, counter.getCount(SecurityEventCounter.EventType.TOKEN_SIZE_EXCEEDED),
                    "Should count TOKEN_SIZE_EXCEEDED event");
        }
    }

    @Nested
    @DisplayName("Security Measures Tests")
    class SecurityMeasuresTest {

        @Test
        @DisplayName("Should respect JSON depth limits")
        void shouldRespectJsonDepthLimits() {
            // Create a deeply nested JSON structure that exceeds the max depth
            StringBuilder nestedJson = new StringBuilder("{");
            for (int i = 0; i < ParserConfig.DEFAULT_MAX_DEPTH + 1; i++) {
                nestedJson.append("\"level").append(i).append("\":{");
            }
            // Close all the nested objects
            nestedJson.append("}".repeat(ParserConfig.DEFAULT_MAX_DEPTH + 1));

            String encodedNestedJson = Base64.getUrlEncoder().encodeToString(nestedJson.toString().getBytes(StandardCharsets.UTF_8));
            String tokenWithDeepJson = encodedNestedJson + "." + ENCODED_PAYLOAD + "." + ENCODED_SIGNATURE;

            // When decoding a token with JSON exceeding max depth, it should throw a TokenValidationException
            TokenValidationException exception = assertThrows(TokenValidationException.class, () -> parser.decode(tokenWithDeepJson),
                    "Should throw TokenValidationException for token with JSON exceeding max depth");

            // Verify the exception has a valid event type
            assertNotNull(exception.getEventType(), "Exception should have an event type");
        }

        @Test
        @DisplayName("Should handle large JSON arrays")
        void shouldHandleLargeJsonArrays() {
            // Create a JSON with a large array
            StringBuilder largeArrayJson = new StringBuilder("{\"array\":[");
            for (int i = 0; i < ParserConfig.DEFAULT_MAX_ARRAY_SIZE - 1; i++) {
                if (i > 0) {
                    largeArrayJson.append(",");
                }
                largeArrayJson.append("\"item").append(i).append("\"");
            }
            largeArrayJson.append("]}");

            String encodedLargeArrayJson = Base64.getUrlEncoder().encodeToString(largeArrayJson.toString().getBytes(StandardCharsets.UTF_8));
            String tokenWithLargeArray = encodedLargeArrayJson + "." + ENCODED_PAYLOAD + "." + ENCODED_SIGNATURE;

            // When decoding a token with large JSON array within limits, it should not throw an exception
            DecodedJwt result = parser.decode(tokenWithLargeArray);

            // Verify the array was parsed correctly
            assertTrue(result.getHeader().isPresent(), "Header should be present");
            JsonObject header = result.getHeader().get();
            assertTrue(header.containsKey("array"), "Array should be present in header");
        }

        @Test
        @DisplayName("Should handle large JSON strings")
        void shouldHandleLargeJsonStrings() {
            // Create a JSON with a large string
            String largeStringJson = "{\"largeString\":\"" + "a".repeat(ParserConfig.DEFAULT_MAX_STRING_SIZE - 100) + "\"}";

            String encodedLargeStringJson = Base64.getUrlEncoder().encodeToString(largeStringJson.getBytes(StandardCharsets.UTF_8));
            String tokenWithLargeString = encodedLargeStringJson + "." + ENCODED_PAYLOAD + "." + ENCODED_SIGNATURE;

            // When decoding a token with large JSON string within limits, it should not throw an exception
            DecodedJwt result = parser.decode(tokenWithLargeString);

            // Verify the string was parsed correctly
            assertTrue(result.getHeader().isPresent(), "Header should be present");
            JsonObject header = result.getHeader().get();
            assertTrue(header.containsKey("largeString"), "Large string should be present in header");
        }

        @Test
        @DisplayName("Should use cached JsonReaderFactory")
        void shouldUseCachedJsonReaderFactory() {
            // This test verifies that the JsonReaderFactory is cached and reused
            // We can't directly test the caching behavior, but we can verify that
            // the parser works correctly after the JsonReaderFactory is created

            // First decode should create and cache the JsonReaderFactory
            DecodedJwt result1 = parser.decode(VALID_TOKEN);

            // Second decode should use the cached JsonReaderFactory
            DecodedJwt result2 = parser.decode(VALID_TOKEN);

            // Both results should be equal
            assertEquals(result1.rawToken(), result2.rawToken(),
                    "Both decodes should produce the same result");
        }
    }
}
