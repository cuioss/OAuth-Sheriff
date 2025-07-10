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

import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.test.TestTokenHolder;
import de.cuioss.jwt.validation.TokenType;
import de.cuioss.jwt.validation.test.generator.ClaimControlParameter;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link BearerTokenResult#errorResponse()} method OAuth compliance.
 * <p>
 * Validates OAuth 2.0 Bearer Token specification (RFC 6750) and OAuth Step-Up
 * Authentication Challenge compliance of error response generation.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@DisplayName("BearerTokenResult Error Response")
class BearerTokenResultErrorResponseTest {

    @Nested
    @DisplayName("FULLY_VERIFIED Response")
    class FullyVerifiedResponse {

        @Test
        @DisplayName("should return 200 OK with security headers")
        void shouldReturn200WithSecurityHeaders() {
            var tokenContent = createTestToken();
            var result = BearerTokenResult.success(tokenContent,
                Collections.emptySet(), Collections.emptySet(), Collections.emptySet());

            Response response = result.errorResponse();

            assertEquals(200, response.getStatus());
            assertEquals("no-store, no-cache, must-revalidate", response.getHeaderString("Cache-Control"));
            assertEquals("no-cache", response.getHeaderString("Pragma"));
            assertNull(response.getHeaderString("WWW-Authenticate"));
        }
    }

    @Nested
    @DisplayName("COULD_NOT_ACCESS_REQUEST Response")
    class CouldNotAccessRequestResponse {

        @Test
        @DisplayName("should return 500 Internal Server Error with error message")
        void shouldReturn500WithErrorMessage() {
            var result = BearerTokenResult.couldNotAccessRequest(
                Collections.emptySet(), Collections.emptySet(), Collections.emptySet());

            Response response = result.errorResponse();

            assertEquals(500, response.getStatus());
            assertEquals("no-store, no-cache, must-revalidate", response.getHeaderString("Cache-Control"));
            assertEquals("no-cache", response.getHeaderString("Pragma"));
            assertEquals("Internal server error: Unable to access request context", response.getEntity());
            assertNull(response.getHeaderString("WWW-Authenticate"));
        }
    }

    @Nested
    @DisplayName("NO_TOKEN_GIVEN Response")
    class NoTokenGivenResponse {

        @Test
        @DisplayName("should return 401 Unauthorized with basic WWW-Authenticate header")
        void shouldReturn401WithBasicWWWAuthenticate() {
            var result = BearerTokenResult.noTokenGiven(
                Collections.emptySet(), Collections.emptySet(), Collections.emptySet());

            Response response = result.errorResponse();

            assertEquals(401, response.getStatus());
            assertEquals("no-store, no-cache, must-revalidate", response.getHeaderString("Cache-Control"));
            assertEquals("no-cache", response.getHeaderString("Pragma"));
            assertEquals("Bearer realm=\"protected-resource\"", response.getHeaderString("WWW-Authenticate"));
        }
    }

    @Nested
    @DisplayName("PARSING_ERROR Response")
    class ParsingErrorResponse {

        @Test
        @DisplayName("should return 401 Unauthorized with invalid_token error")
        void shouldReturn401WithInvalidTokenError() {
            var exception = new TokenValidationException(
                SecurityEventCounter.EventType.INVALID_JWT_FORMAT, "Token format is invalid");
            var result = BearerTokenResult.parsingError(exception,
                Collections.emptySet(), Collections.emptySet(), Collections.emptySet());

            Response response = result.errorResponse();

            assertEquals(401, response.getStatus());
            assertEquals("no-store, no-cache, must-revalidate", response.getHeaderString("Cache-Control"));
            assertEquals("no-cache", response.getHeaderString("Pragma"));
            
            String wwwAuthenticate = response.getHeaderString("WWW-Authenticate");
            assertNotNull(wwwAuthenticate);
            assertTrue(wwwAuthenticate.contains("Bearer realm=\"protected-resource\""));
            assertTrue(wwwAuthenticate.contains("error=\"invalid_token\""));
            assertTrue(wwwAuthenticate.contains("error_description=\"The access token is invalid\""));
        }

