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
package de.cuioss.jwt.quarkus.producer;

import de.cuioss.jwt.quarkus.config.JwtTestProfile;
import de.cuioss.jwt.validation.IssuerConfig;
import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Quarkus integration tests for {@link TokenValidatorProducer} focusing on CDI injection and integration.
 * Configuration validation is covered in the config package tests.
 */
@QuarkusTest
@TestProfile(JwtTestProfile.class)
@EnableTestLogger
class QuarkusTokenValidatorProducerTest {

    @Inject
    TokenValidatorProducer producer;

    @Inject
    Config config;

    @Inject
    TokenValidator tokenValidator;

    @Inject
    List<IssuerConfig> issuerConfigs;

    @Inject
    SecurityEventCounter securityEventCounter;

    @Test
    @DisplayName("Should successfully inject and initialize all JWT components via CDI")
    void shouldSuccessfullyInjectAndInitializeJwtComponents() {
        assertNotNull(producer, "Producer should be injected via CDI");
        assertNotNull(config, "Config should be injected via CDI");

        assertNotNull(tokenValidator, "TokenValidator should be injected via CDI");
        assertNotNull(issuerConfigs, "IssuerConfigs should be injected via CDI");
        assertNotNull(securityEventCounter, "SecurityEventCounter should be injected via CDI");

        assertFalse(issuerConfigs.isEmpty(), "Should have at least one issuer config");
        assertNotNull(securityEventCounter.getCounters(), "SecurityEventCounter should have counters");

        assertEquals(issuerConfigs.size(), tokenValidator.getIssuerConfigs().size(),
                "Injected issuerConfigs should match TokenValidator's configs");

        assertEquals(securityEventCounter.getCounters().size(),
                tokenValidator.getSecurityEventCounter().getCounters().size(),
                "Injected SecurityEventCounter should have same counters as TokenValidator's");

        assertTrue(issuerConfigs.stream()
                        .anyMatch(issuer -> "https://example.com/auth".equals(issuer.getIssuerIdentifier())),
                "Should load default issuer from JwtTestProfile");

        // Note: Log assertions are skipped in Quarkus test environment 
        // as logs during CDI initialization may not be captured by test logger
    }

    @Test
    @DisplayName("Should reject invalid tokens in integrated environment")
    void shouldRejectInvalidTokensInIntegratedEnvironment() {
        String invalidToken = "invalid.token.format";

        assertThrows(TokenValidationException.class, () -> tokenValidator.createAccessToken(invalidToken),
                "Integrated TokenValidator should reject invalid tokens");
    }

}
