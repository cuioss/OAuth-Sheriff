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
package de.cuioss.jwt.validation.security;

import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.tools.logging.LogRecord;
import lombok.NonNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Provides counters for relevant security events in the JWT Token handling module.
 * <p>
 * This class is designed to be thread-safe and highly concurrent, allowing for
 * accurate counting of security events in multi-threaded environments.
 * <p>
 * The counter follows the same naming/numbering scheme as {@link JWTValidationLogMessages}
 * for consistency and easier correlation between logs and metrics.
 * <p>
 * Security events that are tracked include:
 * <ul>
 *   <li>Token format issues - empty tokens, size violations, decoding failures</li>
 *   <li>Missing claims - required fields not present in tokens</li>
 *   <li>Validation failures - expired tokens, audience mismatches</li>
 *   <li>Signature issues - validation failures, missing keys</li>
 *   <li>Algorithm issues - unsupported or insecure algorithms</li>
 *   <li>JWKS issues - endpoint failures, parsing errors</li>
 *   <li>Successful operations - token creations</li>
 * </ul>
 * <p>
 * This implementation is structured to simplify later integration with micrometer
 * but does not create any dependency on it.
 * <p>
 * For more details on the security monitoring aspects, see the
 * <a href="https://github.com/cuioss/cui-jwt/tree/main/doc/specification/security.adoc">Security Specification</a>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public class SecurityEventCounter {

    /**
     * Enum defining all security event types that can be counted.
     * <p>
     * Each event type has an identifier that follows the same numbering scheme
     * as {@link JWTValidationLogMessages}.
     */
    public enum EventType {
        // Token format issues
        TOKEN_EMPTY(JWTValidationLogMessages.WARN.TOKEN_IS_EMPTY, EventCategory.INVALID_STRUCTURE),
        TOKEN_SIZE_EXCEEDED(JWTValidationLogMessages.WARN.TOKEN_SIZE_EXCEEDED, EventCategory.INVALID_STRUCTURE),
        FAILED_TO_DECODE_JWT(JWTValidationLogMessages.WARN.FAILED_TO_DECODE_JWT, EventCategory.INVALID_STRUCTURE),
        INVALID_JWT_FORMAT(JWTValidationLogMessages.WARN.INVALID_JWT_FORMAT, EventCategory.INVALID_STRUCTURE),
        FAILED_TO_DECODE_HEADER(JWTValidationLogMessages.WARN.FAILED_TO_DECODE_HEADER, EventCategory.INVALID_STRUCTURE),
        FAILED_TO_DECODE_PAYLOAD(JWTValidationLogMessages.WARN.FAILED_TO_DECODE_PAYLOAD, EventCategory.INVALID_STRUCTURE),
        DECODED_PART_SIZE_EXCEEDED(JWTValidationLogMessages.WARN.DECODED_PART_SIZE_EXCEEDED, EventCategory.INVALID_STRUCTURE),

        // Missing claims
        MISSING_CLAIM(JWTValidationLogMessages.WARN.MISSING_CLAIM, EventCategory.SEMANTIC_ISSUES),
        MISSING_RECOMMENDED_ELEMENT(JWTValidationLogMessages.WARN.MISSING_RECOMMENDED_ELEMENT, EventCategory.SEMANTIC_ISSUES),

        // Validation failures
        TOKEN_EXPIRED(JWTValidationLogMessages.WARN.TOKEN_EXPIRED, EventCategory.SEMANTIC_ISSUES),
        TOKEN_NBF_FUTURE(JWTValidationLogMessages.WARN.TOKEN_NBF_FUTURE, EventCategory.SEMANTIC_ISSUES),
        AUDIENCE_MISMATCH(JWTValidationLogMessages.WARN.AUDIENCE_MISMATCH, EventCategory.SEMANTIC_ISSUES),
        AZP_MISMATCH(JWTValidationLogMessages.WARN.AZP_MISMATCH, EventCategory.SEMANTIC_ISSUES),
        ISSUER_MISMATCH(JWTValidationLogMessages.WARN.ISSUER_MISMATCH, EventCategory.SEMANTIC_ISSUES),
        NO_ISSUER_CONFIG(JWTValidationLogMessages.WARN.NO_ISSUER_CONFIG, EventCategory.SEMANTIC_ISSUES),

        // Signature issues
        SIGNATURE_VALIDATION_FAILED(JWTValidationLogMessages.ERROR.SIGNATURE_VALIDATION_FAILED, EventCategory.INVALID_SIGNATURE),
        KEY_NOT_FOUND(JWTValidationLogMessages.WARN.KEY_NOT_FOUND, EventCategory.INVALID_SIGNATURE),

        // Algorithm issues
        UNSUPPORTED_ALGORITHM(JWTValidationLogMessages.WARN.UNSUPPORTED_ALGORITHM, EventCategory.INVALID_SIGNATURE),

        // JWKS issues
        JWKS_FETCH_FAILED(JWTValidationLogMessages.WARN.JWKS_FETCH_FAILED, EventCategory.INVALID_SIGNATURE),
        JWKS_JSON_PARSE_FAILED(JWTValidationLogMessages.WARN.JWKS_JSON_PARSE_FAILED, EventCategory.INVALID_SIGNATURE),
        FAILED_TO_READ_JWKS_FILE(JWTValidationLogMessages.WARN.FAILED_TO_READ_JWKS_FILE, EventCategory.INVALID_SIGNATURE),
        KEY_ROTATION_DETECTED(JWTValidationLogMessages.WARN.KEY_ROTATION_DETECTED, EventCategory.INVALID_SIGNATURE),

        // Successful operations
        ACCESS_TOKEN_CREATED(JWTValidationLogMessages.DEBUG.ACCESS_TOKEN_CREATED, null),
        ID_TOKEN_CREATED(JWTValidationLogMessages.DEBUG.ID_TOKEN_CREATED, null),
        REFRESH_TOKEN_CREATED(JWTValidationLogMessages.DEBUG.REFRESH_TOKEN_CREATED, null);

        private final LogRecord logRecord;
        private final EventCategory category;

        EventType(LogRecord logRecord, EventCategory category) {
            this.logRecord = logRecord;
            this.category = category;
        }

        /**
         * @return the event category for this event type, or null for successful operations
         */
        public EventCategory getCategory() {
            return category;
        }

        /**
         * @return the numeric identifier for this event type
         */
        public int getId() {
            return logRecord.getIdentifier();
        }

        /**
         * @return a human-readable description of this event type
         */
        public String getDescription() {
            return logRecord.getTemplate();
        }

        /**
         * Returns the corresponding log record from {@link JWTValidationLogMessages}
         * that is associated with this event type.
         * <p>
         * This method provides a bidirectional link between the event type and its
         * corresponding log message, allowing for consistent error reporting and logging.
         * 
         * @return the corresponding log record from JWTValidationLogMessages
         */
        public LogRecord getLogRecord() {
            return logRecord;
        }
    }

    private final ConcurrentHashMap<EventType, AtomicLong> counters = new ConcurrentHashMap<>();

    /**
     * Increments the counter for the specified event type.
     * <p>
     * If the counter doesn't exist yet, it will be created.
     * 
     * @param eventType the type of security event to count
     * @return the new count value
     */
    public long increment(@NonNull EventType eventType) {
        return counters.computeIfAbsent(eventType, k -> new AtomicLong(0)).incrementAndGet();
    }

    /**
     * Gets the current count for the specified event type.
     * 
     * @param eventType the type of security event
     * @return the current count, or 0 if the event has never been counted
     */
    public long getCount(@NonNull EventType eventType) {
        AtomicLong counter = counters.get(eventType);
        return counter != null ? counter.get() : 0;
    }

    /**
     * Gets a snapshot of all current counter values.
     * 
     * @return an unmodifiable map of event types to their current counts
     */
    public Map<EventType, Long> getCounters() {
        return counters.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().get()));
    }

    /**
     * Resets all counters to zero.
     */
    public void reset() {
        counters.clear();
    }

    /**
     * Resets the counter for the specified event type to zero.
     * 
     * @param eventType the type of security event to reset
     */
    public void reset(@NonNull EventType eventType) {
        counters.remove(eventType);
    }
}
