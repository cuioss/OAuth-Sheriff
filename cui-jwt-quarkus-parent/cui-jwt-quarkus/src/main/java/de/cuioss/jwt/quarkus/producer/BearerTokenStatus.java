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

/**
 * Enumeration representing the different states of bearer token validation.
 * <p>
 * This enum provides structured information about the outcome of bearer token
 * processing, allowing consumers to understand exactly what happened during
 * token validation and respond appropriately.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public enum BearerTokenStatus {

    /**
     * The bearer token was successfully validated and all requirements were met.
     * <p>
     * This indicates that:
     * <ul>
     *   <li>The token was extracted from the Authorization header</li>
     *   <li>The token was successfully parsed and validated</li>
     *   <li>All required scopes, roles, and groups were present</li>
     *   <li>An AccessTokenContent instance was created</li>
     * </ul>
     */
    FULLY_VERIFIED,

    /**
     * The HTTP request could not be accessed to extract the bearer token.
     * <p>
     * This occurs when the HttpServletRequest is not available in the current
     * context, typically in non-web environments or when the servlet resolver
     * fails to provide the request object.
     */
    COULD_NOT_ACCESS_REQUEST,

    /**
     * No bearer token was provided in the Authorization header.
     * <p>
     * This indicates that either:
     * <ul>
     *   <li>The Authorization header is missing</li>
     *   <li>The Authorization header is empty</li>
     *   <li>The Authorization header does not start with "Bearer "</li>
     * </ul>
     */
    NO_TOKEN_GIVEN,

    /**
     * The bearer token could not be parsed or validated.
     * <p>
     * This occurs when a TokenValidationException is thrown during token
     * validation, indicating issues such as:
     * <ul>
     *   <li>Invalid JWT structure</li>
     *   <li>Expired token</li>
     *   <li>Invalid signature</li>
     *   <li>Missing required claims</li>
     * </ul>
     */
    PARSING_ERROR,

    /**
     * The bearer token was valid but did not meet the specified requirements.
     * <p>
     * This indicates that the token was successfully parsed and validated,
     * but failed to meet one or more of the BearerToken annotation requirements:
     * <ul>
     *   <li>Missing required scopes</li>
     *   <li>Missing required roles</li>
     *   <li>Missing required groups</li>
     * </ul>
     */
    CONSTRAINT_VIOLATION
}