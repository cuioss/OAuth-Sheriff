/*
 * Copyright 2025 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

/**
 * Provides classes for handling JWT claims according to RFC 7519, OpenID Connect, and OAuth 2.0 specifications.
 * <p>
 * This package contains the core domain classes for representing and handling JWT claims.
 * It defines a consistent model for working with claims of different types and formats,
 * ensuring type safety and validation throughout the JWT processing pipeline.
 * <p>
 * Key components:
 * <ul>
 *   <li>{@link de.cuioss.jwt.validation.domain.claim.ClaimName} - Enumeration of standard JWT claim names with their expected value types</li>
 *   <li>{@link de.cuioss.jwt.validation.domain.claim.ClaimValue} - Represents a claim value with type information</li>
 *   <li>{@link de.cuioss.jwt.validation.domain.claim.ClaimValueType} - Enumeration of supported claim value types</li>
 *   <li>{@link de.cuioss.jwt.validation.domain.claim.CollectionClaimHandler} - Utility for handling collection-based claims like scopes, roles, and groups</li>
 * </ul>
 * <p>
 * The claim processing supports:
 * <ul>
 *   <li>Standard JWT claims (iss, sub, aud, exp, nbf, iat, jti)</li>
 *   <li>OpenID Connect claims (name, email, preferred_username)</li>
 *   <li>OAuth 2.0 claims (scope, client_id)</li>
 *   <li>Common authorization claims (roles, groups)</li>
 *   <li>Custom claims through extension points</li>
 * </ul>
 * <p>
 * The {@link de.cuioss.jwt.validation.domain.claim.mapper} subpackage contains mappers for converting between JSON and typed claim values.
 * <p>
 * This package implements parts of the following requirements:
 * <ul>
 *   <li>CUI-JWT-4.3: Claim Extraction and Validation</li>
 *   <li>CUI-JWT-4.4: Standard Claim Support</li>
 *   <li>CUI-JWT-5.1: OpenID Connect Support</li>
 * </ul>
 * <p>
 * For more details on claim handling, see the
 * <a href="https://github.com/cuioss/cui-jwt-validation/tree/main/doc/specification/technical-components.adoc#token-structure">Token Structure</a>
 * specification.
 * 
 * @author Oliver Wolff
 * @since 1.0
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc7519">RFC 7519 - JSON Web Token (JWT)</a>
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html">OpenID Connect Core 1.0</a>
 */
package de.cuioss.jwt.validation.domain.claim;
