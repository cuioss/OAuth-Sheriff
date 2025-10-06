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
package de.cuioss.jwt.validation.jwks.parser;

import com.dslplatform.json.DslJson;
import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.jwt.validation.ParserConfig;
import de.cuioss.jwt.validation.json.JwkKey;
import de.cuioss.jwt.validation.json.Jwks;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.security.SecurityEventCounter.EventType;
import de.cuioss.tools.logging.CuiLogger;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses and validates JWKS content using DSL-JSON for high-performance parsing.
 * This class is responsible for:
 * <ul>
 *   <li>Parsing JSON content with security limits using DSL-JSON</li>
 *   <li>Validating JWKS structure and constraints</li>
 *   <li>Extracting keys from JWKS structure</li>
 *   <li>Handling both standard JWKS format and single key format</li>
 *   <li>Security event tracking for parsing failures</li>
 * </ul>
 */
@RequiredArgsConstructor
public class JwksParser {

    private static final CuiLogger LOGGER = new CuiLogger(JwksParser.class);

    @NonNull
    private final DslJson<Object> dslJson;

    @NonNull
    private final SecurityEventCounter securityEventCounter;

    @NonNull
    private final ParserConfig parserConfig;

    /**
     * Create JwksParser with ParserConfig and SecurityEventCounter.
     */
    public JwksParser(@NonNull ParserConfig parserConfig, @NonNull SecurityEventCounter securityEventCounter) {
        this.dslJson = parserConfig.getDslJson();
        this.securityEventCounter = securityEventCounter;
        this.parserConfig = parserConfig;
    }

    /**
     * Parse JWKS content and extract individual JWK objects.
     * 
     * @param jwksContent the JWKS content as a string
     * @return a list of parsed JWK objects, empty if parsing fails
     */
    public List<JwkKey> parse(String jwksContent) {
        List<JwkKey> result = new ArrayList<>();

        // Check content size
        if (!validateContentSize(jwksContent)) {
            return result;
        }

        byte[] bytes = jwksContent.getBytes(StandardCharsets.UTF_8);

        // First, try to parse as standard JWKS with "keys" array
        boolean jwksParsed = false;
        try {
            Jwks jwks = dslJson.deserialize(Jwks.class, bytes, bytes.length);
            if (jwks != null && jwks.keys() != null) {
                // We have a valid JWKS with keys field, let parseJwks handle validation and logging
                return parseJwks(jwks);
            }
            jwksParsed = jwks != null;  // Remember if we parsed a JWKS structure
        } catch (IOException e) {
            // JSON syntax error - continue to try single JWK parsing
        }

        // If standard JWKS parsing failed or had no keys, try parsing as single JWK
        try {
            JwkKey singleKey = dslJson.deserialize(JwkKey.class, bytes, bytes.length);
            if (singleKey != null && singleKey.kty() != null) {
                result.add(singleKey);
                return result;
            }
        } catch (IOException e) {
            // If both parsers threw IOException, it's invalid JSON
            LOGGER.error(JWTValidationLogMessages.ERROR.JWKS_INVALID_JSON.format(e.getMessage()));
            securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
            return result;
        }

        // If we successfully parsed a JWKS but it had no keys field
        if (jwksParsed) {
            LOGGER.warn(JWTValidationLogMessages.WARN.JWKS_OBJECT_NULL::format);
            securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
        } else {
            // If both parsing attempts failed with no IOException, it's likely a structure issue
            LOGGER.warn(JWTValidationLogMessages.WARN.JWKS_KEYS_ARRAY_EMPTY::format);
            securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
        }
        return result;
    }

    /**
     * Parse JWKS content that's already a Jwks object.
     * 
     * @param jwks the JWKS content as a Jwks object
     * @return a list of parsed JWK objects, empty if parsing fails
     */
    public List<JwkKey> parse(Jwks jwks) {
        if (jwks == null) {
            LOGGER.error(JWTValidationLogMessages.ERROR.JWKS_INVALID_JSON.format("JWKS object is null"));
            return new ArrayList<>();
        }
        return parseJwks(jwks);
    }

    /**
     * Internal method to parse a Jwks object into individual JWK objects.
     * 
     * @param jwks the JWKS object to parse
     * @return a list of parsed JWK objects
     */
    private List<JwkKey> parseJwks(Jwks jwks) {
        List<JwkKey> result = new ArrayList<>();
        extractKeys(jwks, result);
        return result;
    }

    /**
     * Validate JWKS content size to prevent memory exhaustion attacks.
     * 
     * @param jwksContent the JWKS content
     * @return true if content size is within limits, false otherwise
     */
    private boolean validateContentSize(String jwksContent) {
        int actualSize = jwksContent.getBytes(StandardCharsets.UTF_8).length;
        int upperLimit = parserConfig.getMaxPayloadSize();

        if (actualSize > upperLimit) {
            LOGGER.error(JWTValidationLogMessages.ERROR.JWKS_CONTENT_SIZE_EXCEEDED.format(upperLimit, actualSize));
            securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
            return false;
        }

        return true;
    }

    /**
     * Extract keys from JWKS structure.
     * Handles both standard JWKS format (with "keys" array) and single key format.
     * 
     * @param jwks the JWKS object
     * @param result the list to store extracted keys
     */
    private void extractKeys(Jwks jwks, List<JwkKey> result) {
        // Validate JWKS structure first
        if (!validateJwksStructure(jwks)) {
            return;
        }

        // JWKS structure already contains keys array
        if (jwks.keys() != null && !jwks.keys().isEmpty()) {
            extractKeysFromArray(jwks, result);
        } else {
            // Keys field present but empty
            LOGGER.warn(JWTValidationLogMessages.WARN.JWKS_KEYS_ARRAY_EMPTY::format);
            securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
        }
    }

    /**
     * Validates the structure and content of a JWKS object.
     * 
     * @param jwks the JWKS object to validate
     * @return true if the JWKS structure is valid, false otherwise
     */
    private boolean validateJwksStructure(Jwks jwks) {
        // Basic null check
        if (jwks == null) {
            LOGGER.warn(JWTValidationLogMessages.WARN.JWKS_OBJECT_NULL::format);
            securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
            return false;
        }

        // Check if it has keys array
        if (jwks.keys() != null) {
            List<JwkKey> keysArray = jwks.keys();

            // Check array size limits
            if (keysArray.size() > 50) {
                LOGGER.warn(JWTValidationLogMessages.WARN.JWKS_KEYS_ARRAY_TOO_LARGE.format(keysArray.size()));
                securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
                return false;
            }

            // Don't log here for empty arrays - let extractKeys handle the logging
        }

        return true;
    }

    /**
     * Extract keys from a standard JWKS with "keys" array.
     * 
     * @param jwks the JWKS object
     * @param result the list to store extracted keys
     */
    private void extractKeysFromArray(Jwks jwks, List<JwkKey> result) {
        List<JwkKey> keysArray = jwks.keys();
        if (keysArray != null && !keysArray.isEmpty()) {
            // Validate each key before adding
            for (JwkKey key : keysArray) {
                if (key != null && key.kty() != null) {
                    result.add(key);
                } else if (key != null) {
                    // Key exists but missing kty field
                    LOGGER.warn(JWTValidationLogMessages.WARN.JWK_MISSING_KTY.format(key.kid()));
                    securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
                }
            }
        } else {
            LOGGER.warn(JWTValidationLogMessages.WARN.JWKS_KEYS_ARRAY_EMPTY::format);
            securityEventCounter.increment(EventType.JWKS_JSON_PARSE_FAILED);
        }
    }
}