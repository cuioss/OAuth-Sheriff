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
package de.cuioss.jwt.validation.pipeline;

import com.dslplatform.json.DslJson;
import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.jwt.validation.ParserConfig;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.json.JwtHeader;
import de.cuioss.jwt.validation.json.MapRepresentation;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.tools.logging.CuiLogger;
import de.cuioss.tools.string.MoreStrings;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * This class provides a unified way to parse JWT tokens and extract common information
 * such as the header, body, signature, issuer, and kid-header.
 * <p>
 * Security features:
 * <ul>
 *   <li>Token size validation to prevent memory exhaustion</li>
 *   <li>Payload size validation for JSON parsing</li>
 *   <li>Standard Base64 decoding for JWT parts</li>
 *   <li>Proper character encoding handling</li>
 *   <li>JSON depth limits to prevent stack overflow attacks</li>
 *   <li>JSON array size limits to prevent denial-of-service attacks</li>
 *   <li>JSON string size limits to prevent memory exhaustion</li>
 * </ul>
 * <p>
 * Basic usage example:
 * <pre>
 * // Create a parser with default settings
 * NonValidatingJwtParser parser = NonValidatingJwtParser.builder()
 *     .securityEventCounter(securityEventCounter)
 *     .build();
 *
 * // Decode a JWT token
 * Optional&lt;DecodedJwt&gt; decodedJwt = parser.decode(tokenString);
 *
 * // Access decoded JWT information using convenience methods
 * decodedJwt.ifPresent(jwt -> {
 *     // Access common JWT fields with convenience methods
 *     jwt.getAlg().ifPresent(alg -> LOGGER.debug("Algorithm: %s", alg));
 *     jwt.getIssuer().ifPresent(issuer -> LOGGER.debug("Issuer: %s", issuer));
 *     jwt.getKid().ifPresent(kid -> LOGGER.debug("Key ID: %s", kid));
 *
 *     // Access the raw token
 *     String rawToken = jwt.getRawToken();
 *
 *     // For more complex operations, access the header and body JSON objects
 *     jwt.getHeader().ifPresent(header -> {
 *         // Process header fields not covered by convenience methods
 *     });
 *
 *     jwt.getBody().ifPresent(body -> {
 *         // Process custom claims in the body
 *         if (body.containsKey("custom_claim")) {
 *             String customValue = body.getString("custom_claim");
 *         }
 *     });
 * });
 * </pre>
 * <p>
 * Example with custom security settings:
 * <pre>
 * // Create a parser with custom security settings using ParserConfig
 * ParserConfig config = ParserConfig.builder()
 *     .maxTokenSize(1024)  // 1KB max token size
 *     .maxPayloadSize(512)  // 512 bytes max payload size
 *     .maxStringSize(256)   // 256 bytes max string size
 *     .maxArraySize(10)     // 10 elements max array size
 *     .maxDepth(5)          // 5 levels max JSON depth
 *     .build();
 *
 * NonValidatingJwtParser customParser = NonValidatingJwtParser.builder()
 *     .config(config)
 *     .securityEventCounter(securityEventCounter)
 *     .build();
 *
 * // Decode a token with the custom parser
 * Optional&lt;DecodedJwt&gt; result = customParser.decode(tokenString);
 * </pre>
 * <p>
 * Example handling empty or invalid tokens:
 * <pre>
 * // Handle empty or null tokens
 * Optional&lt;DecodedJwt&gt; emptyResult = parser.decode("");
 * assertFalse(emptyResult.isPresent());
 *
 * // Handle invalid token format
 * Optional&lt;DecodedJwt&gt; invalidResult = parser.decode("invalid.token.format");
 * assertFalse(invalidResult.isPresent());
 * </pre>
 * <p>
 * For more details on the security aspects, see the
 * <a href="https://github.com/cuioss/cui-jwt/tree/main/doc/specification/security.adoc">Security Specification</a>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@ToString
@EqualsAndHashCode
public class NonValidatingJwtParser {

    private static final CuiLogger LOGGER = new CuiLogger(NonValidatingJwtParser.class);

    /**
     * Configuration for the parser, containing all security settings.
     */
    private final ParserConfig config;

    /**
     * Counter for security events that occur during token processing.
     */
    @NonNull
    private final SecurityEventCounter securityEventCounter;

    private NonValidatingJwtParser(ParserConfig config, @NonNull SecurityEventCounter securityEventCounter) {
        this.config = config != null ? config : ParserConfig.builder().build();
        this.securityEventCounter = securityEventCounter;
    }

    public static NonValidatingJwtParserBuilder builder() {
        return new NonValidatingJwtParserBuilder();
    }

    public static class NonValidatingJwtParserBuilder {
        private ParserConfig config = ParserConfig.builder().build();
        private SecurityEventCounter securityEventCounter;

