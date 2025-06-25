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
package de.cuioss.jwt.validation.jwks.key;

import de.cuioss.jwt.validation.jwks.JwksType;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.test.InMemoryJWKSFactory;
import de.cuioss.jwt.validation.test.InMemoryKeyMaterialHandler;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Extended tests for {@link JWKSKeyLoader} to improve coverage.
 * <p>
 * This test class focuses on testing edge cases and additional functionality
 * that might not be covered by the main test class.
 */
@EnableTestLogger(debug = {JWKSKeyLoader.class}, trace = {JWKSKeyLoader.class})
@DisplayName("Extended Tests for JWKSKeyLoader")
class JWKSKeyLoaderExtendedTest {

    private SecurityEventCounter securityEventCounter;

    @BeforeEach
    void setUp() {
        securityEventCounter = new SecurityEventCounter();
    }

    @Nested
    @DisplayName("EC Key Handling")
    class EcKeyHandlingTests {
        @Test
        @DisplayName("Should parse EC key")
        void shouldParseEcKey() {
            String jwksContent = InMemoryKeyMaterialHandler.createJwks(InMemoryKeyMaterialHandler.Algorithm.ES256, "ec-key-id");

            JWKSKeyLoader keyLoader = JWKSKeyLoader.builder()
                    .originalString(jwksContent)
                    .securityEventCounter(securityEventCounter)
                    .build();

            Optional<KeyInfo> keyInfo = keyLoader.getKeyInfo("ec-key-id");
            assertTrue(keyInfo.isPresent(), "EC key info should be present");
            assertEquals("ES256", keyInfo.get().algorithm(), "Algorithm should be ES256");
        }

        @Test
        @DisplayName("Should determine EC algorithm from curve when not specified")
        void shouldDetermineEcAlgorithmFromCurve() {
            String jwksContent = """
                    {
                      "keys": [
                        {
                          "kty": "EC",
                          "kid": "ec-key-id",
                          "crv": "P-256",
                          "x": "MKBCTNIcKUSDii11ySs3526iDZ8AiTo7Tu6KPAqv7D4",
                          "y": "4Etl6SRW2YiLUrN5vfvVHuhp7x8PxltmWWlbbM4IFyM"
                        }
                      ]
                    }
                    """;

            JWKSKeyLoader keyLoader = JWKSKeyLoader.builder()
                    .originalString(jwksContent)
                    .securityEventCounter(securityEventCounter)
                    .build();

            Optional<KeyInfo> keyInfo = keyLoader.getKeyInfo("ec-key-id");
            assertTrue(keyInfo.isPresent(), "EC key info should be present");
            assertEquals("ES256", keyInfo.get().algorithm(), "Algorithm should be determined from curve");
        }
    }

    @Nested
    @DisplayName("JWKS Structure Validation")
    class JwksStructureValidationTests {
        @Test
        @DisplayName("Should reject JWKS with excessive properties")
        void shouldRejectJwksWithExcessiveProperties() {
            StringBuilder jwksContent = new StringBuilder("{");
            for (int i = 0; i < 15; i++) {
                jwksContent.append("\"prop").append(i).append("\":\"value").append(i).append("\",");
            }
            jwksContent.append("\"keys\":[{\"kty\":\"RSA\",\"kid\":\"test-key-id\"}]}");

            JWKSKeyLoader keyLoader = JWKSKeyLoader.builder()
                    .originalString(jwksContent.toString())
                    .securityEventCounter(securityEventCounter)
                    .build();

            assertFalse(keyLoader.isNotEmpty(), "Loader should reject JWKS with excessive properties");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "JWKS object has excessive number of properties");
        }

