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
import de.cuioss.jwt.validation.ParserConfig;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static de.cuioss.jwt.quarkus.CuiJwtQuarkusLogMessages.INFO;
import static de.cuioss.test.juli.LogAsserts.assertLogMessagePresent;
import static de.cuioss.test.juli.LogAsserts.assertLogMessagePresentContaining;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests ParserConfigResolver functionality.
 */
@EnableTestLogger
class ParserConfigResolverTest {

    @Test
    @DisplayName("Should resolve custom parser config from properties")
    void shouldResolveCustomParserConfig() {
        TestConfig config = new TestConfig(Map.of(
                JwtPropertyKeys.PARSER.MAX_TOKEN_SIZE, "16384",
                JwtPropertyKeys.PARSER.MAX_PAYLOAD_SIZE, "8192",
                JwtPropertyKeys.PARSER.MAX_STRING_SIZE, "4096",
                JwtPropertyKeys.PARSER.MAX_ARRAY_SIZE, "256",
                JwtPropertyKeys.PARSER.MAX_DEPTH, "20"
        ));
        ParserConfigResolver resolver = new ParserConfigResolver(config);

        ParserConfig result = resolver.resolveParserConfig();

        assertEquals(16384, result.getMaxTokenSize(), "Should use custom token size");
        assertEquals(8192, result.getMaxPayloadSize(), "Should use custom payload size");
        assertEquals(4096, result.getMaxStringSize(), "Should use custom string size");
        assertEquals(256, result.getMaxArraySize(), "Should use custom array size");
        assertEquals(20, result.getMaxDepth(), "Should use custom depth");
    }


    @Test
    @DisplayName("Should log configuration details during resolution")
    void shouldLogConfigurationDetails() {
        int tokenSize = 8192;
        TestConfig config = new TestConfig(Map.of(
                JwtPropertyKeys.PARSER.MAX_TOKEN_SIZE, String.valueOf(tokenSize)
        ));
        ParserConfigResolver resolver = new ParserConfigResolver(config);

        resolver.resolveParserConfig();

        assertLogMessagePresentContaining(TestLogLevel.DEBUG, "Set maxTokenSize from configuration");
        assertLogMessagePresent(TestLogLevel.INFO, INFO.RESOLVED_PARSER_CONFIG.format(
                String.valueOf(tokenSize), "8192", "4096", "64", "10"));
    }

    @Test
    @DisplayName("Should handle invalid property values gracefully")
    void shouldHandleInvalidValues() {
        TestConfig config = new TestConfig(Map.of(
                JwtPropertyKeys.PARSER.MAX_TOKEN_SIZE, "invalid-number"
        ));
        ParserConfigResolver resolver = new ParserConfigResolver(config);

        ParserConfig result = assertDoesNotThrow(resolver::resolveParserConfig,
                "Should handle invalid values gracefully");

        assertNotNull(result, "Should create parser config with defaults");
    }

    @Test
    @DisplayName("Should require non-null config parameter")
    void shouldRequireNonNullConfig() {
        assertThrows(NullPointerException.class, () -> new ParserConfigResolver(null),
                "Should reject null config");
    }

    @Test
    @DisplayName("Should resolve default parser config when no properties set")
    void shouldResolveDefaultParserConfig() {
        TestConfig config = new TestConfig(Map.of());
        ParserConfigResolver resolver = new ParserConfigResolver(config);

        ParserConfig result = resolver.resolveParserConfig();

        assertNotNull(result, "Should create parser config");
        assertEquals(ParserConfig.DEFAULT_MAX_TOKEN_SIZE, result.getMaxTokenSize(), "Should use default token size");
        assertEquals(ParserConfig.DEFAULT_MAX_PAYLOAD_SIZE, result.getMaxPayloadSize(), "Should use default payload size");
        assertEquals(ParserConfig.DEFAULT_MAX_STRING_SIZE, result.getMaxStringSize(), "Should use default string size");
        assertEquals(ParserConfig.DEFAULT_MAX_ARRAY_SIZE, result.getMaxArraySize(), "Should use default array size");
        assertEquals(ParserConfig.DEFAULT_MAX_DEPTH, result.getMaxDepth(), "Should use default depth");
        assertLogMessagePresentContaining(TestLogLevel.DEBUG, "Resolving ParserConfig from properties");
        assertLogMessagePresent(TestLogLevel.INFO, INFO.RESOLVED_PARSER_CONFIG.format(
                String.valueOf(ParserConfig.DEFAULT_MAX_TOKEN_SIZE),
                String.valueOf(ParserConfig.DEFAULT_MAX_PAYLOAD_SIZE),
                String.valueOf(ParserConfig.DEFAULT_MAX_STRING_SIZE),
                String.valueOf(ParserConfig.DEFAULT_MAX_ARRAY_SIZE),
                String.valueOf(ParserConfig.DEFAULT_MAX_DEPTH)));
    }
}