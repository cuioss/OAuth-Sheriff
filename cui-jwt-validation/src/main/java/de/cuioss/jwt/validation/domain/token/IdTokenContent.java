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
package de.cuioss.jwt.validation.domain.token;

import de.cuioss.jwt.validation.TokenType;
import de.cuioss.jwt.validation.domain.claim.ClaimName;
import de.cuioss.jwt.validation.domain.claim.ClaimValue;
import de.cuioss.jwt.validation.json.MapRepresentation;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Represents the content of an OpenID Connect ID Token.
 * <p>
 * This class provides access to ID Token specific claims and functionality, focusing on
 * user identity information as defined in the OpenID Connect Core specification.
 * <p>
 * ID Tokens typically contain:
 * <ul>
 *   <li>Standard JWT claims (iss, sub, exp, iat)</li>
 *   <li>Authentication information (auth_time, nonce, acr)</li>
 *   <li>User identity information (name, email, etc.)</li>
 *   <li>Audience (aud) and authorized party (azp) claims</li>
 * </ul>
 * <p>
 * The ID Token is used for authentication purposes and contains claims about the authentication
 * event and the authenticated user. It is not intended for authorization purposes - that's
 * what the access token is for.
 * <p>
 * This implementation follows the standards defined in:
 * <ul>
 *   <li><a href="https://openid.net/specs/openid-connect-core-1_0.html">OpenID Connect Core 1.0</a></li>
 *   <li><a href="https://tools.ietf.org/html/rfc7519">RFC 7519 - JWT</a></li>
 * </ul>
 * <p>
 * For more details on token structure and usage, see the
 * <a href="https://github.com/cuioss/cui-jwt/tree/main/doc/specification/technical-components.adoc#token-types">Token Types</a>
 * specification.
 *
 * @author Oliver Wolff
 * @since 1.0
 */
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class IdTokenContent extends BaseTokenContent {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new IdTokenContent with the given claims and raw token.
     *
     * @param claims     the token claims
     * @param rawToken   the raw token string
     * @param rawPayload the raw JSON payload for ClaimMapper processing
     */
    public IdTokenContent(Map<String, ClaimValue> claims, String rawToken,
            MapRepresentation rawPayload) {
        super(claims, rawToken, TokenType.ID_TOKEN, rawPayload);
    }

    /**
     * Gets the audience claim value.
     * <p>
     * 'aud' is mandatory for {@link TokenType#ID_TOKEN}.
     *
     * @return the audience as a list of strings, or throws exception if it's not present
     * @throws IllegalStateException if the audience claim is not present
     */
    public List<String> getAudience() {
        return getClaimOption(ClaimName.AUDIENCE)
                .map(ClaimValue::getAsList)
                .orElseThrow(() -> new IllegalStateException("Audience claim not presentin token"));
    }

    /**
     * Gets the name from the token claims.
     *
     * @return an Optional containing the name if present, or empty otherwise
     */
    public Optional<String> getName() {
        return getClaimOption(ClaimName.NAME)
                .map(ClaimValue::getOriginalString);
    }

    /**
     * Gets the email from the token claims.
     *
     * @return an Optional containing the email if present, or empty otherwise
     */
    public Optional<String> getEmail() {
        return getClaimOption(ClaimName.EMAIL)
                .map(ClaimValue::getOriginalString);
    }

}