        @Test
        @DisplayName("Should reject JWKS with empty keys array")
        void shouldRejectJwksWithEmptyKeysArray() {
            String jwksContent = "{\"keys\":[]}";

            JWKSKeyLoader keyLoader = JWKSKeyLoader.builder()
                    .originalString(jwksContent)
                    .securityEventCounter(securityEventCounter)
                    .build();

            assertFalse(keyLoader.isNotEmpty(), "Loader should reject JWKS with empty keys array");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "JWKS keys array is empty");
        }

        @Test
        @DisplayName("Should reject JWKS with excessive keys")
        void shouldRejectJwksWithExcessiveKeys() {
            StringBuilder jwksContent = new StringBuilder("{\"keys\":[");
            for (int i = 0; i < 60; i++) {
                if (i > 0) {
                    jwksContent.append(",");
                }
                jwksContent.append("{\"kty\":\"RSA\",\"kid\":\"key-").append(i).append("\"}");
            }
            jwksContent.append("]}");

            JWKSKeyLoader keyLoader = JWKSKeyLoader.builder()
                    .originalString(jwksContent.toString())
                    .securityEventCounter(securityEventCounter)
                    .build();

            assertFalse(keyLoader.isNotEmpty(), "Loader should reject JWKS with excessive keys");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "JWKS keys array exceeds maximum size");
        }
    }

    @Nested
    @DisplayName("Key Parameter Validation")
    class KeyParameterValidationTests {
        @Test
        @DisplayName("Should reject key with unsupported key type")
        void shouldRejectKeyWithUnsupportedKeyType() {
            String jwksContent = """
                    {
                      "keys": [
                        {
                          "kty": "UNSUPPORTED",
                          "kid": "unsupported-key-id"
                        }
                      ]
                    }
                    """;

            JWKSKeyLoader keyLoader = JWKSKeyLoader.builder()
                    .originalString(jwksContent)
                    .securityEventCounter(securityEventCounter)
                    .build();

            Optional<KeyInfo> keyInfo = keyLoader.getKeyInfo("unsupported-key-id");
            assertFalse(keyInfo.isPresent(), "Key with unsupported type should not be present");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "Unsupported key type");
        }

        @Test
        @DisplayName("Should reject key with excessively long key ID")
        void shouldRejectKeyWithExcessivelyLongKeyId() {
            String longKeyId = "a".repeat(150);
            String jwksContent = """
                    {
                      "keys": [
                        {
                          "kty": "RSA",
                          "kid": "%s"
                        }
                      ]
                    }
                    """.formatted(longKeyId);

            JWKSKeyLoader keyLoader = JWKSKeyLoader.builder()
                    .originalString(jwksContent)
                    .securityEventCounter(securityEventCounter)
                    .build();

            Optional<KeyInfo> keyInfo = keyLoader.getKeyInfo(longKeyId);
            assertFalse(keyInfo.isPresent(), "Key with excessively long key ID should not be present");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "Key ID exceeds maximum length");
        }

        @Test
        @DisplayName("Should reject key with invalid algorithm")
        void shouldRejectKeyWithInvalidAlgorithm() {
            String jwksContent = """
                    {
                      "keys": [
                        {
                          "kty": "RSA",
                          "kid": "invalid-alg-key-id",
                          "alg": "INVALID_ALG"
                        }
                      ]
                    }
                    """;

            JWKSKeyLoader keyLoader = JWKSKeyLoader.builder()
                    .originalString(jwksContent)
                    .securityEventCounter(securityEventCounter)
                    .build();

            Optional<KeyInfo> keyInfo = keyLoader.getKeyInfo("invalid-alg-key-id");
            assertFalse(keyInfo.isPresent(), "Key with invalid algorithm should not be present");
            LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "Invalid or unsupported algorithm");
        }
    }

    @Nested
    @DisplayName("Factory Method and Builder Tests")
    class FactoryMethodAndBuilderTests {
        @Test
        @DisplayName("Should create loader using factory method")
        void shouldCreateLoaderUsingFactoryMethod() {
            String jwksContent = InMemoryJWKSFactory.createDefaultJwks();

            JWKSKeyLoader keyLoader = JWKSKeyLoader.create(
                    jwksContent,
                    "test-etag",
                    null,
                    securityEventCounter,
                    JwksType.MEMORY);

            assertTrue(keyLoader.isNotEmpty(), "Loader created with factory method should parse JWKS");
            assertEquals(JwksType.MEMORY, keyLoader.getJwksType(), "Loader should have correct JWKS type");
        }

        @Test
        @DisplayName("Should throw exception when builder is missing originalString")
        void shouldThrowExceptionWhenBuilderIsMissingOriginalString() {
            JWKSKeyLoader.JWKSKeyLoaderBuilder builder = JWKSKeyLoader.builder();
            assertThrows(IllegalArgumentException.class, builder::build,
                    "Builder should throw exception when originalString is missing");
        }

        @Test
        @DisplayName("Should throw exception when builder is missing securityEventCounter")
        void shouldThrowExceptionWhenBuilderIsMissingSecurityEventCounter() {
            JWKSKeyLoader.JWKSKeyLoaderBuilder content = JWKSKeyLoader.builder()
                .originalString("content");
            assertThrows(IllegalArgumentException.class, content::build,
                    "Builder should throw exception when securityEventCounter is missing");
        }
    }
}
