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
package de.cuioss.jwt.quarkus.health;

import de.cuioss.jwt.quarkus.config.JwtTestProfile;
import de.cuioss.test.juli.junit5.EnableTestLogger;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Liveness;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(JwtTestProfile.class)
@EnableTestLogger
@DisplayName("TokenValidatorHealthCheck")
class TokenValidatorHealthCheckTest {

    @Inject
    @Liveness
    TokenValidatorHealthCheck healthCheck;


    @Nested
    @DisplayName("Health Check Response")
    class HealthCheckResponseTests {

        @Test
        @DisplayName("should return UP status with valid configuration")
        void healthCheckShouldReturnUpStatus() {
            HealthCheckResponse response = healthCheck.call();
            assertNotNull(response, "HealthCheckResponse should not be null");
            assertEquals(HealthCheckResponse.Status.UP, response.getStatus(),
                    "Health check status should be UP with valid configuration");
        }

        @Test
        @DisplayName("should have correct name")
        void healthCheckName() {
            HealthCheckResponse response = healthCheck.call();
            assertEquals("jwt-validator", response.getName(),
                    "Health check should have correct name");
        }

        @Test
        @DisplayName("should include correct data for UP status")
        void healthCheckDataForUpStatus() {
            HealthCheckResponse response = healthCheck.call();

            // Verify response has data
            assertTrue(response.getData().isPresent(),
                    "Health check data should be present for UP status");

            Map<String, Object> data = response.getData().get();

            // UP status should have issuer count
            assertTrue(data.containsKey("issuerCount"),
                    "Health check data should contain issuerCount when UP");

            Object issuerCountValue = data.get("issuerCount");
            assertNotNull(issuerCountValue, "issuerCount should not be null");

            assertInstanceOf(Number.class, issuerCountValue,
                    "issuerCount should be a Number, but was: " + issuerCountValue.getClass().getSimpleName());

            int issuerCount = ((Number) issuerCountValue).intValue();
            assertTrue(issuerCount > 0,
                    "issuerCount should be greater than 0 when UP, but was: " + issuerCount);
        }

        @Test
        @DisplayName("should handle valid configuration gracefully")
        void healthCheckValidConfiguration() {
            HealthCheckResponse response = healthCheck.call();

            // Response should be valid with proper configuration
            assertNotNull(response, "Response should not be null");
            assertEquals(HealthCheckResponse.Status.UP, response.getStatus(),
                    "Health check status should be UP with valid configuration");
            assertEquals("jwt-validator", response.getName(),
                    "Health check should have correct name");

            assertTrue(response.getData().isPresent(), "Data should be present");
            Map<String, Object> data = response.getData().get();
            assertTrue(data.containsKey("issuerCount"),
                    "Should contain issuer count for valid configuration");
        }

        @Test
        @DisplayName("should maintain response structure consistency between calls")
        void shouldTestHealthCheckResponseStructure() {
            HealthCheckResponse response1 = healthCheck.call();
            HealthCheckResponse response2 = healthCheck.call();

            assertEquals(response1.getName(), response2.getName(),
                    "Health check name should be consistent");
            assertEquals("jwt-validator", response1.getName(),
                    "Health check should have correct name");

            assertTrue(response1.getData().isPresent(), "First response should have data");
            assertTrue(response2.getData().isPresent(), "Second response should have data");

            assertEquals(HealthCheckResponse.Status.UP, response1.getStatus(),
                    "First response status should be UP with valid configuration");
            assertEquals(HealthCheckResponse.Status.UP, response2.getStatus(),
                    "Second response status should be UP with valid configuration");

            assertEquals(response1.getStatus(), response2.getStatus(),
                    "Response status should be consistent between calls");
        }
    }

    @Nested
    @DisplayName("Error Scenarios")
    class ErrorScenarios {

        @Test
        @DisplayName("should handle null issuer configurations in constructor")
        void shouldHandleNullTokenValidatorInConstructor() {
            TokenValidatorHealthCheck healthCheckWithNull = new TokenValidatorHealthCheck(null);

            assertNotNull(healthCheckWithNull, "Health check should be created even with null issuer configurations");

            HealthCheckResponse response = healthCheckWithNull.call();

            assertNotNull(response, "Response should not be null");
            assertEquals(HealthCheckResponse.Status.DOWN, response.getStatus(),
                    "Status should be DOWN for null issuer configurations");
            assertEquals("jwt-validator", response.getName(),
                    "Health check should have correct name");

            assertTrue(response.getData().isPresent(), "Data should be present");
            Map<String, Object> data = response.getData().get();
            assertTrue(data.containsKey("error"), "Should contain error key");
            assertEquals("No issuer configurations found", data.get("error"),
                    "Should have correct error message");
        }
    }
}
