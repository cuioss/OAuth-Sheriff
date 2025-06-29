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
package de.cuioss.jwt.validation.jwks.parser;

import de.cuioss.jwt.validation.jwks.key.KeyInfo;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.test.InMemoryJWKSFactory;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import jakarta.json.Json;
import jakarta.json.JsonObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger(warn = KeyProcessor.class, debug = KeyProcessor.class)
@DisplayName("Tests KeyProcessor functionality")
class KeyProcessorTest {

    private KeyProcessor processor;
    private SecurityEventCounter securityEventCounter;

    @BeforeEach
    void setUp() {
        securityEventCounter = new SecurityEventCounter();
        processor = new KeyProcessor(securityEventCounter);
    }

    @Nested
    @DisplayName("RSA Key Processing")
    class RsaKeyProcessingTests {

        @Test
        @DisplayName("Should process valid RSA key")
        void shouldProcessValidRsaKey() {
            // Given a valid RSA JWK with actual Base64-encoded values
            JsonObject rsaJwk = Json.createObjectBuilder()
                    .add("kty", "RSA")
                    .add("kid", "test-key")
                    .add("alg", "RS256")
                    .add("n", "0vx7agoebGcQSuuPiLJXZptN9nndrQmbPFRP_gdHPfP7ZjwuvUAqW3AJTEqiVjjpX0yCbZE6krXrOu8n4EJhZjJWgPUQJkIFJTzN6vkF7Oij_7Vo5w8VjL-Aq2eCKr6w")
                    .add("e", "AQAB")
                    .build();

            // When processing
            Optional<KeyInfo> result = processor.processKey(rsaJwk);

            // Then should succeed
            assertTrue(result.isPresent(), "Should successfully process RSA key");
            KeyInfo keyInfo = result.get();
            assertEquals("RSA", keyInfo.key().getAlgorithm(), "Should have RSA algorithm");
            assertEquals("test-key", keyInfo.keyId(), "Should have correct key ID");
            assertEquals("RS256", keyInfo.algorithm(), "Should have signature algorithm");
        }

        @Test
        @DisplayName("Should use default algorithm for RSA key without alg")
        void shouldUseDefaultAlgorithmForRsaKey() {
            // Given an RSA JWK without algorithm
            JsonObject rsaJwk = Json.createObjectBuilder()
                    .add("kty", "RSA")
                    .add("kid", "test-key")
                    .add("n", "0vx7agoebGcQSuuPiLJXZptN9nndrQmbPFRP_gdHPfP7ZjwuvUAqW3AJTEqiVjjpX0yCbZE6krXrOu8n4EJhZjJWgPUQJkIFJTzN6vkF7Oij_7Vo5w8VjL-Aq2eCKr6w")
                    .add("e", "AQAB")
                    .build();

            // When processing
            Optional<KeyInfo> result = processor.processKey(rsaJwk);

            // Then should use default algorithm
            assertTrue(result.isPresent(), "Should successfully process RSA key");
            assertEquals("RS256", result.get().algorithm(), "Should default to RS256");
        }

