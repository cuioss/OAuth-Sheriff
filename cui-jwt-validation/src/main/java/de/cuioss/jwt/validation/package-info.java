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
/**
 * Provides a comprehensive framework for handling OAuth2 and OpenID Connect tokens
 * in a Portal environment. This package focuses on JWT token parsing, validation,
 * and management with support for multiple token issuers.
 * <p>
 * Key components:
 * <ul>
 *   <li>{@link de.cuioss.jwt.validation.TokenValidator} - Main entry point for creating and validating tokens</li>
 *   <li>{@link de.cuioss.jwt.validation.IssuerConfig} - Configuration for token issuers</li>
 *   <li>{@link de.cuioss.jwt.validation.ParserConfig} - Configuration for token parsing</li>
 *   <li>{@link de.cuioss.jwt.validation.JWTValidationLogMessages} - Structured logging messages</li>
 * </ul>
 * <p>
 * The package supports:
 * <ul>
 *   <li>Multi-issuer token validation</li>
 *   <li>JWKS (JSON Web Key Set) integration</li>
 *   <li>Role and scope-based authorization</li>
 *   <li>Token expiration management</li>
 *   <li>Custom claim mapping</li>
 * </ul>
 * <p>
 * For detailed information about the library, see the following documentation:
 * <ul>
 *   <li><a href="https://github.com/cuioss/cui-jwt/tree/main/doc/Requirements.adoc">Requirements</a></li>
 *   <li><a href="https://github.com/cuioss/cui-jwt/tree/main/doc/Specification.adoc">Specification</a></li>
 *   <li><a href="https://github.com/cuioss/cui-jwt/tree/main/cui-jwt-validation/README.adoc">Usage Guide</a></li>
 * </ul>
 * <p>
 * Note: The implementation is primarily tested with Keycloak as the identity provider.
 * Some features may be specific to Keycloak's token implementation.
 * 
 * @since 1.0
 */
package de.cuioss.jwt.validation;
