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
package de.cuioss.sheriff.oauth.core.pipeline;

import de.cuioss.sheriff.oauth.core.ParserConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Basic test for {@link ParserConfig} in pipeline context.
 */
@DisplayName("Basic ParserConfig Tests")
class ParserConfigTest {

    @Test
    @DisplayName("Should create default parser config")
    void shouldCreateDefaultParserConfig() {
        ParserConfig config = ParserConfig.builder().build();

        assertNotNull(config);
        assertEquals(ParserConfig.DEFAULT_MAX_TOKEN_SIZE, config.getMaxTokenSize());
        assertEquals(ParserConfig.DEFAULT_MAX_PAYLOAD_SIZE, config.getMaxPayloadSize());
        assertEquals(ParserConfig.DEFAULT_MAX_STRING_LENGTH, config.getMaxStringLength());
        assertEquals(ParserConfig.DEFAULT_MAX_BUFFER_SIZE, config.getMaxBufferSize());
    }

    @Test
    @DisplayName("Should create custom parser config")
    void shouldCreateCustomParserConfig() {
        ParserConfig config = ParserConfig.builder()
                .maxTokenSize(16384)
                .maxPayloadSize(8192)
                .maxStringLength(4096)
                .maxBufferSize(65536)
                .build();

        assertNotNull(config);
        assertEquals(16384, config.getMaxTokenSize());
        assertEquals(8192, config.getMaxPayloadSize());
        assertEquals(4096, config.getMaxStringLength());
        assertEquals(65536, config.getMaxBufferSize());

        // Verify DslJson is created properly
        assertNotNull(config.getDslJson());
    }
}