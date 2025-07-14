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
package de.cuioss.jwt.validation.jwks;

/**
 * Enum representing the status of a JWKS loader.
 * <p>
 * A loader status is:
 * <ul>
 *   <li>OK: if it can load at least one key</li>
 *   <li>ERROR: if it failed to load any keys due to configuration or runtime issues</li>
 *   <li>UNDEFINED: if the status hasn't been determined yet</li>
 * </ul>
 */
public enum LoaderStatus {
    /** The loader is functioning properly and contains at least one key */
    OK("ok"),
    /** The loader has encountered an error and couldn't load any keys */
    ERROR("error"),
    /** The loader's status hasn't been determined yet */
    UNDEFINED("undefined");

    private final String value;

    LoaderStatus(String value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return value;
    }
}
