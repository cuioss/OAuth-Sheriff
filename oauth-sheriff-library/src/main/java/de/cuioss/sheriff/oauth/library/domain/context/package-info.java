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
/**
 * Provides context objects for JWT validation operations.
 * <p>
 * This package contains domain objects that carry state and context
 * throughout the JWT validation pipeline, enabling efficient caching
 * and optimization of validation operations.
 * <p>
 * Key classes:
 * <ul>
 *   <li>{@link de.cuioss.sheriff.oauth.library.domain.context.ValidationContext} - 
 *       Carries cached current time and configuration through the validation pipeline</li>
 * </ul>
 *
 * @since 1.0
 */
package de.cuioss.sheriff.oauth.library.domain.context;