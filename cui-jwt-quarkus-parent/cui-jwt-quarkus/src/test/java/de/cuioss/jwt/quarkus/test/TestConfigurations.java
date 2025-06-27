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
package de.cuioss.jwt.quarkus.test;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class providing predefined test configurations for common JWT scenarios.
 * This class offers convenient factory methods for creating typical JWT test configurations
 * without the boilerplate of setting up individual properties.
 */
public final class TestConfigurations {

    private TestConfigurations() {
        // Utility class
    }

    /**
     * Creates a minimal valid JWT configuration with a single issuer.
     * Suitable for testing basic positive scenarios.
     *
     * @return TestConfig with minimal valid JWT configuration
     */
    public static TestConfig minimalValid() {
        return new TestConfig(Map.of(
                "cui.jwt.issuers.test.enabled", "true",
                "cui.jwt.issuers.test.identifier", "https://test.example.com",
                "cui.jwt.issuers.test.public-key-location", "classpath:jwt-test.key"
        ));
    }

    /**
     * Creates a configuration with invalid parser settings.
     * Useful for testing configuration validation error scenarios.
     *
     * @return TestConfig with invalid parser configuration
     */
    public static TestConfig invalidParser() {
        return new TestConfig(Map.of(
                "cui.jwt.parser.max-token-size-bytes", "-1",
                "cui.jwt.issuers.test.enabled", "true",
                "cui.jwt.issuers.test.identifier", "https://test.example.com"
        ));
    }

    /**
     * Creates a configuration with no enabled issuers.
     * Useful for testing empty configuration scenarios.
     *
     * @return TestConfig with no enabled issuers
     */
    public static TestConfig noEnabledIssuers() {
        return new TestConfig(Map.of(
                "cui.jwt.issuers.test.enabled", "false",
                "cui.jwt.issuers.test.identifier", "https://test.example.com"
        ));
    }

    /**
     * Creates a configuration with conflicting issuer settings.
     * Tests validation of mutually exclusive configuration options.
     *
     * @return TestConfig with conflicting issuer configuration
     */
    public static TestConfig conflictingIssuerConfig() {
        return new TestConfig(Map.of(
                "cui.jwt.issuers.test.enabled", "true",
                "cui.jwt.issuers.test.identifier", "https://test.example.com",
                "cui.jwt.issuers.test.well-known-url", "https://test.example.com/.well-known/openid_configuration"
        ));
    }

    /**
     * Creates a configuration for testing JWKS endpoint scenarios.
     * Includes proper JWKS URL configuration.
     *
     * @return TestConfig with JWKS URL configuration
     */
    public static TestConfig withJwksUrl() {
        return new TestConfig(Map.of(
                "cui.jwt.issuers.test.enabled", "true",
                "cui.jwt.issuers.test.identifier", "https://test.example.com",
                "cui.jwt.issuers.test.jwks.url", "https://test.example.com/jwks",
                "cui.jwt.issuers.test.jwks.refresh-interval-seconds", "300",
                "cui.jwt.issuers.test.jwks.connection-timeout-seconds", "5"
        ));
    }

    /**
     * Creates a configuration for testing well-known endpoint discovery.
     * Includes proper well-known URL configuration.
     *
     * @return TestConfig with well-known URL configuration
     */
    public static TestConfig withWellKnownUrl() {
        return new TestConfig(Map.of(
                "cui.jwt.issuers.test.enabled", "true",
                "cui.jwt.issuers.test.well-known-url", "https://test.example.com/.well-known/openid_configuration",
                "cui.jwt.issuers.test.jwks.connection-timeout-seconds", "5",
                "cui.jwt.issuers.test.jwks.read-timeout-seconds", "5"
        ));
    }

