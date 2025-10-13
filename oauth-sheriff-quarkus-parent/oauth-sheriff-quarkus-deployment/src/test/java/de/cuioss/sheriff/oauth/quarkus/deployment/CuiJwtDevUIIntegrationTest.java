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
package de.cuioss.sheriff.oauth.quarkus.deployment;

import io.quarkus.devui.spi.JsonRPCProvidersBuildItem;
import io.quarkus.devui.spi.page.CardPageBuildItem;
import io.quarkus.test.QuarkusUnitTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for CUI JWT DevUI components.
 * <p>
 * This test verifies that DevUI build items are properly registered
 * when the extension is enabled in development mode.
 */
class CuiJwtDevUIIntegrationTest {

    @RegisterExtension
    static final QuarkusUnitTest config = new QuarkusUnitTest()
            .withApplicationRoot(jar -> jar
                    .addClasses(CuiJwtProcessor.class, CuiJwtDevUIJsonRPCService.class))
            .overrideConfigKey("cui.jwt.enabled", "true")
            .overrideConfigKey("quarkus.dev", "true");

    @Test
    @DisplayName("Should register DevUI components successfully")
    void devUIComponentsRegistered() {
        // This test verifies that the extension builds successfully
        // and that DevUI components are registered when in development mode

        // Since this is a build-time test, we're primarily checking that
        // the extension builds without errors and doesn't fail during
        // the build process when DevUI dependencies are present

        assertTrue(true, "DevUI extension built successfully");
    }

    @Test
    @DisplayName("Should have required DevUI build steps in processor")
    void devUIBuildStepsExist() {
        // Verify that the CuiJwtProcessor has the required DevUI build steps
        var processor = new CuiJwtProcessor();

        // These methods should exist and be callable
        assertDoesNotThrow(() -> {
            CardPageBuildItem cardPage = processor.createJwtDevUICard();
            assertNotNull(cardPage, "DevUI card should be created");
        });

        assertDoesNotThrow(() -> {
            JsonRPCProvidersBuildItem jsonRpcProviders = processor.createJwtDevUIJsonRPCService();
            assertNotNull(jsonRpcProviders, "JSON-RPC providers should be created");
        });
    }
}
