/*
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cuioss.jwt.validation.jwks;

import de.cuioss.jwt.validation.jwks.key.JWKSKeyLoader;
import de.cuioss.jwt.validation.jwks.key.KeyInfo;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.test.InMemoryJWKSFactory;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.Key;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@EnableTestLogger(debug = {JWKSKeyLoader.class, JwksLoaderFactory.class})
@DisplayName("Tests in-memory JWKSKeyLoader functionality")
class InMemoryJwksLoaderTest {

    private JwksLoader inMemoryJwksLoader;
    private SecurityEventCounter securityEventCounter;

    @BeforeEach
    void setUp() {
        // Initialize the SecurityEventCounter
        securityEventCounter = new SecurityEventCounter();

        // Create the InMemoryJwksLoader with the valid content
        inMemoryJwksLoader = JwksLoaderFactory.createInMemoryLoader(InMemoryJWKSFactory.createDefaultJwks(), securityEventCounter);
    }


    @Test
    @DisplayName("Should load and parse JWKS from string")
    void shouldLoadAndParseJwksFromString() {
        // When
        Optional<Key> key = inMemoryJwksLoader.getKeyInfo(InMemoryJWKSFactory.DEFAULT_KEY_ID).map(KeyInfo::getKey);

        // Then
        assertTrue(key.isPresent(), "Key should be present");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.DEBUG, "Resolving key loader for in-memory JWKS data");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.DEBUG, "Successfully loaded");
    }

    @Test
    @DisplayName("Should return empty when kid is null")
    void shouldReturnEmptyWhenKidIsNull() {
        // When
        Optional<Key> key = inMemoryJwksLoader.getKeyInfo(null).map(KeyInfo::getKey);

        // Then
        assertFalse(key.isPresent(), "Key should not be present when kid is null");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.DEBUG, "Key ID is null or empty");
    }

    @Test
    @DisplayName("Should return empty when kid is not found")
    void shouldReturnEmptyWhenKidNotFound() {
        // When
        Optional<Key> key = inMemoryJwksLoader.getKeyInfo("unknown-kid").map(KeyInfo::getKey);

        // Then
        assertFalse(key.isPresent(), "Key should not be present when kid is not found");
    }

    @Test
    @DisplayName("Should get first key when available")
    void shouldGetFirstKeyWhenAvailable() {
        // When
        Optional<Key> key = inMemoryJwksLoader.getFirstKeyInfo().map(KeyInfo::getKey);

        // Then
        assertTrue(key.isPresent(), "First key should be present");
    }

    @Test
    @DisplayName("Should handle invalid JWKS format")
    void shouldHandleInvalidJwksFormat() {

        // Given
        String invalidJwksContent = InMemoryJWKSFactory.createInvalidJson();
        JwksLoader invalidJwksLoader = JwksLoaderFactory.createInMemoryLoader(invalidJwksContent, securityEventCounter);

        // When
        Optional<Key> key = invalidJwksLoader.getKeyInfo(InMemoryJWKSFactory.DEFAULT_KEY_ID).map(KeyInfo::getKey);

        // Then
        assertFalse(key.isPresent(), "Key should not be present when JWKS is invalid");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "Failed to parse JWKS JSON");

    }

    @Test
    @DisplayName("Should handle missing required fields in JWK")
    void shouldHandleMissingRequiredFieldsInJwk() {
        // Given
        String missingFieldsJwksContent = InMemoryJWKSFactory.createJwksWithMissingFields(InMemoryJWKSFactory.DEFAULT_KEY_ID);
        JwksLoader missingFieldsJwksLoader = JwksLoaderFactory.createInMemoryLoader(missingFieldsJwksContent, securityEventCounter);

        // When
        Optional<Key> key = missingFieldsJwksLoader.getKeyInfo(InMemoryJWKSFactory.DEFAULT_KEY_ID).map(KeyInfo::getKey);

        // Then
        assertFalse(key.isPresent(), "Key should not be present when JWK is missing required fields");
        LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN, "Failed to parse RSA key");
    }

    @Test
    @DisplayName("Should update keys when refreshed with new data")
    void shouldUpdateKeysWhenRefreshedWithNewData() {
        // Given
        Optional<Key> initialKey = inMemoryJwksLoader.getKeyInfo(InMemoryJWKSFactory.DEFAULT_KEY_ID).map(KeyInfo::getKey);
        assertTrue(initialKey.isPresent(), "Initial key should be present");

        // When - create a new loader with updated content
        String updatedJwksContent = InMemoryJWKSFactory.createValidJwksWithKeyId("updated-key-id");
        JwksLoader updatedLoader = JwksLoaderFactory.createInMemoryLoader(updatedJwksContent, securityEventCounter);

        // Then - verify the new loader has the updated key
        Optional<Key> oldKey = updatedLoader.getKeyInfo(InMemoryJWKSFactory.DEFAULT_KEY_ID).map(KeyInfo::getKey);
        assertFalse(oldKey.isPresent(), "Old key should not be present in the new loader");

        Optional<Key> newKey = updatedLoader.getKeyInfo("updated-key-id").map(KeyInfo::getKey);
        assertTrue(newKey.isPresent(), "New key should be present in the new loader");
    }

    @Test
    @DisplayName("Should return correct keySet")
    void shouldReturnCorrectKeySet() {
        // Given
        // The loader is already initialized with valid JWKS content in setUp()

        // When
        var keySet = inMemoryJwksLoader.keySet();

        // Then
        assertFalse(keySet.isEmpty(), "KeySet should not be empty");
        assertTrue(keySet.contains(InMemoryJWKSFactory.DEFAULT_KEY_ID), "KeySet should contain the test key ID");
        assertEquals(1, keySet.size(), "KeySet should contain exactly one key");
    }

    @Test
    @DisplayName("Should create loader from factory method")
    void shouldCreateLoaderFromFactoryMethod() {
        // Given
        String jwksContent = InMemoryJWKSFactory.createDefaultJwks();

        // When
        JwksLoader loader = JwksLoaderFactory.createInMemoryLoader(jwksContent, securityEventCounter);

        // Then
        assertInstanceOf(JWKSKeyLoader.class, loader, "Loader should be an instance of JWKSKeyLoader");
        Optional<Key> key = loader.getKeyInfo(InMemoryJWKSFactory.DEFAULT_KEY_ID).map(KeyInfo::getKey);
        assertTrue(key.isPresent(), "Key should be present");
    }
}
