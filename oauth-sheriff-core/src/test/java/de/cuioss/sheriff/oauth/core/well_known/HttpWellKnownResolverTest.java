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

import de.cuioss.http.client.LoaderStatus;
import de.cuioss.http.client.retry.RetryStrategy;
import de.cuioss.sheriff.oauth.core.json.WellKnownResult;
import de.cuioss.sheriff.oauth.core.test.dispatcher.WellKnownDispatcher;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.test.mockwebserver.EnableMockWebServer;
import de.cuioss.test.mockwebserver.URIBuilder;
import lombok.Getter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for {@link HttpWellKnownResolver} with MockWebServer integration.
 * <p>
 * These tests verify the resolver works correctly with real HTTP operations,
 * including timing variants, error scenarios, and caching behavior.
 *
 * @author Oliver Wolff
 */
@EnableTestLogger
@DisplayName("HttpWellKnownResolver MockWebServer Tests")
@EnableMockWebServer
class HttpWellKnownResolverTest {

    @Getter
    private final WellKnownDispatcher moduleDispatcher = new WellKnownDispatcher();

    private HttpWellKnownResolver resolver;

    @BeforeEach
    void setUp() {
        moduleDispatcher.setCallCounter(0);
    }

    @Test
    @DisplayName("Should successfully resolve JWKS URI from well-known endpoint")
    void shouldSuccessfullyResolveJwksUri(URIBuilder uriBuilder) {
        // Setup well-known dispatcher to return valid response
        moduleDispatcher.returnDefault();

        String baseUrl = uriBuilder.buildAsString();
        String wellKnownUrl = uriBuilder.addPathSegment(".well-known")
                .addPathSegment("openid-configuration").buildAsString();
        String expectedJwksUri = baseUrl + "/oidc/jwks.json";

        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl(wellKnownUrl)
                .retryStrategy(RetryStrategy.none())
                .build();

        resolver = config.createResolver();

        // Test JWKS URI resolution
        Optional<String> jwksUri = resolver.getJwksUri();
        assertTrue(jwksUri.isPresent(), "JWKS URI should be discovered");
        assertEquals(expectedJwksUri, jwksUri.get(), "JWKS URI should match expected URL");

        // Verify health status is OK after successful resolution
        assertEquals(LoaderStatus.OK, resolver.getLoaderStatus());

        // Verify well-known endpoint was called once
        assertEquals(1, moduleDispatcher.getCallCounter(), "Well-known endpoint should be called once");
    }

    @Test
    @DisplayName("Should successfully resolve issuer from well-known endpoint")
    void shouldSuccessfullyResolveIssuer(URIBuilder uriBuilder) {
        // Setup well-known dispatcher to return valid response
        moduleDispatcher.returnDefault();

        String baseUrl = uriBuilder.buildAsString();
        String wellKnownUrl = uriBuilder.addPathSegment(".well-known")
                .addPathSegment("openid-configuration").buildAsString();

        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl(wellKnownUrl)
                .retryStrategy(RetryStrategy.none())
                .build();

        resolver = config.createResolver();

        // Test issuer resolution
        Optional<String> issuer = resolver.getIssuer();
        assertTrue(issuer.isPresent(), "Issuer should be discovered");
        assertEquals(baseUrl, issuer.get(), "Issuer should match base URL");

        // Verify health status is OK
        assertEquals(LoaderStatus.OK, resolver.getLoaderStatus());
    }

