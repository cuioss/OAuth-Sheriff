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
package de.cuioss.jwt.validation.well_known;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.cuioss.tools.net.http.HttpHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for {@link WellKnownEndpointMapper}.
 *
 * @author Oliver Wolff
 */
@EnableTestLogger
@EnableGeneratorController
@DisplayName("WellKnownEndpointMapper")
class WellKnownEndpointMapperTest {

    private static final String ENDPOINT_KEY = "jwks_uri";
    private static final String VALID_URL = "https://example.com/.well-known/jwks.json";

    private WellKnownEndpointMapper mapper;
    private URL wellKnownUrl;
    private Map<String, HttpHandler> endpointMap;

    @BeforeEach
    void setup() throws MalformedURLException {
        wellKnownUrl = URI.create("https://example.com/.well-known/openid-configuration").toURL();
        endpointMap = new HashMap<>();
        HttpHandler baseHandler = HttpHandler.builder().url(wellKnownUrl.toString()).build();
        mapper = new WellKnownEndpointMapper(baseHandler);
    }

    @Test
    @DisplayName("Should add HttpHandler to map for valid URL")
    void shouldAddHttpHandlerToMapForValidUrl() {
        mapper.addHttpHandlerToMap(endpointMap, ENDPOINT_KEY, VALID_URL, wellKnownUrl, true);

        assertTrue(endpointMap.containsKey(ENDPOINT_KEY));
        assertNotNull(endpointMap.get(ENDPOINT_KEY));
        assertEquals(VALID_URL, endpointMap.get(ENDPOINT_KEY).getUrl().toString());
    }

    @ParameterizedTest(name = "URL: {0}, Required: {1}, Should throw: {2}, Exception message contains: {3}")
    @CsvSource({
            "'<null>', false, false, ''",
            "'<null>', true, true, 'Required URL field'",
            "'http://example.com/invalid path with spaces', true, true, 'Malformed URL for field'",
            "'http://example.com/invalid path with spaces', false, true, 'Malformed URL for field'",
            "'', true, true, 'Malformed URL for field'",
            "'', false, true, 'Malformed URL for field'"
    })
    @DisplayName("Should handle various URL scenarios")
    void shouldHandleVariousUrlScenarios(String urlInput, boolean required, boolean shouldThrow, String expectedMessagePart) {
        // Convert "<null>" string to actual null
        String url = "<null>".equals(urlInput) ? null : urlInput;

        if (shouldThrow) {
            WellKnownDiscoveryException exception = assertThrows(WellKnownDiscoveryException.class,
                    () -> mapper.addHttpHandlerToMap(endpointMap, ENDPOINT_KEY, url, wellKnownUrl, required));

            assertTrue(exception.getMessage().contains(expectedMessagePart));
            assertTrue(exception.getMessage().contains(ENDPOINT_KEY));
            if (url != null) {
                assertTrue(exception.getMessage().contains(url));
            }
            assertTrue(exception.getMessage().contains(wellKnownUrl.toString()));

            if (expectedMessagePart.contains("Malformed URL")) {
                assertNotNull(exception.getCause());
                assertInstanceOf(IllegalArgumentException.class, exception.getCause());
            }
        } else {
            assertDoesNotThrow(() -> mapper.addHttpHandlerToMap(endpointMap, ENDPOINT_KEY, url, wellKnownUrl, required));
        }

        // In all cases, the endpoint should not be added to the map
        assertFalse(endpointMap.containsKey(ENDPOINT_KEY));
    }

    @Test
    @DisplayName("Should handle multiple endpoint URLs")
    void shouldHandleMultipleEndpointUrls() {
        String secondKey = "token_endpoint";
        String secondUrl = "https://example.com/token";

        mapper.addHttpHandlerToMap(endpointMap, ENDPOINT_KEY, VALID_URL, wellKnownUrl, true);
        mapper.addHttpHandlerToMap(endpointMap, secondKey, secondUrl, wellKnownUrl, true);

        assertEquals(2, endpointMap.size());
        assertTrue(endpointMap.containsKey(ENDPOINT_KEY));
        assertTrue(endpointMap.containsKey(secondKey));
        assertEquals(VALID_URL, endpointMap.get(ENDPOINT_KEY).getUrl().toString());
        assertEquals(secondUrl, endpointMap.get(secondKey).getUrl().toString());
    }

    @Test
    @DisplayName("Should handle null handler in accessibility check")
    void shouldHandleNullHandlerInAccessibilityCheck() {
        assertDoesNotThrow(() -> mapper.performAccessibilityCheck("jwks_uri", null));
    }

    @Test
    @DisplayName("Should override existing endpoint in map")
    void shouldOverrideExistingEndpointInMap() {
        String newUrl = "https://example.com/new-jwks.json";

        // Add first endpoint
        mapper.addHttpHandlerToMap(endpointMap, ENDPOINT_KEY, VALID_URL, wellKnownUrl, true);
        assertEquals(1, endpointMap.size());

        // Override with new URL
        mapper.addHttpHandlerToMap(endpointMap, ENDPOINT_KEY, newUrl, wellKnownUrl, true);
        assertEquals(1, endpointMap.size());
        assertTrue(endpointMap.containsKey(ENDPOINT_KEY));
        assertEquals(newUrl, endpointMap.get(ENDPOINT_KEY).getUrl().toString());
    }

}