        @Test
        @DisplayName("Should handle invalid RSA key parameters")
        void shouldHandleInvalidRsaKeyParameters() {
            // Given an RSA JWK with invalid parameters
            JsonObject invalidRsaJwk = Json.createObjectBuilder()
                    .add("kty", "RSA")
                    .add("kid", "test-key")
                    .add("n", "invalid-modulus")
                    .add("e", "invalid-exponent")
                    .build();

            // When processing
            Optional<KeyInfo> result = processor.processKey(invalidRsaJwk);

            // Then should fail gracefully
            assertFalse(result.isPresent(), "Should fail to process invalid RSA key");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "Failed to parse RSA key");
        }
    }

    @Nested
    @DisplayName("EC Key Processing")
    class EcKeyProcessingTests {

        @Test
        @DisplayName("Should process valid EC key")
        void shouldProcessValidEcKey() {
            // Given a valid EC JWK with actual Base64-encoded values
            JsonObject ecJwk = Json.createObjectBuilder()
                    .add("kty", "EC")
                    .add("kid", "test-ec-key")
                    .add("alg", "ES256")
                    .add("crv", "P-256")
                    .add("x", "MKBCTNIcKUSDii11ySs3526iDZ8AiTo7Tu6KPAqv7D4")
                    .add("y", "4Etl6SRW2YiLUrN5vfvVHuhp7x8PxltmWWlbbM4IFyM")
                    .build();

            // When processing
            Optional<KeyInfo> result = processor.processKey(ecJwk);

            // Then should succeed
            assertTrue(result.isPresent(), "Should successfully process EC key");
            KeyInfo keyInfo = result.get();
            assertEquals("EC", keyInfo.key().getAlgorithm(), "Should have EC algorithm");
            assertEquals("test-ec-key", keyInfo.keyId(), "Should have correct key ID");
            assertEquals("ES256", keyInfo.algorithm(), "Should have signature algorithm");
        }

        @Test
        @DisplayName("Should determine algorithm from curve for EC key")
        void shouldDetermineAlgorithmFromCurve() {
            // Given an EC JWK with curve but no algorithm
            JsonObject ecJwk = Json.createObjectBuilder()
                    .add("kty", "EC")
                    .add("kid", "test-key")
                    .add("crv", "P-256")
                    .add("x", "MKBCTNIcKUSDii11ySs3526iDZ8AiTo7Tu6KPAqv7D4")
                    .add("y", "4Etl6SRW2YiLUrN5vfvVHuhp7x8PxltmWWlbbM4IFyM")
                    .build();

            // When processing
            Optional<KeyInfo> result = processor.processKey(ecJwk);

            // Then should determine algorithm from curve
            assertTrue(result.isPresent(), "Should successfully process EC key");
            assertEquals("ES256", result.get().algorithm(), "Should determine ES256 from P-256 curve");
        }

        @Test
        @DisplayName("Should handle invalid EC key parameters")
        void shouldHandleInvalidEcKeyParameters() {
            // Given an EC JWK with invalid parameters
            JsonObject invalidEcJwk = Json.createObjectBuilder()
                    .add("kty", "EC")
                    .add("kid", "test-key")
                    .add("crv", "P-256")
                    .add("x", "invalid-x")
                    .add("y", "invalid-y")
                    .build();

            // When processing
            Optional<KeyInfo> result = processor.processKey(invalidEcJwk);

            // Then should fail gracefully
            assertFalse(result.isPresent(), "Should fail to process invalid EC key");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "Failed to parse EC key");
        }
    }

    @Nested
    @DisplayName("General Key Processing")
    class GeneralKeyProcessingTests {

        @Test
        @DisplayName("Should handle key without kty")
        void shouldHandleKeyWithoutKty() {
            // Given a JWK without key type
            JsonObject jwk = Json.createObjectBuilder()
                    .add("kid", "test-key")
                    .build();

            // When processing
            Optional<KeyInfo> result = processor.processKey(jwk);

            // Then should fail
            assertFalse(result.isPresent(), "Should fail to process key without kty");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "JWK is missing required field 'kty'");
        }

        @Test
        @DisplayName("Should handle unsupported key type")
        void shouldHandleUnsupportedKeyType() {
            // Given a JWK with unsupported key type
            JsonObject jwk = Json.createObjectBuilder()
                    .add("kty", "oct")
                    .add("kid", "test-key")
                    .build();

            // When processing
            Optional<KeyInfo> result = processor.processKey(jwk);

            // Then should fail
            assertFalse(result.isPresent(), "Should fail to process unsupported key type");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.DEBUG, "Unsupported key type: oct");
        }

        @Test
        @DisplayName("Should use default key ID when missing")
        void shouldUseDefaultKeyIdWhenMissing() {
            // Given a valid RSA JWK without key ID
            JsonObject rsaJwk = Json.createObjectBuilder()
                    .add("kty", "RSA")
                    .add("n", "0vx7agoebGcQSuuPiLJXZptN9nndrQmbPFRP_gdHPfP7ZjwuvUAqW3AJTEqiVjjpX0yCbZE6krXrOu8n4EJhZjJWgPUQJkIFJTzN6vkF7Oij_7Vo5w8VjL-Aq2eCKr6w")
                    .add("e", "AQAB")
                    .build();

            // When processing
            Optional<KeyInfo> result = processor.processKey(rsaJwk);

            // Then should use default key ID
            assertTrue(result.isPresent(), "Should successfully process key");
            assertEquals("default-key-id", result.get().keyId(), "Should use default key ID");
        }
    }

    @Nested
    @DisplayName("Security Event Tracking")
    class SecurityEventTrackingTests {

        @Test
        @DisplayName("Should track processing failures")
        void shouldTrackProcessingFailures() {
            // Given invalid key
            JsonObject invalidJwk = Json.createObjectBuilder()
                    .add("kty", "RSA")
                    .add("kid", "test-key")
                    .add("n", "invalid")
                    .add("e", "invalid")
                    .build();

            // When processing
            processor.processKey(invalidJwk);

            // Then should track security event
            assertTrue(securityEventCounter.getCount(SecurityEventCounter.EventType.JWKS_JSON_PARSE_FAILED) > 0,
                    "Should track processing failures");
        }
    }
}