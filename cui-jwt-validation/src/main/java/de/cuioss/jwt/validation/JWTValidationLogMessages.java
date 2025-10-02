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
package de.cuioss.jwt.validation;

import de.cuioss.tools.logging.LogRecord;
import de.cuioss.tools.logging.LogRecordModel;
import lombok.experimental.UtilityClass;

/**
 * Provides logging messages for the cui-jwt-validation module.
 * All messages follow the format: JWTValidation-[identifier]: [message]
 * <p>
 * Implements requirements:
 * <ul>
 *   <li><a href="https://github.com/cuioss/cui-jwt/tree/main/doc/Requirements.adoc#CUI-JWT-7">CUI-JWT-7: Logging</a></li>
 *   <li><a href="https://github.com/cuioss/cui-jwt/tree/main/doc/Requirements.adoc#CUI-JWT-7.1">CUI-JWT-7.1: Structured Logging</a></li>
 *   <li><a href="https://github.com/cuioss/cui-jwt/tree/main/doc/Requirements.adoc#CUI-JWT-7.2">CUI-JWT-7.2: Helpful Error Messages</a></li>
 * </ul>
 * <p>
 * For more detailed information about log messages, see the
 * <a href="https://github.com/cuioss/cui-jwt/tree/main/doc/LogMessages.adoc">Log Messages Documentation</a>
 *
 * @since 1.0
 */
@UtilityClass
public final class JWTValidationLogMessages {

    private static final String PREFIX = "JWTValidation";

    /**
     * Contains error-level log messages for significant problems that require attention.
     * These messages indicate failures that impact functionality but don't necessarily
     * prevent the application from continuing to run.
     */
    @UtilityClass
    public static final class ERROR {
        public static final LogRecord SIGNATURE_VALIDATION_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(200)
                .template("Failed to validate validation signature: %s")
                .build();

        public static final LogRecord JWKS_CONTENT_SIZE_EXCEEDED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(201)
                .template("JWKS content size exceeds maximum allowed size (upperLimit=%s, actual=%s)")
                .build();

        public static final LogRecord JWKS_INVALID_JSON = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(202)
                .template("Failed to parse JWKS JSON: %s")
                .build();

        public static final LogRecord JWKS_LOAD_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(203)
                .template("Failed to load JWKS")
                .build();

        // New entries for direct logging conversions
        public static final LogRecord UNSUPPORTED_JWKS_TYPE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(204)
                .template("Unsupported JwksType for HttpJwksLoader: %s")
                .build();

        public static final LogRecord JSON_PARSE_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(205)
                .template("Failed to parse JSON from %s: %s")
                .build();

        public static final LogRecord CACHE_TOKEN_NO_EXPIRATION = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(206)
                .template("Token passed validation but has no expiration time - this indicates a validation bug")
                .build();

        public static final LogRecord CACHE_TOKEN_STORE_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(207)
                .template("Unexpected error while caching token")
                .build();

        public static final LogRecord CACHE_VALIDATION_FUNCTION_NULL = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(208)
                .template("Validation function returned null instead of throwing exception")
                .build();

        public static final LogRecord CACHE_EVICTION_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(209)
                .template("Error during cache eviction")
                .build();

        public static final LogRecord JWKS_INITIALIZATION_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(210)
                .template("JWKS initialization failed: %s for issuer: %s")
                .build();

        public static final LogRecord JWKS_LOAD_EXECUTION_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(211)
                .template("JWKS load execution failed: %s for issuer: %s")
                .build();

        // Retry operation error messages are handled at WARN level
    }

    /**
     * Contains info-level log messages for general operational information.
     * These messages provide high-level information about the normal operation
     * of the application that is useful for monitoring.
     */
    @UtilityClass
    public static final class INFO {
        public static final LogRecord TOKEN_FACTORY_INITIALIZED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(1)
                .template("TokenValidator initialized with %s")
                .build();

        public static final LogRecord JWKS_KEYS_UPDATED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(2)
                .template("Keys updated due to data change - load state: %s")
                .build();

        public static final LogRecord JWKS_HTTP_LOADED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(3)
                .template("Successfully loaded JWKS from HTTP endpoint")
                .build();

        public static final LogRecord JWKS_BACKGROUND_REFRESH_STARTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(4)
                .template("Background JWKS refresh started with interval: %s seconds")
                .build();

