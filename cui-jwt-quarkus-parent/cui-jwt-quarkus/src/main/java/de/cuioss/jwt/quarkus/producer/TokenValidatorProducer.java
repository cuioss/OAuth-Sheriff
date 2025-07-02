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

import de.cuioss.jwt.quarkus.config.IssuerConfigResolver;
import de.cuioss.jwt.quarkus.config.ParserConfigResolver;
import de.cuioss.jwt.validation.IssuerConfig;
import de.cuioss.jwt.validation.ParserConfig;
import de.cuioss.jwt.validation.TokenValidator;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.tools.logging.CuiLogger;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.Config;

import java.util.List;

/**
 * CDI producer for JWT validation related instances.
 * <p>
 * This producer creates and manages all JWT validation components from
 * configuration properties. Components are initialized during startup
 * via {@link PostConstruct} and exposed through field-based producers.
 * </p>
 * <p>
 * Configuration resolution is delegated to dedicated resolver classes for
 * better separation of concerns, while validation is handled by the underlying
 * builders to avoid logic duplication.
 * </p>
 * <p>
 * Produced components:
 * <ul>
 *   <li>{@link TokenValidator} - Main JWT validation component</li>
 *   <li>{@link List}&lt;{@link IssuerConfig}&gt; - Resolved issuer configurations</li>
 *   <li>{@link SecurityEventCounter} - Security event monitoring</li>
 * </ul>
 *
 * @since 1.0
 */
@ApplicationScoped
public class TokenValidatorProducer {

    private static final CuiLogger LOGGER = new CuiLogger(TokenValidatorProducer.class);

    private final Config config;

    @Produces
    @ApplicationScoped
    TokenValidator tokenValidator;

    @Produces
    @ApplicationScoped
    List<IssuerConfig> issuerConfigs;

    @Produces
    @ApplicationScoped
    SecurityEventCounter securityEventCounter;

    public TokenValidatorProducer(Config config) {
        this.config = config;
    }

    /**
     * Initializes all JWT validation components from configuration.
     * <p>
     * This method is called during CDI container startup and creates all
     * the validation components that will be exposed through the field producers.
     * </p>
     */
    @PostConstruct
    void init() {
        LOGGER.info("Initializing JWT validation components from configuration");

        // Resolve issuer configurations using the dedicated resolver
        IssuerConfigResolver issuerConfigResolver = new IssuerConfigResolver(config);
        issuerConfigs = issuerConfigResolver.resolveIssuerConfigs();

        // Resolve parser config using the dedicated resolver
        ParserConfigResolver parserConfigResolver = new ParserConfigResolver(config);
        ParserConfig parserConfig = parserConfigResolver.resolveParserConfig();

        // Create TokenValidator directly - it handles internal initialization
        tokenValidator = new TokenValidator(parserConfig, issuerConfigs.toArray(new IssuerConfig[0]));

        // Extract SecurityEventCounter from the TokenValidator
        securityEventCounter = tokenValidator.getSecurityEventCounter();

        LOGGER.info("JWT validation components initialized successfully with %d issuers", issuerConfigs.size());
    }

}
