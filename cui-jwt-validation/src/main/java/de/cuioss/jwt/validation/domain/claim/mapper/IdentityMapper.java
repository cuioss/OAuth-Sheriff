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
package de.cuioss.jwt.validation.domain.claim.mapper;

import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import de.cuioss.jwt.validation.domain.claim.ClaimValueType;
import de.cuioss.jwt.validation.json.MapRepresentation;

import java.util.Optional;

/**
 * A {@link ClaimMapper} implementation that maps a claim from a {@link MapRepresentation} to a
 * {@link ClaimValue} without any transformation.
 * This is useful for claims that are already in the desired format.
 *
 * @since 1.0
 */
public class IdentityMapper implements ClaimMapper {
    @Override
    public ClaimValue map(MapRepresentation mapRepresentation, String claimName) {

        Optional<Object> optionalValue = mapRepresentation.getValue(claimName);
        if (optionalValue.isEmpty()) {
            return ClaimValue.createEmptyClaimValue(ClaimValueType.STRING);
        }
        Object value = optionalValue.get();

        // For IdentityMapper, we convert all types to string representation
        String stringValue;
        if (value instanceof String str) {
            stringValue = str;
        } else {
            // Convert other types (numbers, booleans, arrays, objects) to string
            stringValue = value.toString();
        }

        return ClaimValue.forPlainString(stringValue);
    }
}
