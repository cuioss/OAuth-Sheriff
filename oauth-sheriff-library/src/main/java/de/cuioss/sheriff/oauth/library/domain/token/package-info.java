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
 * Provides interfaces and implementations for JWT Token content.
 * <p>
 * This package defines a comprehensive type hierarchy for representing different
 * token types in OAuth 2.0 and OpenID Connect:
 * <ul>
 *   <li>{@link de.cuioss.sheriff.oauth.library.domain.token.TokenContent} - Base interface for JWT Token content</li>
 *   <li>{@link de.cuioss.sheriff.oauth.library.domain.token.MinimalTokenContent} - Minimal interface for token content with raw token string and type</li>
 *   <li>{@link de.cuioss.sheriff.oauth.library.domain.token.BaseTokenContent} - Abstract base implementation of token content</li>
 *   <li>{@link de.cuioss.sheriff.oauth.library.domain.token.AccessTokenContent} - Specialized implementation for OAuth2 access tokens</li>
 *   <li>{@link de.cuioss.sheriff.oauth.library.domain.token.IdTokenContent} - Specialized implementation for OpenID Connect ID tokens</li>
 *   <li>{@link de.cuioss.sheriff.oauth.library.domain.token.RefreshTokenContent} - Specialized implementation for OAuth2 refresh tokens</li>
 * </ul>
 * <p>
 * The token implementations:
 * <ul>
 *   <li>Provide type-safe access to claims through consistent APIs</li>
 *   <li>Implement token-type specific functionality (e.g., scope, role, and group validation for access tokens)</li>
 *   <li>Support both required and optional claims defined in the specifications</li>
 *   <li>Support common but non-standardized claims like roles and groups</li>
 *   <li>Maintain immutability for thread safety</li>
 * </ul>
 * <p>
 * This package implements the following requirements:
 * <ul>
 *   <li>CUI-JWT-4.1: JWT Token Structure</li>
 *   <li>CUI-JWT-4.2: Token Types</li>
 *   <li>CUI-JWT-5.1: OpenID Connect Support</li>
 * </ul>
 * <p>
 * For more details on token structure and usage, see the
 * <a href="https://github.com/cuioss/OAuth-Sheriff/tree/main/doc/specification/technical-components.adoc#token-types">Token Types</a>
 * specification.
 *
 * @author Oliver Wolff
 * @since 1.0
 * @see de.cuioss.sheriff.oauth.library.domain.claim.ClaimName
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc6749">RFC 6749 - OAuth 2.0</a>
 * @see <a href="https://openid.net/specs/openid-connect-core-1_0.html">OpenID Connect Core 1.0</a>
 */
package de.cuioss.sheriff.oauth.library.domain.token;