    /**
     * Creates a configuration with multiple issuers for testing complex scenarios.
     * Includes both enabled and disabled issuers.
     *
     * @return TestConfig with multiple issuers
     */
    public static TestConfig multipleIssuers() {
        return new TestConfig(Map.of(
                "cui.jwt.issuers.primary.enabled", "true",
                "cui.jwt.issuers.primary.identifier", "https://primary.example.com",
                "cui.jwt.issuers.primary.public-key-location", "classpath:jwt-test.key",
                "cui.jwt.issuers.secondary.enabled", "true",
                "cui.jwt.issuers.secondary.identifier", "https://secondary.example.com",
                "cui.jwt.issuers.secondary.public-key-location", "classpath:jwt-test.key",
                "cui.jwt.issuers.disabled.enabled", "false",
                "cui.jwt.issuers.disabled.identifier", "https://disabled.example.com"
        ));
    }

    /**
     * Creates a configuration with custom parser settings.
     * Useful for testing parser configuration scenarios.
     *
     * @return TestConfig with custom parser configuration
     */
    public static TestConfig customParser() {
        return new TestConfig(Map.of(
                "cui.jwt.parser.max-token-size-bytes", "16384",
                "cui.jwt.parser.leeway-seconds", "60",
                "cui.jwt.parser.validate-expiration", "true",
                "cui.jwt.parser.validate-not-before", "false",
                "cui.jwt.parser.allowed-algorithms", "RS256,ES256",
                "cui.jwt.issuers.test.enabled", "true",
                "cui.jwt.issuers.test.identifier", "https://test.example.com",
                "cui.jwt.issuers.test.public-key-location", "classpath:jwt-test.key"
        ));
    }

    /**
     * Creates an empty configuration for testing missing configuration scenarios.
     *
     * @return Empty TestConfig
     */
    public static TestConfig empty() {
        return new TestConfig(Map.of());
    }

    /**
     * Builder for creating custom test configurations with fluent API.
     *
     * @return new TestConfigBuilder instance
     */
    public static TestConfigBuilder builder() {
        return new TestConfigBuilder();
    }

    /**
     * Builder class for creating custom test configurations with a fluent API.
     */
    public static class TestConfigBuilder {
        private final Map<String, String> properties = new HashMap<>();

        /**
         * Adds an issuer configuration to the builder.
         *
         * @param issuerName the name of the issuer
         * @return IssuerConfigBuilder for configuring the issuer
         */
        public IssuerConfigBuilder withIssuer(String issuerName) {
            return new IssuerConfigBuilder(this, issuerName);
        }

        /**
         * Adds parser configuration to the builder.
         *
         * @return ParserConfigBuilder for configuring the parser
         */
        public ParserConfigBuilder withParser() {
            return new ParserConfigBuilder(this);
        }

        /**
         * Adds a custom property to the configuration.
         *
         * @param key   the property key
         * @param value the property value
         * @return this builder
         */
        public TestConfigBuilder withProperty(String key, String value) {
            properties.put(key, value);
            return this;
        }

        /**
         * Builds the TestConfig with all configured properties.
         *
         * @return new TestConfig instance
         */
        public TestConfig build() {
            return new TestConfig(new HashMap<>(properties));
        }

        void addProperty(String key, String value) {
            properties.put(key, value);
        }
    }

    /**
     * Builder for configuring issuer-specific properties.
     */
    public static class IssuerConfigBuilder {
        private final TestConfigBuilder parent;
        private final String issuerName;

        IssuerConfigBuilder(TestConfigBuilder parent, String issuerName) {
            this.parent = parent;
            this.issuerName = issuerName;
        }

        /**
         * Sets whether the issuer is enabled.
         *
         * @param enabled true if enabled
         * @return this builder
         */
        public IssuerConfigBuilder enabled(boolean enabled) {
            parent.addProperty("cui.jwt.issuers." + issuerName + ".enabled", String.valueOf(enabled));
            return this;
        }

