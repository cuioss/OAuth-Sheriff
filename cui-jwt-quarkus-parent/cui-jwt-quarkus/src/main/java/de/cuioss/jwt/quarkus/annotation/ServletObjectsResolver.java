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
package de.cuioss.jwt.quarkus.annotation;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.inject.Qualifier;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * CDI qualifier for identifying different implementations of servlet objects resolvers.
 * 
 * <p>This qualifier allows for different implementations of servlet object resolution
 * to be injected based on the variant specified.</p>
 * 
 * <p>Usage example:</p>
 * <pre>{@code
 * @Inject
 * @ServletObjectsResolver(ServletObjectsResolver.Variant.RESTEASY)
 * HttpServletRequestResolver resolver;
 * }</pre>
 * 
 * @author Oliver Wolff
 * @since 1.0
 */
@Qualifier
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.TYPE})
@RegisterForReflection
public @interface ServletObjectsResolver {

    /**
     * The variant of servlet objects resolver to use.
     * 
     * @return the resolver variant
     */
    Variant value() default Variant.RESTEASY;

    /**
     * Enum defining the different variants of servlet objects resolvers.
     */
    enum Variant {
        /**
         * RESTEasy-based resolver that uses ResteasyProviderFactory to access servlet objects.
         * This is the default variant.
         */
        RESTEASY
    }
}