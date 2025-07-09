/**
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
/**
 * Provides CDI annotations and qualifiers for the CUI JWT Quarkus extension.
 * <p>
 * This package contains:
 * <ul>
 *   <li>CDI qualifiers for injecting JWT-related components</li>
 *   <li>Annotations for configuring JWT validation requirements</li>
 * </ul>
 * <p>
 * Key annotations:
 * <ul>
 *   <li>{@link de.cuioss.jwt.quarkus.annotation.BearerToken} - CDI qualifier for injecting validated AccessTokenContent</li>
 * </ul>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
package de.cuioss.jwt.quarkus.annotation;