        /**
         * Sets the issuer identifier.
         *
         * @param identifier the issuer identifier URL
         * @return this builder
         */
        public IssuerConfigBuilder identifier(String identifier) {
            parent.addProperty("cui.jwt.issuers." + issuerName + ".identifier", identifier);
            return this;
        }

        /**
         * Sets the public key location.
         *
         * @param location the public key file location
         * @return this builder
         */
        public IssuerConfigBuilder publicKeyLocation(String location) {
            parent.addProperty("cui.jwt.issuers." + issuerName + ".public-key-location", location);
            return this;
        }

        /**
         * Sets the JWKS URL.
         *
         * @param url the JWKS endpoint URL
         * @return this builder
         */
        public IssuerConfigBuilder jwksUrl(String url) {
            parent.addProperty("cui.jwt.issuers." + issuerName + ".jwks.url", url);
            return this;
        }

        /**
         * Sets the well-known discovery URL.
         *
         * @param url the well-known endpoint URL
         * @return this builder
         */
        public IssuerConfigBuilder wellKnownUrl(String url) {
            parent.addProperty("cui.jwt.issuers." + issuerName + ".well-known-url", url);
            return this;
        }

        /**
         * Configures JWKS refresh interval.
         *
         * @param seconds refresh interval in seconds
         * @return this builder
         */
        public IssuerConfigBuilder jwksRefreshInterval(int seconds) {
            parent.addProperty("cui.jwt.issuers." + issuerName + ".jwks.refresh-interval-seconds", String.valueOf(seconds));
            return this;
        }

        /**
         * Returns to the parent builder.
         *
         * @return parent TestConfigBuilder
         */
        public TestConfigBuilder and() {
            return parent;
        }

        /**
         * Builds the configuration directly from this issuer builder.
         *
         * @return new TestConfig instance
         */
        public TestConfig build() {
            return parent.build();
        }
    }

    /**
     * Builder for configuring parser-specific properties.
     */
    public static class ParserConfigBuilder {
        private final TestConfigBuilder parent;

        ParserConfigBuilder(TestConfigBuilder parent) {
            this.parent = parent;
        }

        /**
         * Sets the maximum token size in bytes.
         *
         * @param bytes maximum token size
         * @return this builder
         */
        public ParserConfigBuilder maxTokenSizeBytes(int bytes) {
            parent.addProperty("cui.jwt.parser.max-token-size-bytes", String.valueOf(bytes));
            return this;
        }

        /**
         * Sets the leeway time in seconds.
         *
         * @param seconds leeway in seconds
         * @return this builder
         */
        public ParserConfigBuilder leewaySeconds(int seconds) {
            parent.addProperty("cui.jwt.parser.leeway-seconds", String.valueOf(seconds));
            return this;
        }

        /**
         * Sets whether to validate token expiration.
         *
         * @param validate true to validate expiration
         * @return this builder
         */
        public ParserConfigBuilder validateExpiration(boolean validate) {
            parent.addProperty("cui.jwt.parser.validate-expiration", String.valueOf(validate));
            return this;
        }

        /**
         * Sets whether to validate not-before claim.
         *
         * @param validate true to validate not-before
         * @return this builder
         */
        public ParserConfigBuilder validateNotBefore(boolean validate) {
            parent.addProperty("cui.jwt.parser.validate-not-before", String.valueOf(validate));
            return this;
        }

        /**
         * Sets the allowed signature algorithms.
         *
         * @param algorithms comma-separated list of allowed algorithms
         * @return this builder
         */
        public ParserConfigBuilder allowedAlgorithms(String algorithms) {
            parent.addProperty("cui.jwt.parser.allowed-algorithms", algorithms);
            return this;
        }

        /**
         * Returns to the parent builder.
         *
         * @return parent TestConfigBuilder
         */
        public TestConfigBuilder and() {
            return parent;
        }

        /**
         * Builds the configuration directly from this parser builder.
         *
         * @return new TestConfig instance
         */
        public TestConfig build() {
            return parent.build();
        }
    }
}