        public static final LogRecord JWKS_BACKGROUND_REFRESH_UPDATED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(5)
                .template("Background JWKS refresh updated keys, load state: %s")
                .build();

        // New entries for direct logging conversions
        public static final LogRecord ISSUER_CONFIG_SKIPPED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(6)
                .template("Skipping disabled issuer configuration %s")
                .build();

        public static final LogRecord JWKS_URI_RESOLVED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(7)
                .template("Successfully resolved JWKS URI from well-known endpoint: %s")
                .build();

        // Retry operation info messages
        public static final LogRecord RETRY_OPERATION_COMPLETED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(8)
                .template("Retry operation '%s' completed successfully after %s attempts in %sms")
                .build();

        public static final LogRecord JWKS_LOADED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(9)
                .template("JWKS loaded successfully for issuer: %s")
                .build();

        public static final LogRecord ISSUER_CONFIG_LOADED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(10)
                .template("Issuer configuration loaded successfully: %s")
                .build();
    }

    /**
     * Contains warning-level log messages for potential issues that don't prevent
     * normal operation but may indicate problems. These messages highlight situations
     * that should be monitored or addressed to prevent future errors.
     */
    @UtilityClass
    public static final class WARN {
        public static final LogRecord TOKEN_SIZE_EXCEEDED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(100)
                .template("Token exceeds maximum size limit of %s bytes, validation will be rejected")
                .build();

        public static final LogRecord TOKEN_IS_EMPTY = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(101)
                .template("The given validation was empty, request will be rejected")
                .build();

        public static final LogRecord KEY_NOT_FOUND = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(102)
                .template("No key found with ID: %s")
                .build();

        public static final LogRecord FAILED_TO_DECODE_JWT = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(103)
                .template("Failed to decode JWT Token")
                .build();

        public static final LogRecord INVALID_JWT_FORMAT = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(104)
                .template("Invalid JWT Token format: expected 3 parts but got %s")
                .build();

        public static final LogRecord DECODED_PART_SIZE_EXCEEDED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(105)
                .template("Decoded part exceeds maximum size limit of %s bytes")
                .build();

        public static final LogRecord UNSUPPORTED_ALGORITHM = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(106)
                .template("Unsupported algorithm: %s")
                .build();

        public static final LogRecord TOKEN_NBF_FUTURE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(107)
                .template("Token has a 'not before' claim that is more than 60 seconds in the future")
                .build();

        public static final LogRecord UNKNOWN_TOKEN_TYPE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(108)
                .template("Unknown validation type: %s")
                .build();

        public static final LogRecord MISSING_CLAIM = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(109)
                .template("Token is missing required claim: %s")
                .build();

        public static final LogRecord TOKEN_EXPIRED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(110)
                .template("Token has expired")
                .build();

        public static final LogRecord AZP_MISMATCH = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(111)
                .template("Token authorized party '%s' does not match expected client ID '%s'")
                .build();

        public static final LogRecord MISSING_RECOMMENDED_ELEMENT = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(112)
                .template("Missing recommended element: %s")
                .build();

        public static final LogRecord AUDIENCE_MISMATCH = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(113)
                .template("Token audience %s does not match any of the expected audiences %s")
                .build();

        public static final LogRecord NO_ISSUER_CONFIG = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(114)
                .template("No configuration found for issuer: %s")
                .build();

        public static final LogRecord ALGORITHM_REJECTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(115)
                .template("Algorithm %s is explicitly rejected for security reasons")
                .build();

        public static final LogRecord INVALID_JWKS_URI = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(116)
                .template("Creating HttpJwksLoaderConfig with invalid JWKS URI. The loader will return empty results.")
                .build();

        public static final LogRecord JWKS_LOAD_FAILED_CACHED_CONTENT = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(117)
                .template("Load operation failed but using cached content")
                .build();

        public static final LogRecord JWKS_LOAD_FAILED_NO_CACHE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(118)
                .template("Load operation failed with no cached content available")
                .build();

        public static final LogRecord JWK_MISSING_KTY = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(119)
                .template("JWK is missing required field 'kty'")
                .build();

        public static final LogRecord JWK_UNSUPPORTED_KEY_TYPE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(120)
                .template("Unsupported key type: %s")
                .build();