        public NonValidatingJwtParserBuilder config(ParserConfig config) {
            this.config = config;
            return this;
        }

        public NonValidatingJwtParserBuilder securityEventCounter(SecurityEventCounter securityEventCounter) {
            this.securityEventCounter = securityEventCounter;
            return this;
        }

        public NonValidatingJwtParser build() {
            return new NonValidatingJwtParser(config, securityEventCounter);
        }
    }

    /**
     * Decodes a JWT token and returns a DecodedJwt object containing the decoded parts.
     * <p>
     * Security considerations:
     * <ul>
     *   <li>Does not validate signatures - use only for inspection</li>
     *   <li>Implements size checks to prevent overflow attacks</li>
     *   <li>Uses standard Java Base64 decoder</li>
     * </ul>
     * <p>
     * This method logs warnings when decoding fails. Use {@link #decode(String, boolean)}
     * to control warning logging behavior.
     *
     * @param token the JWT token string to parse
     * @return the DecodedJwt if parsing is successful
     * @throws TokenValidationException if the token is invalid or cannot be parsed
     */
    public DecodedJwt decode(String token) {
        return decode(token, true);
    }

    /**
     * Decodes a JWT token without logging warnings or tracking security events.
     * <p>
     * This method is specifically designed for opaque tokens (like refresh tokens) where
     * parsing failures are expected and should not be logged or tracked as security events.
     * <p>
     * Security considerations:
     * <ul>
     *   <li>Does not validate signatures - use only for inspection</li>
     *   <li>Implements size checks to prevent overflow attacks</li>
     *   <li>Uses standard Java Base64 decoder</li>
     *   <li>No logging or security event tracking on failures</li>
     * </ul>
     *
     * @param token the JWT token string to parse (may be opaque)
     * @return the DecodedJwt if parsing is successful
     * @throws TokenValidationException if the token is invalid or cannot be parsed
     */
    public DecodedJwt decodeOpaqueToken(String token) {
        return decodeInternal(token, false, false);
    }

    /**
     * Decodes a JWT token and returns a DecodedJwt object containing the decoded parts.
     * <p>
     * Security considerations:
     * <ul>
     *   <li>Does not validate signatures - use only for inspection</li>
     *   <li>Implements size checks to prevent overflow attacks</li>
     *   <li>Uses standard Java Base64 decoder</li>
     * </ul>
     * <p>
     * This method allows controlling whether warnings are logged when decoding fails.
     * This is useful when checking if a token is a JWT without logging warnings.
     *
     * @param token       the JWT token string to parse
     * @param logWarnings whether to log warnings when decoding fails
     * @return the DecodedJwt if parsing is successful
     * @throws TokenValidationException if the token is invalid or cannot be parsed
     */
    public DecodedJwt decode(String token, boolean logWarnings) {
        return decodeInternal(token, logWarnings, true);
    }

    /**
     * Internal method that handles token decoding with configurable logging and security tracking.
     *
     * @param token the JWT token string to parse
     * @param logWarnings whether to log warnings when decoding fails
     * @param trackSecurityEvents whether to track security events on failures
     * @return the DecodedJwt if parsing is successful
     * @throws TokenValidationException if the token is invalid or cannot be parsed
     */
    @SuppressWarnings("java:S3776") // owolff: Justified - complexity due to logging and security event tracking
    private DecodedJwt decodeInternal(String token, boolean logWarnings, boolean trackSecurityEvents) {
        // Check if token is empty
        if (MoreStrings.isEmpty(token)) {
            if (logWarnings) {
                LOGGER.warn(JWTValidationLogMessages.WARN.TOKEN_IS_EMPTY::format);
            }
            if (trackSecurityEvents) {
                securityEventCounter.increment(SecurityEventCounter.EventType.TOKEN_EMPTY);
            }
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.TOKEN_EMPTY,
                    "Token is empty or null"
            );
        }

