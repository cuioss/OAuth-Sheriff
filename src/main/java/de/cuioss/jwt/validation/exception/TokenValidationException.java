/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cuioss.jwt.validation.exception;

import de.cuioss.jwt.validation.security.EventCategory;
import de.cuioss.jwt.validation.security.SecurityEventCounter.EventType;
import lombok.Getter;
import lombok.NonNull;

import java.io.Serial;

/**
 * Exception thrown when token validation fails.
 * <p>
 * This exception encapsulates information about the validation failure:
 * <ul>
 *   <li>The event type that caused the failure</li>
 *   <li>A detailed error message</li>
 * </ul>
 * <p>
 * The event type includes the event category, which can be used to determine
 * the appropriate HTTP status code or other response mechanism.
 * <p>
 * This exception is thrown by the validation pipeline when a token fails
 * validation, replacing the previous Optional-based error signaling approach.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public class TokenValidationException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The event type that caused the validation failure.
     */
    @Getter
    private final EventType eventType;

    /**
     * Constructs a new TokenValidationException with the specified event type and detail message.
     *
     * @param eventType the event type that caused the validation failure
     * @param message the detail message
     */
    public TokenValidationException(@NonNull EventType eventType, String message) {
        super(message);
        this.eventType = eventType;
    }

    /**
     * Constructs a new TokenValidationException with the specified event type, detail message, and cause.
     *
     * @param eventType the event type that caused the validation failure
     * @param message the detail message
     * @param cause the cause of the validation failure
     */
    public TokenValidationException(@NonNull EventType eventType, String message, Throwable cause) {
        super(message, cause);
        this.eventType = eventType;
    }

    /**
     * Gets the event category for this validation failure.
     * <p>
     * This is a convenience method that delegates to {@link EventType#getCategory()}.
     *
     * @return the event category
     */
    public EventCategory getCategory() {
        return eventType.getCategory();
    }
}