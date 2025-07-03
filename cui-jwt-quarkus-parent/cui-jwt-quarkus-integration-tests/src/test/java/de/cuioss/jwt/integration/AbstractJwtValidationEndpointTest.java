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
package de.cuioss.jwt.integration;

import org.junit.jupiter.api.*;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Abstract base test class for testing JwtValidationEndpoint endpoints.
 * Provides comprehensive testing of all JWT validation endpoints with both positive and negative scenarios.
 */
@DisplayName("JWT Validation Endpoint Tests")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public abstract class AbstractJwtValidationEndpointTest extends BaseIntegrationTest {

    public static final String AUTHORIZATION = "Authorization";
    // String constants for repeated literals
    private static final String CONTENT_TYPE_JSON = "application/json";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String JWT_VALIDATE_PATH = "/jwt/validate";
    private static final String JWT_VALIDATE_ID_TOKEN_PATH = "/jwt/validate/id-token";
    private static final String JWT_VALIDATE_REFRESH_TOKEN_PATH = "/jwt/validate/refresh-token";
    private static final String ACCESS_TOKEN_VALID_MESSAGE = "Access token is valid";
    private static final String TOKEN_FIELD_NAME = "token";
    public static final String VALID = "valid";
    public static final String MESSAGE = "message";
    public static final String REFRESH_TOKEN_IS_VALID = "Refresh token is valid";

    /**
     * Returns the TestRealm instance to use for testing.
     * Implementations should return either TestRealm.createBenchmarkRealm() or TestRealm.createIntegrationRealm().
     *
     * @return TestRealm instance for testing
     */
    protected abstract TestRealm getTestRealm();

    @Test
    @Order(1)
    @DisplayName("Verify Keycloak health and token obtaining functionality")
    void testKeycloakHealthiness() {
        // First test: Healthiness including resolving of tokens using TestRealm#isKeycloakHealthy
        assertTrue(getTestRealm().isKeycloakHealthy(), "Keycloak should be healthy and accessible");

        // Add verification of getTestRealm().obtainValidToken() with all tokens not being null
        TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidToken();
        assertNotNull(tokenResponse.accessToken(), "Access token should not be null");
        assertNotNull(tokenResponse.idToken(), "ID token should not be null");
        assertNotNull(tokenResponse.refreshToken(), "Refresh token should not be null");
    }

    @Nested
    @DisplayName("Positive Tests - Valid Token Validation")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class PositiveTests {

        @Test
        @Order(2)
        @DisplayName("Validate access token via Authorization header")
        void testValidateAccessTokenEndpoint_Positive() {
            // Obtain tokens locally for this test
            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidToken();
            String validAccessToken = tokenResponse.accessToken();

            // Test positive case: valid access token via Authorization header
            given()
                    .contentType(CONTENT_TYPE_JSON)
                    .header(AUTHORIZATION, BEARER_PREFIX + validAccessToken)
                    .when()
                    .post(JWT_VALIDATE_PATH)
                    .then()
                    .statusCode(200)
                    .body(VALID, equalTo(true))
                    .body(MESSAGE, equalTo(ACCESS_TOKEN_VALID_MESSAGE));
        }

        @Test
        @Order(3)
        @DisplayName("Validate ID token via request body")
        void testValidateIdTokenEndpoint_Positive() {
            // Obtain tokens locally for this test
            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidToken();
            String validIdToken = tokenResponse.idToken();

            // Test positive case: valid ID token via request body
            given()
                    .contentType(CONTENT_TYPE_JSON)
                    .body(Map.of(TOKEN_FIELD_NAME, validIdToken))
                    .when()
                    .post(JWT_VALIDATE_ID_TOKEN_PATH)
                    .then()
                    .statusCode(200)
                    .body(VALID, equalTo(true))
                    .body(MESSAGE, equalTo("ID token is valid"));
        }

        @Test
        @Order(4)
        @DisplayName("Validate refresh token via request body")
        void testValidateRefreshTokenEndpoint_Positive() {
            // Obtain tokens locally for this test
            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidToken();
            String validRefreshToken = tokenResponse.refreshToken();

            // Test positive case: valid refresh token via request body
            given()
                    .contentType(CONTENT_TYPE_JSON)
                    .body(Map.of(TOKEN_FIELD_NAME, validRefreshToken))
                    .when()
                    .post(JWT_VALIDATE_REFRESH_TOKEN_PATH)
                    .then()
                    .statusCode(200)
                    .body(VALID, equalTo(true))
                    .body(MESSAGE, equalTo(REFRESH_TOKEN_IS_VALID));
        }

        @Test
        @Order(14)
        @DisplayName("Validate access token with multiple consecutive requests")
        void testValidateAccessTokenEndpoint_MultipleRequests() {
            // Obtain tokens locally for this test
            TestRealm.TokenResponse tokenResponse = getTestRealm().obtainValidToken();
            String validAccessToken = tokenResponse.accessToken();

            // Test multiple consecutive requests
            for (int i = 0; i < 3; i++) {
                given()
                        .contentType(CONTENT_TYPE_JSON)
                        .header(AUTHORIZATION, BEARER_PREFIX + validAccessToken)
                        .when()
                        .post(JWT_VALIDATE_PATH)
                        .then()
                        .statusCode(200)
                        .body(VALID, equalTo(true))
                        .body(MESSAGE, equalTo(ACCESS_TOKEN_VALID_MESSAGE));
            }
        }
    }

    @Nested
    @DisplayName("Negative Tests - Invalid Token Validation")
    @TestMethodOrder(MethodOrderer.OrderAnnotation.class)
    class NegativeTests {

        @Test
        @Order(5)
        @DisplayName("Access token validation with missing Authorization header")
        void testValidateAccessTokenEndpoint_MissingAuthorizationHeader() {
            // Test negative case: missing Authorization header
            given()
                    .contentType(CONTENT_TYPE_JSON)
                    .when()
                    .post(JWT_VALIDATE_PATH)
                    .then()
                    .statusCode(400)
                    .body(VALID, equalTo(false))
                    .body(MESSAGE, equalTo("Missing or invalid Authorization header"));
        }

        @Test
        @Order(6)
        @DisplayName("Access token validation with invalid Authorization header format")
        void testValidateAccessTokenEndpoint_InvalidAuthorizationHeader() {
            // Test negative case: invalid Authorization header format
            given()
                    .contentType(CONTENT_TYPE_JSON)
                    .header(AUTHORIZATION, "InvalidFormat token")
                    .when()
                    .post(JWT_VALIDATE_PATH)
                    .then()
                    .statusCode(400)
                    .body(VALID, equalTo(false))
                    .body(MESSAGE, equalTo("Missing or invalid Authorization header"));
        }

        @Test
        @Order(7)
        @DisplayName("Access token validation with invalid token")
        void testValidateAccessTokenEndpoint_InvalidToken() {
            // Test negative case: invalid access token
            given()
                    .contentType(CONTENT_TYPE_JSON)
                    .header(AUTHORIZATION, BEARER_PREFIX + "invalid.token.here")
                    .when()
                    .post(JWT_VALIDATE_PATH)
                    .then()
                    .statusCode(401)
                    .body(VALID, equalTo(false))
                    .body(MESSAGE, containsString("Access token validation failed"));
        }

        @Test
        @Order(8)
        @DisplayName("ID token validation with missing request body")
        void testValidateIdTokenEndpoint_MissingRequestBody() {
            // Test negative case: missing request body
            given()
                    .contentType(CONTENT_TYPE_JSON)
                    .when()
                    .post(JWT_VALIDATE_ID_TOKEN_PATH)
                    .then()
                    .statusCode(400)
                    .body(VALID, equalTo(false))
                    .body(MESSAGE, equalTo("Missing or empty ID token in request body"));
        }

        @Test
        @Order(9)
        @DisplayName("ID token validation with empty token")
        void testValidateIdTokenEndpoint_EmptyToken() {
            // Test negative case: empty token in request body
            given()
                    .contentType(CONTENT_TYPE_JSON)
                    .body(Map.of(TOKEN_FIELD_NAME, ""))
                    .when()
                    .post(JWT_VALIDATE_ID_TOKEN_PATH)
                    .then()
                    .statusCode(400)
                    .body(VALID, equalTo(false))
                    .body(MESSAGE, equalTo("Missing or empty ID token in request body"));
        }

        @Test
        @Order(10)
        @DisplayName("ID token validation with invalid token")
        void testValidateIdTokenEndpoint_InvalidToken() {
            // Test negative case: invalid ID token
            given()
                    .contentType(CONTENT_TYPE_JSON)
                    .body(Map.of(TOKEN_FIELD_NAME, "invalid.token.here"))
                    .when()
                    .post(JWT_VALIDATE_ID_TOKEN_PATH)
                    .then()
                    .statusCode(401)
                    .body(VALID, equalTo(false))
                    .body(MESSAGE, containsString("ID token validation failed"));
        }

        @Test
        @Order(11)
        @DisplayName("Refresh token validation with missing request body")
        void testValidateRefreshTokenEndpoint_MissingRequestBody() {
            // Test negative case: missing request body
            given()
                    .contentType(CONTENT_TYPE_JSON)
                    .when()
                    .post(JWT_VALIDATE_REFRESH_TOKEN_PATH)
                    .then()
                    .statusCode(400)
                    .body(VALID, equalTo(false))
                    .body(MESSAGE, equalTo("Missing or empty refresh token in request body"));
        }

        @Test
        @Order(12)
        @DisplayName("Refresh token validation with empty token")
        void testValidateRefreshTokenEndpoint_EmptyToken() {
            // Test negative case: empty token in request body
            given()
                    .contentType(CONTENT_TYPE_JSON)
                    .body(Map.of(TOKEN_FIELD_NAME, ""))
                    .when()
                    .post(JWT_VALIDATE_REFRESH_TOKEN_PATH)
                    .then()
                    .statusCode(400)
                    .body(VALID, equalTo(false))
                    .body(MESSAGE, equalTo("Missing or empty refresh token in request body"));
        }

        @Test
        @Order(13)
        @DisplayName("Refresh token validation with invalid token")
        void testValidateRefreshTokenEndpoint_InvalidToken() {
            // Test negative case: Will result in a positive result, because Refresh-token are opaquely invalid
            given()
                    .contentType(CONTENT_TYPE_JSON)
                    .body(Map.of(TOKEN_FIELD_NAME, "invalid.token.here"))
                    .when()
                    .post(JWT_VALIDATE_REFRESH_TOKEN_PATH)
                    .then()
                    .statusCode(200)
                    .body(VALID, equalTo(true))
                    .body(MESSAGE, containsString(REFRESH_TOKEN_IS_VALID));
        }
    }
}