        @Test
        @DisplayName("should handle null error message gracefully")
        void shouldHandleNullErrorMessage() {
            var exception = new TokenValidationException(
                SecurityEventCounter.EventType.INVALID_JWT_FORMAT, null);
            var result = BearerTokenResult.parsingError(exception,
                Collections.emptySet(), Collections.emptySet(), Collections.emptySet());

            Response response = result.errorResponse();

            assertEquals(401, response.getStatus());
            String wwwAuthenticate = response.getHeaderString("WWW-Authenticate");
            assertTrue(wwwAuthenticate.contains("error_description=\"The access token is invalid\""));
        }
    }

    @Nested
    @DisplayName("CONSTRAINT_VIOLATION Response")
    class ConstraintViolationResponse {

        @Test
        @DisplayName("should return 401 Unauthorized for scope violation with insufficient_scope error")
        void shouldReturn401ForScopeViolation() {
            var missingScopes = Set.of("read", "write");
            var result = BearerTokenResult.constraintViolation(
                missingScopes, Collections.emptySet(), Collections.emptySet());

            Response response = result.errorResponse();

            assertEquals(401, response.getStatus());
            assertEquals("no-store, no-cache, must-revalidate", response.getHeaderString("Cache-Control"));
            assertEquals("no-cache", response.getHeaderString("Pragma"));
            
            String wwwAuthenticate = response.getHeaderString("WWW-Authenticate");
            assertNotNull(wwwAuthenticate);
            assertTrue(wwwAuthenticate.contains("Bearer realm=\"protected-resource\""));
            assertTrue(wwwAuthenticate.contains("error=\"insufficient_scope\""));
            assertTrue(wwwAuthenticate.contains("error_description=\"The request requires higher privileges than provided by the access token\""));
            // Check both possible orders since Set doesn't guarantee ordering
            boolean hasCorrectScope = wwwAuthenticate.contains("scope=\"read write\"") ||
                                     wwwAuthenticate.contains("scope=\"write read\"");
            assertTrue(hasCorrectScope, "WWW-Authenticate header should contain correctly ordered scopes: " + wwwAuthenticate);
        }

        @Test
        @DisplayName("should return 403 Forbidden for role violation with insufficient_privileges error")
        void shouldReturn403ForRoleViolation() {
            var missingRoles = Set.of("admin", "manager");
            var result = BearerTokenResult.constraintViolation(
                Collections.emptySet(), missingRoles, Collections.emptySet());

            Response response = result.errorResponse();

            assertEquals(403, response.getStatus());
            assertEquals("no-store, no-cache, must-revalidate", response.getHeaderString("Cache-Control"));
            assertEquals("no-cache", response.getHeaderString("Pragma"));
            
            String wwwAuthenticate = response.getHeaderString("WWW-Authenticate");
            assertNotNull(wwwAuthenticate);
            assertTrue(wwwAuthenticate.contains("Bearer realm=\"protected-resource\""));
            assertTrue(wwwAuthenticate.contains("error=\"insufficient_privileges\""));
            assertTrue(wwwAuthenticate.contains("error_description=\"The request requires higher privileges than provided by the access token\""));
            // Check both possible orders since Set doesn't guarantee ordering
            boolean hasCorrectRoles = wwwAuthenticate.contains("required_roles=\"admin manager\"") ||
                                     wwwAuthenticate.contains("required_roles=\"manager admin\"");
            assertTrue(hasCorrectRoles, "WWW-Authenticate header should contain correctly ordered roles: " + wwwAuthenticate);
        }

        @Test
        @DisplayName("should return 403 Forbidden for group violation with insufficient_privileges error")
        void shouldReturn403ForGroupViolation() {
            var missingGroups = Set.of("developers", "testers");
            var result = BearerTokenResult.constraintViolation(
                Collections.emptySet(), Collections.emptySet(), missingGroups);

            Response response = result.errorResponse();

            assertEquals(403, response.getStatus());
            
            String wwwAuthenticate = response.getHeaderString("WWW-Authenticate");
            assertNotNull(wwwAuthenticate);
            assertTrue(wwwAuthenticate.contains("Bearer realm=\"protected-resource\""));
            assertTrue(wwwAuthenticate.contains("error=\"insufficient_privileges\""));
            // Check both possible orders since Set doesn't guarantee ordering
            boolean hasCorrectGroups = wwwAuthenticate.contains("required_groups=\"developers testers\"") ||
                                      wwwAuthenticate.contains("required_groups=\"testers developers\"");
            assertTrue(hasCorrectGroups, "WWW-Authenticate header should contain correctly ordered groups: " + wwwAuthenticate);
        }

