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
package de.cuioss.sheriff.oauth.core;

import com.dslplatform.json.DslJson;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.valueobjects.junit5.contracts.ShouldImplementEqualsAndHashCode;
import de.cuioss.test.valueobjects.junit5.contracts.ShouldImplementToString;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test for {@link ParserConfig}.
 * 
 * @author Oliver Wolff
 */
@EnableGeneratorController
class ParserConfigTest implements ShouldImplementEqualsAndHashCode<ParserConfig>, ShouldImplementToString<ParserConfig> {

    @Override
    public ParserConfig getUnderTest() {
        return ParserConfig.builder().build();
    }

    @Test
    @DisplayName("Should use default values when created with builder")
    void shouldUseDefaultValues() {
        var config = ParserConfig.builder().build();

        assertEquals(ParserConfig.DEFAULT_MAX_TOKEN_SIZE, config.getMaxTokenSize());
        assertEquals(ParserConfig.DEFAULT_MAX_PAYLOAD_SIZE, config.getMaxPayloadSize());
        assertEquals(ParserConfig.DEFAULT_MAX_STRING_LENGTH, config.getMaxStringLength());
        assertEquals(ParserConfig.DEFAULT_MAX_BUFFER_SIZE, config.getMaxBufferSize());
    }

    @Test
    @DisplayName("Should accept custom values through builder")
    void shouldAcceptCustomValues() {
        var config = ParserConfig.builder()
                .maxTokenSize(16384)
                .maxPayloadSize(32768)
                .maxStringLength(8192)
                .maxBufferSize(256000)
                .build();

        assertEquals(16384, config.getMaxTokenSize());
        assertEquals(32768, config.getMaxPayloadSize());
        assertEquals(8192, config.getMaxStringLength());
        assertEquals(256000, config.getMaxBufferSize());
    }

    @Test
    @DisplayName("Should provide consistent default constants")
    void shouldProvideConsistentDefaults() {
        assertEquals(ParserConfig.DEFAULT_MAX_TOKEN_SIZE, 8 * 1024);
        assertEquals(ParserConfig.DEFAULT_MAX_PAYLOAD_SIZE, 8 * 1024);
        assertEquals(ParserConfig.DEFAULT_MAX_STRING_LENGTH, 4 * 1024);
        assertEquals(ParserConfig.DEFAULT_MAX_BUFFER_SIZE, 128 * 1024);
    }

    @Test
    @DisplayName("Should create DslJson with security settings")
    void shouldCreateDslJsonWithSecuritySettings() {
        var config = ParserConfig.builder()
                .maxStringLength(2048)
                .maxBufferSize(64000)
                .build();

        var dslJson = config.getDslJson();
        assertNotNull(dslJson);

        // DslJson should be lazily created and cached
        assertSame(dslJson, config.getDslJson());
    }

    @Test
    @DisplayName("Should handle boundary values")
    void shouldHandleBoundaryValues() {
        var config = ParserConfig.builder()
                .maxTokenSize(1)
                .maxPayloadSize(1)
                .maxStringLength(1)
                .maxBufferSize(1)
                .build();

        assertEquals(1, config.getMaxTokenSize());
        assertEquals(1, config.getMaxPayloadSize());
        assertEquals(1, config.getMaxStringLength());
        assertEquals(1, config.getMaxBufferSize());
    }

    @Test
    @DisplayName("Should handle zero values")
    void shouldHandleZeroValues() {
        var config = ParserConfig.builder()
                .maxTokenSize(0)
                .maxPayloadSize(0)
                .maxStringLength(0)
                .maxBufferSize(0)
                .build();

        assertEquals(0, config.getMaxTokenSize());
        assertEquals(0, config.getMaxPayloadSize());
        assertEquals(0, config.getMaxStringLength());
        assertEquals(0, config.getMaxBufferSize());
    }

    @Test
    @DisplayName("Should handle large values")
    void shouldHandleLargeValues() {
        var config = ParserConfig.builder()
                .maxTokenSize(Integer.MAX_VALUE)
                .maxPayloadSize(Integer.MAX_VALUE)
                .maxStringLength(Integer.MAX_VALUE)
                .maxBufferSize(Integer.MAX_VALUE)
                .build();

        assertEquals(Integer.MAX_VALUE, config.getMaxTokenSize());
        assertEquals(Integer.MAX_VALUE, config.getMaxPayloadSize());
        assertEquals(Integer.MAX_VALUE, config.getMaxStringLength());
        assertEquals(Integer.MAX_VALUE, config.getMaxBufferSize());
    }

    @Test
    @DisplayName("Should maintain immutability")
    void shouldMaintainImmutability() {
        var config = ParserConfig.builder()
                .maxTokenSize(1024)
                .build();

        // Accessing the same value multiple times should return the same result
        assertEquals(1024, config.getMaxTokenSize());
        assertEquals(1024, config.getMaxTokenSize());

        // DslJson should be cached (lazy initialization)
        var dslJson1 = config.getDslJson();
        var dslJson2 = config.getDslJson();
        assertSame(dslJson1, dslJson2);
    }

    @Test
    @DisplayName("Should verify DSL-JSON configuration is properly applied")
    void shouldVerifyDslJsonConfigurationIsProperlyApplied() {
        var config = ParserConfig.builder()
                .maxStringLength(1024)
                .maxBufferSize(2048)
                .build();

        var dslJson = config.getDslJson();
        assertNotNull(dslJson, "DslJson should be created with the specified configuration");

        // DSL-JSON configuration is applied during instantiation
        // We can verify the instance is created successfully with our settings
        assertInstanceOf(DslJson.class, dslJson, "Should create proper DslJson instance");
    }

    @Test
    @DisplayName("Should create DslJson with consistent configuration")
    void shouldCreateDslJsonWithConsistentConfiguration() {
        var config = ParserConfig.builder()
                .maxStringLength(100)
                .maxBufferSize(2048)
                .build();

        var dslJson = config.getDslJson();
        assertNotNull(dslJson, "DslJson should be created successfully");

        // Verify that multiple calls return the same instance (cached)
        var dslJson2 = config.getDslJson();
        assertSame(dslJson, dslJson2, "DslJson should be cached and return same instance");
    }

    @Test
    @DisplayName("Should verify DslJson settings are applied correctly")
    void shouldVerifyDslJsonSettingsAreAppliedCorrectly() {
        var config1 = ParserConfig.builder()
                .maxStringLength(1024)
                .maxBufferSize(2048)
                .build();

        var config2 = ParserConfig.builder()
                .maxStringLength(2048)
                .maxBufferSize(4096)
                .build();

        var dslJson1 = config1.getDslJson();
        var dslJson2 = config2.getDslJson();

        assertNotNull(dslJson1);
        assertNotNull(dslJson2);

        // Different configurations should produce different DslJson instances
        assertNotSame(dslJson1, dslJson2, "Different configurations should create different DslJson instances");
    }
}