        // Check if token size exceeds maximum
        if (token.getBytes(StandardCharsets.UTF_8).length > config.getMaxTokenSize()) {
            if (logWarnings) {
                LOGGER.warn(JWTValidationLogMessages.WARN.TOKEN_SIZE_EXCEEDED.format(config.getMaxTokenSize()));
            }
            if (trackSecurityEvents) {
                securityEventCounter.increment(SecurityEventCounter.EventType.TOKEN_SIZE_EXCEEDED);
            }
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.TOKEN_SIZE_EXCEEDED,
                    JWTValidationLogMessages.WARN.TOKEN_SIZE_EXCEEDED.format(config.getMaxTokenSize())
            );
        }

        // Split token and validate format
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            if (logWarnings) {
                LOGGER.warn(JWTValidationLogMessages.WARN.INVALID_JWT_FORMAT.format(parts.length));
            }
            if (trackSecurityEvents) {
                securityEventCounter.increment(SecurityEventCounter.EventType.INVALID_JWT_FORMAT);
            }
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.INVALID_JWT_FORMAT,
                    JWTValidationLogMessages.WARN.INVALID_JWT_FORMAT.format(parts.length)
            );
        }

        try {
            // Decode token parts
            return decodeTokenParts(parts, token, logWarnings, trackSecurityEvents);
        } catch (IllegalArgumentException e) {
            if (logWarnings) {
                LOGGER.warn(e, JWTValidationLogMessages.WARN.FAILED_TO_DECODE_JWT.format());
            }
            if (trackSecurityEvents) {
                securityEventCounter.increment(SecurityEventCounter.EventType.FAILED_TO_DECODE_JWT);
            }
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.FAILED_TO_DECODE_JWT,
                    "Failed to decode JWT: %s".formatted(e.getMessage()),
                    e
            );
        }
    }


    /**
     * Decodes the token parts and creates a DecodedJwt object using DSL-JSON.
     *
     * @param parts                the token parts
     * @param token                the original token
     * @param logWarnings          whether to log warnings
     * @param trackSecurityEvents  whether to track security events
     * @return the DecodedJwt if decoding is successful
     * @throws TokenValidationException if decoding fails
     */
    private DecodedJwt decodeTokenParts(String[] parts, String token, boolean logWarnings, boolean trackSecurityEvents) {
        try {
            // Decode the header (first part) to JwtHeader using DSL-JSON
            JwtHeader header = decodeJwtHeader(parts[0]);

            // Decode the payload (second part) to MapRepresentation using DSL-JSON
            MapRepresentation body = decodePayload(parts[1]);

            // The signature part (third part) is kept as is
            String signature = parts[2];

            return new DecodedJwt(header, body, signature, parts, token);
        } catch (IOException e) {
            // IOException from DSL-JSON deserialization
            if (logWarnings) {
                LOGGER.warn(e, JWTValidationLogMessages.WARN.FAILED_TO_DECODE_JWT.format());
            }
            if (trackSecurityEvents) {
                securityEventCounter.increment(SecurityEventCounter.EventType.FAILED_TO_DECODE_JWT);
            }
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.FAILED_TO_DECODE_JWT,
                    "Failed to decode JWT parts: " + e.getMessage(),
                    e
            );
        }
    }

    /**
     * Decodes a Base64Url encoded JWT header using DSL-JSON.
     *
     * @param encodedHeader the Base64Url encoded header part
     * @return the decoded JwtHeader
     * @throws IOException if decoding fails
     */
    private JwtHeader decodeJwtHeader(String encodedHeader) throws IOException {
        String decodedJson = decodeBase64UrlPart(encodedHeader);
        DslJson<Object> dslJson = config.getDslJson();

        byte[] jsonBytes = decodedJson.getBytes();
        JwtHeader header = dslJson.deserialize(JwtHeader.class, jsonBytes, jsonBytes.length);

        if (header == null) {
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.FAILED_TO_DECODE_JWT,
                    "Failed to deserialize JWT header: null result"
            );
        }

        return header;
    }

    /**
     * Decodes a Base64Url encoded JWT payload using DSL-JSON.
     *
     * @param encodedPayload the Base64Url encoded payload part
     * @return the decoded MapRepresentation
     * @throws IOException if decoding fails
     */
    private MapRepresentation decodePayload(String encodedPayload) throws IOException {
        String decodedJson = decodeBase64UrlPart(encodedPayload);
        DslJson<Object> dslJson = config.getDslJson();

        return MapRepresentation.fromJson(dslJson, decodedJson);
    }

    /**
     * Decodes a Base64Url encoded part to JSON string.
     *
     * @param encodedPart the Base64Url encoded part
     * @return the decoded JSON string
     */
    private String decodeBase64UrlPart(String encodedPart) {
        try {
            byte[] decodedBytes = Base64.getUrlDecoder().decode(encodedPart);

            // Check payload size limit to prevent memory exhaustion attacks
            if (decodedBytes.length > config.getMaxPayloadSize()) {
                LOGGER.warn(JWTValidationLogMessages.WARN.DECODED_PART_SIZE_EXCEEDED.format(config.getMaxPayloadSize()));
                securityEventCounter.increment(SecurityEventCounter.EventType.DECODED_PART_SIZE_EXCEEDED);
                throw new TokenValidationException(
                        SecurityEventCounter.EventType.DECODED_PART_SIZE_EXCEEDED,
                        JWTValidationLogMessages.WARN.DECODED_PART_SIZE_EXCEEDED.format(config.getMaxPayloadSize())
                );
            }

            return new String(decodedBytes, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.FAILED_TO_DECODE_JWT,
                    "Failed to decode Base64Url part: " + e.getMessage(),
                    e
            );
        }
    }


}