        @Test
        @DisplayName("should return 403 Forbidden for combined role and group violation")
        void shouldReturn403ForCombinedRoleGroupViolation() {
            var missingRoles = Set.of("admin");
            var missingGroups = Set.of("developers");
            var result = BearerTokenResult.constraintViolation(
                Collections.emptySet(), missingRoles, missingGroups);

            Response response = result.errorResponse();

            assertEquals(403, response.getStatus());
            
            String wwwAuthenticate = response.getHeaderString("WWW-Authenticate");
            assertNotNull(wwwAuthenticate);
            assertTrue(wwwAuthenticate.contains("required_roles=\"admin\""));
            assertTrue(wwwAuthenticate.contains("required_groups=\"developers\""));
        }

        @Test
        @DisplayName("should prioritize scope violation over role/group violation")
        void shouldPrioritizeScopeViolation() {
            var missingScopes = Set.of("read");
            var missingRoles = Set.of("admin");
            var missingGroups = Set.of("developers");
            var result = BearerTokenResult.constraintViolation(
                missingScopes, missingRoles, missingGroups);

            Response response = result.errorResponse();

            // Should return 401 for scope violation, not 403 for role/group
            assertEquals(401, response.getStatus());
            
            String wwwAuthenticate = response.getHeaderString("WWW-Authenticate");
            assertTrue(wwwAuthenticate.contains("error=\"insufficient_scope\""));
            assertTrue(wwwAuthenticate.contains("scope=\"read\""));
        }
    }

    @Nested
    @DisplayName("Quote Escaping")
    class QuoteEscaping {

        @Test
        @DisplayName("should escape quotes in scope names")
        void shouldEscapeQuotesInScopeNames() {
            var missingScopes = Set.of("read:\"special\"", "write");
            var result = BearerTokenResult.constraintViolation(
                missingScopes, Collections.emptySet(), Collections.emptySet());

            String wwwAuthenticate = result.errorResponse().getHeaderString("WWW-Authenticate");
            
            // Check both possible orders since Set doesn't guarantee ordering
            boolean hasCorrectScope = wwwAuthenticate.contains("scope=\"read:\\\"special\\\" write\"") ||
                                     wwwAuthenticate.contains("scope=\"write read:\\\"special\\\"\"");
            assertTrue(hasCorrectScope, "WWW-Authenticate header should contain correctly escaped scopes: " + wwwAuthenticate);
        }

        @Test
        @DisplayName("should escape quotes in role names")
        void shouldEscapeQuotesInRoleNames() {
            var missingRoles = Set.of("admin:\"special\"");
            var result = BearerTokenResult.constraintViolation(
                Collections.emptySet(), missingRoles, Collections.emptySet());

            String wwwAuthenticate = result.errorResponse().getHeaderString("WWW-Authenticate");
            assertTrue(wwwAuthenticate.contains("required_roles=\"admin:\\\"special\\\"\""));
        }
    }

    @Nested
    @DisplayName("Security Headers")
    class SecurityHeaders {

        @Test
        @DisplayName("should include security headers in all responses")
        void shouldIncludeSecurityHeadersInAllResponses() {
            var testCases = java.util.Map.of(
                "success", BearerTokenResult.success(createTestToken(), Set.of(), Set.of(), Set.of()),
                "couldNotAccess", BearerTokenResult.couldNotAccessRequest(Set.of(), Set.of(), Set.of()),
                "noToken", BearerTokenResult.noTokenGiven(Set.of(), Set.of(), Set.of()),
                "parsingError", BearerTokenResult.parsingError(new TokenValidationException(SecurityEventCounter.EventType.INVALID_JWT_FORMAT, "test"), Set.of(), Set.of(), Set.of()),
                "constraintViolation", BearerTokenResult.constraintViolation(Set.of("read"), Set.of(), Set.of())
            );

            testCases.forEach((name, result) -> {
                var response = result.errorResponse();
                assertEquals("no-store, no-cache, must-revalidate", response.getHeaderString("Cache-Control"),
                    name + " should have Cache-Control header");
                assertEquals("no-cache", response.getHeaderString("Pragma"),
                    name + " should have Pragma header");
            });
        }
    }

    private de.cuioss.jwt.validation.domain.token.AccessTokenContent createTestToken() {
        TestTokenHolder holder = new TestTokenHolder(TokenType.ACCESS_TOKEN, 
            ClaimControlParameter.defaultForTokenType(TokenType.ACCESS_TOKEN));
        return holder.asAccessTokenContent();
    }
}