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
 * JWKS parsing and validation components.
 * <p>
 * This package contains components for parsing, validating, and processing JWKS content:
 * <ul>
 *   <li>{@link de.cuioss.jwt.validation.jwks.parser.JwksParser} - Parses JWKS JSON content</li>
 *   <li>{@link de.cuioss.jwt.validation.jwks.parser.JwksValidator} - Validates JWKS structure and keys</li>
 *   <li>{@link de.cuioss.jwt.validation.jwks.parser.KeyProcessor} - Processes individual JWK objects</li>
 * </ul>
 * <p>
 * These components are designed to be used together to provide secure and robust JWKS processing
 * with proper validation, error handling, and security event tracking.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
package de.cuioss.jwt.validation.jwks.parser;