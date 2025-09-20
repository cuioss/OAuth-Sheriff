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
package de.cuioss.jwt.validation.json;

import com.dslplatform.json.DslJson;
import de.cuioss.tools.string.MoreStrings;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A type-safe wrapper for Map&lt;String, Object&gt; that provides convenient access methods
 * for JWT claim processing.
 * <p>
 * This record encapsulates access to the underlying Map&lt;String, Object&gt; representation
 * of JWT claims, providing type-safe methods for extracting common claim types such as
 * strings, numbers, lists, and nested objects.
 * <p>
 * The record follows the same access patterns as the previous JsonObject-based approach
 * but works with the DSL-JSON deserialized Map&lt;String, Object&gt; structure.
 * <p>
 * Key features:
 * <ul>
 *   <li>Type-safe access to string, number, boolean, and list claims</li>
 *   <li>Support for nested object access</li>
 *   <li>Null-safe operations with Optional returns</li>
 *   <li>Direct compatibility with ClaimMapper implementations</li>
 * </ul>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public record MapRepresentation(Map<String, Object> data) implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;


    /**
     * Factory method to create MapRepresentation from JSON string using DSL-JSON.
     * <p>
     * This method encapsulates the deserialization logic within MapRepresentation,
     * allowing for future customization of parsing behavior and reducing coupling
     * with external deserialization code.
     *
     * @param dslJson the DSL-JSON instance configured for deserialization
     * @param jsonContent the JSON string content to parse
     * @return a new MapRepresentation containing the parsed data
     * @throws IOException if the JSON content cannot be parsed
     */
    public static MapRepresentation fromJson(@NonNull DslJson<Object> dslJson, String jsonContent) throws IOException {
        return fromJson(dslJson, MoreStrings.nullToEmpty(jsonContent).getBytes());
    }

    /**
     * Factory method to create MapRepresentation from JSON byte array using DSL-JSON.
     * <p>
     * This overload provides direct byte array processing without string conversion,
     * which can be more efficient when working with pre-encoded JSON data.
     *
     * @param dslJson the DSL-JSON instance configured for deserialization
     * @param jsonBytes the JSON byte array to parse
     * @return a new MapRepresentation containing the parsed data
     * @throws IOException if the JSON content cannot be parsed
     */
    public static MapRepresentation fromJson(@NonNull DslJson<Object> dslJson, byte [] jsonBytes) throws IOException {
        if (null == jsonBytes || jsonBytes.length == 0) {
            // Return empty MapRepresentation for null/empty JSON
            return new MapRepresentation(Map.of());
        }
        @SuppressWarnings("unchecked") Map<String, Object> parsedData = dslJson.deserialize(Map.class, jsonBytes, jsonBytes.length);

        if (parsedData == null) {
            // Return empty MapRepresentation for null/empty JSON
            return new MapRepresentation(Map.of());
        }

        // Create immutable copy to ensure immutability, filtering out null values
        // Map.copyOf() doesn't allow null values, so we need to filter them out
        Map<String, Object> filteredData = parsedData.entrySet().stream()
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue
                ));
        return new MapRepresentation(filteredData);
    }

    /**
     * Factory method to create an empty MapRepresentation.
     *
     * @return an empty MapRepresentation
     */
    public static MapRepresentation empty() {
        return new MapRepresentation(Map.of());
    }


    /**
     * Checks if the map contains a key.
     *
     * @param key the key to check
     * @return true if the key exists, false otherwise
     */
    public boolean containsKey(String key) {
        return data != null && data.containsKey(key);
    }

    /**
     * Gets a value as an Object.
     *
     * @param key the key to look up
     * @return Optional containing the value, or empty if not found
     */
    public Optional<Object> getValue(String key) {
        return data != null ? Optional.ofNullable(data.get(key)) : Optional.empty();
    }

    /**
     * Gets a value as a String.
     *
     * @param key the key to look up
     * @return Optional containing the string value, or empty if not found or not a string
     */
    public Optional<String> getString(String key) {
        if (data == null) return Optional.empty();
        Object value = data.get(key);
        if (value instanceof String string) {
            return Optional.of(string);
        }
        return Optional.empty();
    }

    /**
     * Gets a value as a Number.
     *
     * @param key the key to look up
     * @return Optional containing the number value, or empty if not found or not a number
     */
    public Optional<Number> getNumber(String key) {
        if (data == null) return Optional.empty();
        Object value = data.get(key);
        if (value instanceof Number number) {
            return Optional.of(number);
        }
        return Optional.empty();
    }

    /**
     * Gets a value as a Boolean.
     *
     * @param key the key to look up
     * @return Optional containing the boolean value, or empty if not found or not a boolean
     */
    public Optional<Boolean> getBoolean(String key) {
        if (data == null) return Optional.empty();
        Object value = data.get(key);
        if (value instanceof Boolean boolean1) {
            return Optional.of(boolean1);
        }
        return Optional.empty();
    }

    /**
     * Gets a value as a List.
     *
     * @param key the key to look up
     * @return Optional containing the list value, or empty if not found or not a list
     */
    @SuppressWarnings("unchecked")
    public Optional<List<Object>> getList(String key) {
        if (data == null) return Optional.empty();
        Object value = data.get(key);
        if (value instanceof List) {
            return Optional.of((List<Object>) value);
        }
        return Optional.empty();
    }

    /**
     * Gets a value as a nested Map (for nested objects).
     *
     * @param key the key to look up
     * @return Optional containing the nested map, or empty if not found or not a map
     */
    @SuppressWarnings("unchecked")
    public Optional<Map<String, Object>> getMap(String key) {
        if (data == null) return Optional.empty();
        Object value = data.get(key);
        if (value instanceof Map) {
            return Optional.of((Map<String, Object>) value);
        }
        return Optional.empty();
    }

    /**
     * Gets a nested MapRepresentation for accessing nested objects.
     *
     * @param key the key to look up
     * @return Optional containing the nested MapRepresentation, or empty if not found or not a map
     */
    public Optional<MapRepresentation> getNestedMap(String key) {
        return getMap(key).map(MapRepresentation::new);
    }

    /**
     * Gets a string list from an array claim.
     *
     * @param key the key to look up
     * @return Optional containing the string list, or empty if not found or not convertible
     */
    public Optional<List<String>> getStringList(String key) {
        return getList(key).map(list ->
                list.stream()
                        .filter(String.class::isInstance)
                        .map(String.class::cast)
                        .toList()
        );
    }

    /**
     * Checks if the map is empty.
     *
     * @return true if the map is empty, false otherwise
     */
    public boolean isEmpty() {
        return data == null || data.isEmpty();
    }

    /**
     * Gets the size of the map.
     *
     * @return the number of key-value pairs
     */
    public int size() {
        return data == null ? 0 : data.size();
    }

    /**
     * Returns the key set of the underlying map.
     *
     * @return the set of keys in the map
     */
    public Set<String> keySet() {
        return data == null ? Set.of() : data.keySet();
    }

}