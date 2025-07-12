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
package de.cuioss.jwt.quarkus.config;

import de.cuioss.jwt.quarkus.test.TestConfig;
import de.cuioss.jwt.validation.IssuerConfig;
import de.cuioss.jwt.validation.security.SignatureAlgorithmPreferences;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages.INFO;
import static de.cuioss.test.juli.LogAsserts.assertLogMessagePresent;
import static de.cuioss.test.juli.LogAsserts.assertLogMessagePresentContaining;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests IssuerConfigResolver functionality.
 */
@DisplayName("IssuerConfigResolver")
@EnableTestLogger
class IssuerConfigResolverTest {

    private static final String TEST_ISSUER = "test";
    private static final String ANOTHER_ISSUER = "another";

    @Nested
    @DisplayName("Constructor Validation")
    class ConstructorValidation {

        @Test
        @DisplayName("should require non-null config")
        void shouldRequireNonNullConfig() {
            assertThrows(NullPointerException.class, () -> new IssuerConfigResolver(null),
                    "Should reject null config");
        }

        @Test
        @DisplayName("should accept valid config")
        void shouldAcceptValidConfig() {
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.ENABLED.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), "https://example.com/jwks"
            ));

            assertDoesNotThrow(() -> new IssuerConfigResolver(config),
                    "Should accept valid config");
        }

        @Test
        @DisplayName("should accept empty config but fail on resolution")
        void shouldAcceptEmptyConfig() {
            TestConfig emptyConfig = new TestConfig(Map.of());

            IssuerConfigResolver resolver = new IssuerConfigResolver(emptyConfig);

            // Note: The resolver will throw when trying to resolve configs, but construction should succeed
            assertThrows(IllegalStateException.class, resolver::resolveIssuerConfigs,
                    "Should throw when trying to resolve with empty config");
        }
    }

    @Nested
    @DisplayName("Issuer Discovery")
    class IssuerDiscovery {

        @Test
        @DisplayName("should throw when no issuers configured")
        void shouldThrowWhenNoIssuersConfigured() {
            TestConfig config = new TestConfig(Map.of());
            IssuerConfigResolver resolver = new IssuerConfigResolver(config);

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    resolver::resolveIssuerConfigs,
                    "Should throw when no issuers found");
            assertEquals("No issuer configurations found in properties", exception.getMessage());
        }

        @Test
        @DisplayName("should throw when no issuers enabled")
        void shouldThrowWhenNoIssuersEnabled() {
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.ENABLED.formatted(TEST_ISSUER), "false"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config);

            IllegalStateException exception = assertThrows(IllegalStateException.class,
                    resolver::resolveIssuerConfigs,
                    "Should throw when no enabled issuers found");
            assertEquals("No enabled issuer configurations found", exception.getMessage());
        }

        @Test
        @DisplayName("should discover issuer from properties")
        void shouldDiscoverIssuerFromProperties() {
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.ENABLED.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), "https://example.com/jwks"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config);

            List<IssuerConfig> result = resolver.resolveIssuerConfigs();

            assertEquals(1, result.size(), "Should find one issuer");
            assertTrue(result.getFirst().isEnabled(), "Should be enabled");
        }

        @Test
        @DisplayName("should discover multiple issuers")
        void shouldDiscoverMultipleIssuers() {
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.ENABLED.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), "https://example.com/jwks",
                    JwtPropertyKeys.ISSUERS.ENABLED.formatted(ANOTHER_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(ANOTHER_ISSUER), "https://other.com/jwks"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config);

            List<IssuerConfig> result = resolver.resolveIssuerConfigs();

            assertEquals(2, result.size(), "Should find two issuers");
            assertTrue(result.stream().allMatch(IssuerConfig::isEnabled), "All should be enabled");
        }
    }

    @Nested
    @DisplayName("Enabled Property Handling")
    class EnabledProperty {

        @Test
        @DisplayName("should skip disabled issuers")
        void shouldSkipDisabledIssuers() {
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.ENABLED.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), "https://example.com/jwks",
                    JwtPropertyKeys.ISSUERS.ENABLED.formatted(ANOTHER_ISSUER), "false",
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(ANOTHER_ISSUER), "https://other.com/jwks"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config);

            List<IssuerConfig> result = resolver.resolveIssuerConfigs();

            assertEquals(1, result.size(), "Should find only enabled issuer");
            assertLogMessagePresentContaining(TestLogLevel.DEBUG, "Skipping disabled issuer: " + ANOTHER_ISSUER);
        }

        @ParameterizedTest
        @DisplayName("should respect enabled property value")
        @CsvSource({
                "true, true",
                "false, false"
        })
        void shouldRespectEnabledProperty(String enabledValue, boolean expectedEnabled) {
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.ENABLED.formatted(TEST_ISSUER), enabledValue,
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), "https://example.com/jwks"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config);

            if (expectedEnabled) {
                List<IssuerConfig> result = resolver.resolveIssuerConfigs();
                assertEquals(1, result.size());
                assertEquals(expectedEnabled, result.getFirst().isEnabled());
            } else {
                assertThrows(IllegalStateException.class, resolver::resolveIssuerConfigs);
            }
        }

        @Test
        @DisplayName("should default to enabled when not specified")
        void shouldDefaultToEnabledWhenNotSpecified() {
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), "https://example.com/jwks"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config);

            List<IssuerConfig> result = resolver.resolveIssuerConfigs();

            assertEquals(1, result.size(), "Should find issuer");
            assertTrue(result.getFirst().isEnabled(), "Should default to enabled");
        }
    }

    @Nested
    @DisplayName("JWKS Source Configuration")
    class JwksSourceConfiguration {

        @Test
        @DisplayName("should configure HTTP JWKS URL with timeouts")
        void shouldConfigureHttpJwksUrl() {
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), "https://example.com/jwks",
                    JwtPropertyKeys.ISSUERS.CONNECT_TIMEOUT_SECONDS.formatted(TEST_ISSUER), "30",
                    JwtPropertyKeys.ISSUERS.READ_TIMEOUT_SECONDS.formatted(TEST_ISSUER), "60"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config);

            List<IssuerConfig> result = resolver.resolveIssuerConfigs();

            assertEquals(1, result.size());
            IssuerConfig issuer = result.getFirst();
            assertNotNull(issuer.getJwksLoader(), "Should have JWKS loader");
        }

        @Test
        @DisplayName("should configure well-known URL with refresh interval")
        void shouldConfigureWellKnownUrl() {
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.WELL_KNOWN_URL.formatted(TEST_ISSUER), "https://example.com/.well-known/openid_configuration",
                    JwtPropertyKeys.ISSUERS.REFRESH_INTERVAL_SECONDS.formatted(TEST_ISSUER), "3600"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config);

            List<IssuerConfig> result = resolver.resolveIssuerConfigs();

            assertEquals(1, result.size());
            IssuerConfig issuer = result.getFirst();
            assertNotNull(issuer.getJwksLoader(), "Should have JWKS loader");
        }

        @Test
        @DisplayName("should reject mutually exclusive JWKS sources")
        void shouldRejectMutuallyExclusiveJwksSources() {
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), "https://example.com/jwks",
                    JwtPropertyKeys.ISSUERS.WELL_KNOWN_URL.formatted(TEST_ISSUER), "https://example.com/.well-known/openid_configuration"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                    resolver::resolveIssuerConfigs,
                    "Should reject mutually exclusive JWKS sources");

            assertTrue(exception.getMessage().contains("mutually exclusive"),
                    "Exception should indicate mutual exclusivity violation");
        }

    }

    @Nested
    @DisplayName("Property Configuration")
    class PropertyConfiguration {

        @Test
        @DisplayName("should configure issuer identifier")
        void shouldConfigureIssuerIdentifier() {
            String issuerIdentifier = "https://example.com";
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.ISSUER_IDENTIFIER.formatted(TEST_ISSUER), issuerIdentifier,
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), "https://example.com/jwks"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config);

            List<IssuerConfig> result = resolver.resolveIssuerConfigs();

            assertEquals(1, result.size());
            assertNotNull(result.getFirst().getJwksLoader(), "Should have JWKS loader");
        }

        @Test
        @DisplayName("should configure expected audiences from comma-separated list")
        void shouldConfigureAudiences() {
            String audiences = "client1,client2,client3";
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.EXPECTED_AUDIENCE.formatted(TEST_ISSUER), audiences,
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), "https://example.com/jwks"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config);

            List<IssuerConfig> result = resolver.resolveIssuerConfigs();

            assertEquals(1, result.size());
            IssuerConfig issuer = result.getFirst();
            assertEquals(3, issuer.getExpectedAudience().size(), "Should have three audiences");
            assertTrue(issuer.getExpectedAudience().contains("client1"), "Should contain client1");
            assertTrue(issuer.getExpectedAudience().contains("client2"), "Should contain client2");
            assertTrue(issuer.getExpectedAudience().contains("client3"), "Should contain client3");
        }

        @Test
        @DisplayName("should configure expected client IDs from comma-separated list")
        void shouldConfigureClientIds() {
            String clientIds = "id1, id2 , id3";
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.EXPECTED_CLIENT_ID.formatted(TEST_ISSUER), clientIds,
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), "https://example.com/jwks"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config);

            List<IssuerConfig> result = resolver.resolveIssuerConfigs();

            assertEquals(1, result.size());
            IssuerConfig issuer = result.getFirst();
            assertEquals(3, issuer.getExpectedClientId().size(), "Should have three client IDs");
            assertTrue(issuer.getExpectedClientId().contains("id1"), "Should contain id1");
            assertTrue(issuer.getExpectedClientId().contains("id2"), "Should contain id2");
            assertTrue(issuer.getExpectedClientId().contains("id3"), "Should contain id3");
        }

        @Test
        @DisplayName("should configure algorithm preferences from comma-separated list")
        void shouldConfigureAlgorithmPreferences() {
            String algorithms = "RS256,ES256,PS256";
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.ALGORITHM_PREFERENCES.formatted(TEST_ISSUER), algorithms,
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), "https://example.com/jwks"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config);

            List<IssuerConfig> result = resolver.resolveIssuerConfigs();

            assertEquals(1, result.size());
            IssuerConfig issuer = result.getFirst();
            assertNotNull(issuer.getAlgorithmPreferences(), "Should have algorithm preferences");
            SignatureAlgorithmPreferences preferences = issuer.getAlgorithmPreferences();
            List<String> preferredAlgorithms = preferences.getPreferredAlgorithms();
            assertEquals(3, preferredAlgorithms.size(), "Should have three algorithms");
            assertEquals("RS256", preferredAlgorithms.getFirst(), "First should be RS256");
            assertEquals("ES256", preferredAlgorithms.get(1), "Second should be ES256");
            assertEquals("PS256", preferredAlgorithms.get(2), "Third should be PS256");
        }
    }

    @Nested
    @DisplayName("Logging Validation")
    class LoggingValidation {

        @Test
        @DisplayName("should log discovery and resolution process")
        void shouldLogDiscoveryAndResolution() {
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), "https://example.com/jwks"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config);

            resolver.resolveIssuerConfigs();

            assertLogMessagePresent(TestLogLevel.INFO, INFO.RESOLVING_ISSUER_CONFIGURATIONS.format());
            assertLogMessagePresentContaining(TestLogLevel.DEBUG, "Discovered issuer names:");
            assertLogMessagePresent(TestLogLevel.INFO, INFO.RESOLVED_ISSUER_CONFIGURATION.format(TEST_ISSUER));
            assertLogMessagePresent(TestLogLevel.INFO, INFO.RESOLVED_ENABLED_ISSUER_CONFIGURATIONS.format("1"));
        }

        @Test
        @DisplayName("should log JWKS source configuration")
        void shouldLogJwksSourceConfiguration() {
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), "https://example.com/jwks"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config);

            resolver.resolveIssuerConfigs();

            assertLogMessagePresentContaining(TestLogLevel.DEBUG, "Configured HTTP JWKS URL for " + TEST_ISSUER);
        }

        @Test
        @DisplayName("should log when skipping disabled issuers")
        void shouldLogDisabledIssuerSkipping() {
            TestConfig config = new TestConfig(Map.of(
                    JwtPropertyKeys.ISSUERS.ENABLED.formatted(TEST_ISSUER), "true",
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(TEST_ISSUER), "https://example.com/jwks",
                    JwtPropertyKeys.ISSUERS.ENABLED.formatted(ANOTHER_ISSUER), "false",
                    JwtPropertyKeys.ISSUERS.JWKS_URL.formatted(ANOTHER_ISSUER), "https://other.com/jwks"
            ));
            IssuerConfigResolver resolver = new IssuerConfigResolver(config);

            resolver.resolveIssuerConfigs();

            assertLogMessagePresentContaining(TestLogLevel.DEBUG, "Skipping disabled issuer: " + ANOTHER_ISSUER);
            assertLogMessagePresent(TestLogLevel.INFO, INFO.RESOLVED_ISSUER_CONFIGURATION.format(TEST_ISSUER));
        }
    }
}
