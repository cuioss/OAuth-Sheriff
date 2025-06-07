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
/**
 * Provides mappers for converting between JSON and typed claim values.
 * <p>
 * These mappers implement the strategy pattern for claim extraction and conversion, 
 * allowing the library to handle different claim formats and types consistently.
 * They are used by the {@link de.cuioss.jwt.validation.domain.claim.ClaimName} enum
 * to convert JSON claim values to strongly-typed {@link de.cuioss.jwt.validation.domain.claim.ClaimValue} objects.
 * <p>
 * Key components:
 * <ul>
 *   <li>{@link de.cuioss.jwt.validation.domain.claim.mapper.ClaimMapper} - Base interface for claim mappers</li>
 *   <li>{@link de.cuioss.jwt.validation.domain.claim.mapper.IdentityMapper} - Maps string values directly</li>
 *   <li>{@link de.cuioss.jwt.validation.domain.claim.mapper.JsonCollectionMapper} - Maps JSON arrays to collections</li>
 *   <li>{@link de.cuioss.jwt.validation.domain.claim.mapper.OffsetDateTimeMapper} - Maps numeric timestamps to OffsetDateTime</li>
 *   <li>{@link de.cuioss.jwt.validation.domain.claim.mapper.ScopeMapper} - Special mapper for the 'scope' claim</li>
 * </ul>
 * <p>
 * The mappers handle various claim formats including:
 * <ul>
 *   <li>Simple string claims (subject, issuer)</li>
 *   <li>Time-based claims in numeric format (expiration, issued-at)</li>
 *   <li>Array-based claims (audiences)</li>
 *   <li>Space-delimited string claims (scopes)</li>
 * </ul>
 * <p>
 * All mappers handle null values gracefully by returning appropriate empty or default values,
 * ensuring that token validation is robust against missing or malformed claims.
 * <p>
 * This package implements parts of the following requirements:
 * <ul>
 *   <li>CUI-JWT-4.3: Claim Extraction and Validation</li>
 *   <li>CUI-JWT-4.4: Standard Claim Support</li>
 * </ul>
 * <p>
 * For more details on claim handling, see the
 * <a href="https://github.com/cuioss/cui-jwt/tree/main/doc/specification/technical-components.adoc#token-structure">Token Structure</a>
 * specification.
 * 
 * @author Oliver Wolff
 * @since 1.0
 * @see de.cuioss.jwt.validation.domain.claim.ClaimName
 * @see de.cuioss.jwt.validation.domain.claim.ClaimValue
 */
package de.cuioss.jwt.validation.domain.claim.mapper;