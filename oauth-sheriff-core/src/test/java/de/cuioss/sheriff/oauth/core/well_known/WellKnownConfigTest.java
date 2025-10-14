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
package de.cuioss.sheriff.oauth.core.well_known;

import de.cuioss.http.client.handler.SecureSSLContextProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import java.net.URI;
import java.security.NoSuchAlgorithmException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tests WellKnownConfig")
class WellKnownConfigTest {

    private static final String TEST_WELL_KNOWN_URL = "https://example.com/.well-known/openid-configuration";
    private static final URI TEST_WELL_KNOWN_URI = URI.create(TEST_WELL_KNOWN_URL);

    @Test
    @DisplayName("Should create config with URL string")
    void shouldCreateConfigWithUrl() {
        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl(TEST_WELL_KNOWN_URL)
                .build();

        assertNotNull(config.getHttpHandler());
        assertEquals(TEST_WELL_KNOWN_URI.toString(), config.getHttpHandler().getUri().toString());
    }

    @Test
    @DisplayName("Should create config with URI")
    void shouldCreateConfigWithUri() {
        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUri(TEST_WELL_KNOWN_URI)
                .build();

        assertNotNull(config.getHttpHandler());
        assertEquals(TEST_WELL_KNOWN_URI, config.getHttpHandler().getUri());
    }

    @Test
    @DisplayName("Should create config with custom timeouts")
    void shouldCreateConfigWithCustomTimeouts() {
        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl(TEST_WELL_KNOWN_URL)
                .connectTimeoutSeconds(5)
                .readTimeoutSeconds(10)
                .build();

        assertNotNull(config.getHttpHandler());
        // HTTP handler should be created with the custom timeouts (can't directly verify timeouts from HttpHandler)
    }

    @Test
    @DisplayName("Should fail when no well-known URI configured")
    void shouldFailWhenNoWellKnownUri() {
        WellKnownConfig.WellKnownConfigBuilder builder = WellKnownConfig.builder();

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, builder::build);
        assertTrue(exception.getMessage().contains("Invalid well-known endpoint configuration"));
    }

    @Test
    @DisplayName("Should use default exponential backoff when RetryStrategy not specified")
    void shouldUseDefaultRetryStrategy() {
        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl(TEST_WELL_KNOWN_URL)
                .build();

        assertNotNull(config.getRetryStrategy());
        // Should be exponential backoff strategy by default
        assertNotNull(config.getHttpHandler());
    }

    @Test
    @DisplayName("Should fail with invalid timeout values")
    void shouldFailWithInvalidTimeouts() {
        // Test invalid connect timeout
        var builderWithInvalidConnectTimeout = WellKnownConfig.builder()
                .wellKnownUrl(TEST_WELL_KNOWN_URL)
                .connectTimeoutSeconds(0);

        assertThrows(IllegalArgumentException.class, builderWithInvalidConnectTimeout::build);

        // Test invalid read timeout
        var builderWithInvalidReadTimeout = WellKnownConfig.builder()
                .wellKnownUrl(TEST_WELL_KNOWN_URL)
                .readTimeoutSeconds(-1);

        assertThrows(IllegalArgumentException.class, builderWithInvalidReadTimeout::build);
    }

    @Test
    @DisplayName("Should create config with custom SSL context")
    void shouldCreateConfigWithCustomSslContext() throws NoSuchAlgorithmException {
        // Create a custom SSL context
        SSLContext customSslContext = SSLContext.getDefault();

        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl(TEST_WELL_KNOWN_URL)
                .sslContext(customSslContext)
                .build();

        assertNotNull(config.getHttpHandler());
        // The SSL context is set on the underlying HTTP handler
        // We can't directly verify it but the builder method should work without exceptions
    }

    @Test
    @DisplayName("Should support sslContext() method in builder API")
    void shouldSupportSslContextBuilderMethod() throws NoSuchAlgorithmException {
        // API test: verify sslContext() method exists and returns builder for chaining
        SSLContext sslContext = SSLContext.getDefault();

        WellKnownConfig.WellKnownConfigBuilder builder = WellKnownConfig.builder()
                .wellKnownUrl(TEST_WELL_KNOWN_URL)
                .sslContext(sslContext);

        // Verify the method returns the builder instance for chaining
        assertNotNull(builder);
        assertInstanceOf(WellKnownConfig.WellKnownConfigBuilder.class, builder);

        // Verify the builder can still build successfully
        WellKnownConfig config = builder.build();
        assertNotNull(config);
    }

    @Test
    @DisplayName("Should support tlsVersions() method in builder API")
    void shouldSupportTlsVersionsBuilderMethod() {
        // API test: verify tlsVersions() method exists and returns builder for chaining
        // Using the existing SecureSSLContextProvider with its constants
        SecureSSLContextProvider tlsProvider = new SecureSSLContextProvider(SecureSSLContextProvider.TLS_V1_3);

        WellKnownConfig.WellKnownConfigBuilder builder = WellKnownConfig.builder()
                .wellKnownUrl(TEST_WELL_KNOWN_URL)
                .tlsVersions(tlsProvider);

        // Verify the method returns the builder instance for chaining
        assertNotNull(builder);
        assertInstanceOf(WellKnownConfig.WellKnownConfigBuilder.class, builder);

        // Verify the builder can still build successfully
        WellKnownConfig config = builder.build();
        assertNotNull(config);
    }

    @Test
    @DisplayName("Should allow chaining of sslContext() and tlsVersions() methods")
    void shouldAllowChainingOfSslContextAndTlsVersionsMethods() throws NoSuchAlgorithmException {
        // API test: verify both methods can be chained together
        SSLContext sslContext = SSLContext.getDefault();
        SecureSSLContextProvider tlsProvider = new SecureSSLContextProvider(SecureSSLContextProvider.TLS_V1_2);

        // Test method chaining
        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl(TEST_WELL_KNOWN_URL)
                .sslContext(sslContext)           // First method
                .tlsVersions(tlsProvider)         // Second method
                .connectTimeoutSeconds(10)        // Other methods still work
                .readTimeoutSeconds(20)
                .build();

        assertNotNull(config);
        assertNotNull(config.getHttpHandler());
    }
}