/*
 * Copyright 2025 the original author or authors.
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
package de.cuioss.jwt.validation.test.generator;

import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import de.cuioss.jwt.validation.domain.claim.ClaimValueType;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Generator for {@link ClaimValue} objects.
 * Generates arbitrary claim values of different types (STRING, STRING_LIST, DATETIME).
 */
public class ClaimValueGenerator implements TypedGenerator<ClaimValue> {

    private final boolean allowNullOriginalString;
    private final ClaimValueType fixedType;

    /**
     * Constructor with default settings.
     * Generates random claim values of any type with non-null original strings.
     */
    public ClaimValueGenerator() {
        this(false, null);
    }

    /**
     * Constructor with configurable null handling.
     *
     * @param allowNullOriginalString if true, may generate claim values with null original strings
     */
    public ClaimValueGenerator(boolean allowNullOriginalString) {
        this(allowNullOriginalString, null);
    }

    /**
     * Constructor with configurable type.
     *
     * @param fixedType if not null, only generates claim values of this type
     */
    public ClaimValueGenerator(ClaimValueType fixedType) {
        this(false, fixedType);
    }

    /**
     * Constructor with fully configurable settings.
     *
     * @param allowNullOriginalString if true, may generate claim values with null original strings
     * @param fixedType if not null, only generates claim values of this type
     */
    public ClaimValueGenerator(boolean allowNullOriginalString, ClaimValueType fixedType) {
        this.allowNullOriginalString = allowNullOriginalString;
        this.fixedType = fixedType;
    }

    @Override
    public ClaimValue next() {
        // Determine the type of claim value to generate
        ClaimValueType type = fixedType != null ? fixedType : randomClaimValueType();

        // Generate the original string (may be null if allowNullOriginalString is true)
        String originalString = generateOriginalString();

        return switch (type) {
            case STRING -> generateStringClaimValue(originalString);
            case STRING_LIST -> generateStringListClaimValue(originalString);
            case DATETIME -> generateDateTimeClaimValue(originalString);
        };
    }

    private ClaimValueType randomClaimValueType() {
        ClaimValueType[] types = ClaimValueType.values();
        return types[ThreadLocalRandom.current().nextInt(types.length)];
    }

    private String generateOriginalString() {
        if (allowNullOriginalString && ThreadLocalRandom.current().nextBoolean()) {
            return null;
        }
        return Generators.strings(5, 20).next();
    }

    private ClaimValue generateStringClaimValue(String originalString) {
        return ClaimValue.forPlainString(originalString);
    }

    private ClaimValue generateStringListClaimValue(String originalString) {
        int listSize = ThreadLocalRandom.current().nextInt(0, 5);
        List<String> list = new ArrayList<>(listSize);

        for (int i = 0; i < listSize; i++) {
            list.add(Generators.letterStrings(3, 10).next());
        }

        return ClaimValue.forList(originalString, list);
    }

    private ClaimValue generateDateTimeClaimValue(String originalString) {
        // Generate a random date within the last 10 years
        long now = System.currentTimeMillis();
        long tenYearsInMillis = 10L * 365 * 24 * 60 * 60 * 1000;
        long randomTime = now - ThreadLocalRandom.current().nextLong(0, tenYearsInMillis);

        OffsetDateTime dateTime = OffsetDateTime.ofInstant(
                Instant.ofEpochMilli(randomTime),
                ZoneOffset.UTC);

        return ClaimValue.forDateTime(originalString, dateTime);
    }
}