    @Test
    @DisplayName("Should resolve multiple endpoints correctly")
    void shouldResolveMultipleEndpoints(URIBuilder uriBuilder) {
        // Setup well-known dispatcher to return valid response
        moduleDispatcher.returnDefault();

        String baseUrl = uriBuilder.buildAsString();
        String wellKnownUrl = uriBuilder.addPathSegment(".well-known")
                .addPathSegment("openid-configuration").buildAsString();

        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl(wellKnownUrl)
                .retryStrategy(RetryStrategy.none())
                .build();

        resolver = config.createResolver();

        // Test multiple endpoint resolutions
        Optional<String> issuer = resolver.getIssuer();
        Optional<String> jwksUri = resolver.getJwksUri();
        Optional<String> authEndpoint = resolver.getAuthorizationEndpoint();
        Optional<String> tokenEndpoint = resolver.getTokenEndpoint();
        Optional<String> userinfoEndpoint = resolver.getUserinfoEndpoint();

        // All endpoints should be available
        assertTrue(issuer.isPresent(), "Issuer should be available");
        assertTrue(jwksUri.isPresent(), "JWKS URI should be available");
        assertTrue(authEndpoint.isPresent(), "Authorization endpoint should be available");
        assertTrue(tokenEndpoint.isPresent(), "Token endpoint should be available");
        assertTrue(userinfoEndpoint.isPresent(), "Userinfo endpoint should be available");

        // Verify endpoint URLs
        assertEquals(baseUrl, issuer.get());
        assertEquals(baseUrl + "/oidc/jwks.json", jwksUri.get());
        assertEquals(baseUrl + "/protocol/openid-connect/auth", authEndpoint.get());
        assertEquals(baseUrl + "/protocol/openid-connect/token", tokenEndpoint.get());
        assertEquals(baseUrl + "/protocol/openid-connect/userinfo", userinfoEndpoint.get());

        // Should only call the well-known endpoint once due to caching
        assertEquals(1, moduleDispatcher.getCallCounter(), "Well-known endpoint should be called once (cached)");
    }

    @Test
    @DisplayName("Should handle error responses gracefully")
    void shouldHandleErrorResponsesGracefully(URIBuilder uriBuilder) {
        // Setup dispatcher to return error
        moduleDispatcher.returnError();

        String wellKnownUrl = uriBuilder.addPathSegment(".well-known")
                .addPathSegment("openid-configuration").buildAsString();

        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl(wellKnownUrl)
                .retryStrategy(RetryStrategy.none())
                .build();

        resolver = config.createResolver();

        // All endpoint resolutions should return empty
        assertFalse(resolver.getJwksUri().isPresent(), "JWKS URI should not be available on error");
        assertFalse(resolver.getIssuer().isPresent(), "Issuer should not be available on error");
        assertFalse(resolver.getAuthorizationEndpoint().isPresent(), "Authorization endpoint should not be available on error");

        // Health status should reflect error
        assertEquals(LoaderStatus.ERROR, resolver.getLoaderStatus());

        // Well-known endpoint should be called
        assertTrue(moduleDispatcher.getCallCounter() >= 1, "Well-known endpoint should be called at least once");
    }

    @Test
    @DisplayName("Should handle missing JWKS URI gracefully")
    void shouldHandleMissingJwksUriGracefully(URIBuilder uriBuilder) {
        // Setup dispatcher with response missing JWKS URI
        moduleDispatcher.returnMissingJwksUri();

        String wellKnownUrl = uriBuilder.addPathSegment(".well-known")
                .addPathSegment("openid-configuration").buildAsString();

        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl(wellKnownUrl)
                .retryStrategy(RetryStrategy.none())
                .build();

        resolver = config.createResolver();

        // JWKS URI should not be available
        assertFalse(resolver.getJwksUri().isPresent(), "JWKS URI should not be available when missing from response");

        // No endpoints should be available since JWKS URI is a required field
        assertFalse(resolver.getIssuer().isPresent(), "Issuer should not be available when JWKS URI is missing (invalid config)");

        // Health status should be ERROR since JWKS URI is required for JWT validation
        assertEquals(LoaderStatus.ERROR, resolver.getLoaderStatus());
    }

    @Test
    @DisplayName("Should provide access to complete WellKnownResult")
    void shouldProvideAccessToCompleteWellKnownResult(URIBuilder uriBuilder) {
        // Setup well-known dispatcher to return valid response
        moduleDispatcher.returnDefault();

        String wellKnownUrl = uriBuilder.addPathSegment(".well-known")
                .addPathSegment("openid-configuration").buildAsString();

        WellKnownConfig config = WellKnownConfig.builder()
                .wellKnownUrl(wellKnownUrl)
                .retryStrategy(RetryStrategy.none())
                .build();

        resolver = config.createResolver();

        // Test complete result access
        Optional<WellKnownResult> result = resolver.getWellKnownResult();
        assertTrue(result.isPresent(), "WellKnownResult should be available");

        var wellKnown = result.get();
        assertNotNull(wellKnown.getIssuer(), "Issuer should be present in result");
        assertNotNull(wellKnown.getJwksUri(), "JWKS URI should be present in result");
        assertNotNull(wellKnown.getAuthorizationEndpoint(), "Authorization endpoint should be present in result");
    }
}