        public static final LogRecord JWK_KEY_ID_TOO_LONG = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(121)
                .template("Key ID exceeds maximum length: %s")
                .build();

        public static final LogRecord JWK_INVALID_ALGORITHM = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(122)
                .template("Invalid or unsupported algorithm: %s")
                .build();

        // New entries for direct logging conversions
        public static final LogRecord ISSUER_CONFIG_UNHEALTHY = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(123)
                .template("Found unhealthy issuer config: %s")
                .build();

        public static final LogRecord BACKGROUND_REFRESH_SKIPPED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(124)
                .template("Background refresh skipped - no HTTP cache available")
                .build();

        public static final LogRecord BACKGROUND_REFRESH_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(125)
                .template("Background JWKS refresh failed: %s")
                .build();

        public static final LogRecord JWKS_URI_RESOLUTION_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(126)
                .template("Failed to resolve JWKS URI from well-known resolver")
                .build();

        public static final LogRecord JWKS_OBJECT_NULL = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(127)
                .template("JWKS object is null")
                .build();

        public static final LogRecord JWKS_KEYS_ARRAY_TOO_LARGE = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(128)
                .template("JWKS keys array exceeds maximum size: %s")
                .build();

        public static final LogRecord JWKS_KEYS_ARRAY_EMPTY = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(129)
                .template("JWKS keys array is empty")
                .build();

        public static final LogRecord RSA_KEY_PARSE_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(130)
                .template("Failed to parse RSA key with ID %s: %s")
                .build();

        public static final LogRecord EC_KEY_PARSE_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(131)
                .template("Failed to parse EC key with ID %s: %s")
                .build();

        // Retry operation warning messages
        public static final LogRecord RETRY_OPERATION_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(132)
                .template("Retry operation '%s' failed after %s attempts in %sms")
                .build();

        public static final LogRecord JWKS_JSON_PARSE_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(133)
                .template("Failed to parse JWKS JSON: %s")
                .build();

        public static final LogRecord CLAIM_SUB_OPTIONAL_WARNING = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(134)
                .template("IssuerConfig for issuer '%s' has claimSubOptional=true. This is not conform to RFC 7519 which requires the 'sub' claim for ACCESS_TOKEN and ID_TOKEN types. Use this setting only when necessary and ensure appropriate alternative validation mechanisms.")
                .build();

        public static final LogRecord INVALID_BASE64_URL_ENCODING = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(135)
                .template("Invalid Base64 URL encoding detected for JWK field: %s")
                .build();

        public static final LogRecord BACKGROUND_REFRESH_NO_HANDLER = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(136)
                .template("Background refresh skipped - no HTTP handler available")
                .build();

        public static final LogRecord BACKGROUND_REFRESH_IO_ERROR = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(137)
                .template("Background refresh IO error: %s for issuer: %s")
                .build();

        public static final LogRecord BACKGROUND_REFRESH_PARSE_ERROR = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(138)
                .template("Background refresh parse error: %s for issuer: %s")
                .build();

        public static final LogRecord BACKGROUND_REFRESH_KEY_ERROR = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(139)
                .template("Background refresh key processing error: %s for issuer: %s")
                .build();

        public static final LogRecord ISSUER_CONFIG_LOAD_FAILED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(140)
                .template("Failed to load issuer configuration for %s, status: %s")
                .build();

        public static final LogRecord JWKS_LOAD_TIMEOUT = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(141)
                .template("Timeout waiting for JWKS to load for issuer: %s")
                .build();

        public static final LogRecord JWKS_LOAD_INTERRUPTED = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(142)
                .template("Interrupted while waiting for JWKS to load for issuer: %s")
                .build();

        public static final LogRecord ISSUER_MISMATCH = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(143)
                .template("Configured issuer '%s' does not match discovered issuer '%s' from well-known document")
                .build();

        public static final LogRecord INSECURE_HTTP_JWKS = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(144)
                .template("Using insecure HTTP protocol for JWKS endpoint: %s - HTTPS should be used in production")
                .build();

        public static final LogRecord JWKS_PARSE_NULL_RESULT = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(145)
                .template("DSL-JSON returned null for JWKS parsing")
                .build();

        public static final LogRecord JWKS_PARSE_IO_ERROR = LogRecordModel.builder()
                .prefix(PREFIX)
                .identifier(146)
                .template("Failed to parse JWKS content: %s")
                .build();
    }

}
