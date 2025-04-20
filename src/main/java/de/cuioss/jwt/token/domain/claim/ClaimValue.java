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
package de.cuioss.jwt.token.domain.claim;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NonNull;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.SortedSet;

/**
 * Represents a typed claim value in a JWT token.
 * All implementations must be immutable, properly implement equals/hashCode,
 * and be serializable.
 */
@ToString
@EqualsAndHashCode
public class ClaimValue implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * The original string representation of this claim value as it appeared in the token.
     * All claim values must preserve their original string representation to maintain
     * fidelity with the original token format.
     */
    @Getter
    private final String originalString;

    /**
     * The type of this claim value.
     */
    @Getter
    @NonNull
    private final ClaimValueType type;

    /**
     * Only relevant for {@link ClaimValueType#STRING_LIST}
     */
    @Getter
    @NonNull // Must not be null, but may be empty
    private final List<String> asList;

    /**
     * Only relevant for {@link ClaimValueType#DATETIME}
     */
    @Getter
    private final OffsetDateTime dateTime;

    /**
     * Constructor for ClaimValue.
     *
     * @param originalString the original string representation of the claim value
     * @param type the type of the claim value
     * @param asList the list of string values (only relevant for STRING_LIST)
     * @param dateTime the OffsetDateTime value (only relevant for DATETIME)
     */
    public ClaimValue(String originalString, @NonNull ClaimValueType type,
            @NonNull List<String> asList, OffsetDateTime dateTime) {
        this.originalString = originalString;
        this.type = type;
        this.asList = asList;
        this.dateTime = dateTime;
    }

    /**
     * Checks if the value is present (not null).
     *
     * @return true if the value is present, false otherwise
     */
    public boolean isPresent() {
        return null != originalString;
    }

    /**
     * Checks if the value is empty (null).
     *
     * @return true if the value is empty, false otherwise
     */
    public boolean isEmpty() {
        return null == originalString;
    }

    /**
     * Checks if the claim value is NOT present for the current {@link ClaimValueType}.
     * For STRING_LIST, it checks if the list is empty.
     * For DATETIME, it checks if the dateTime is null.
     *
     * @return true if the claim value is NOT present for the claim value type, false otherwise
     */
    public boolean isNotPresentForClaimValueType() {
        if (isPresent()) {
            return true;
        }
        return !switch (getType()) {
            case STRING_LIST -> !asList.isEmpty();
            case DATETIME -> dateTime != null;
            default -> true;
        };
    }

    /**
     * Creates a ClaimValue for a plain string. With {@link ClaimValueType#STRING}
     *
     * @param originalString the original string representation of the claim value
     */
    public static ClaimValue forPlainString(String originalString) {
        return new ClaimValue(originalString, ClaimValueType.STRING, Collections.emptyList(), null);
    }

    /**
     * Creates a ClaimValue for a sorted set of strings. With {@link ClaimValueType#STRING_LIST}.
     * This method is provided for backward compatibility with code that previously used STRING_SET.
     *
     * @param originalString the original string representation of the claim value
     * @param sortedSet      the sorted set of string values
     */
    public static ClaimValue forSortedSet(String originalString, @NonNull SortedSet<String> sortedSet) {
        return new ClaimValue(originalString, ClaimValueType.STRING_LIST, new ArrayList<>(sortedSet), null);
    }

    /**
     * Creates a ClaimValue for a list of strings. With {@link ClaimValueType#STRING_LIST}.
     *
     * @param originalString the original string representation of the claim value
     * @param list           the list of string values
     */
    public static ClaimValue forList(String originalString, List<String> list) {
        return new ClaimValue(originalString, ClaimValueType.STRING_LIST, list, null);
    }

    /**
     * Creates a ClaimValue for a datetime. With {@link ClaimValueType#DATETIME}
     *
     * @param originalString the original string representation of the claim value
     * @param dateTime       the OffsetDateTime value
     */
    public static ClaimValue forDateTime(String originalString, OffsetDateTime dateTime) {
        return new ClaimValue(originalString, ClaimValueType.DATETIME, Collections.emptyList(), dateTime);
    }

    /**
     * Creates a default ClaimValue for a missing or null claim.
     * This method should be used when the JSON object is null, does not contain the claim,
     * or the claim value is null.
     *
     * @param valueType the type of the claim value
     * @return a default ClaimValue for the given type
     */
    public static ClaimValue createDefaultClaimValue(ClaimValueType valueType) {
        return switch (valueType) {
            case STRING -> forPlainString(null);
            case STRING_LIST -> forList(null, Collections.emptyList());
            case DATETIME -> forDateTime(null, null);
        };
    }
}
