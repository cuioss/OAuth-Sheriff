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
package de.cuioss.jwt.validation.pipeline;

import de.cuioss.jwt.validation.domain.claim.ClaimName;
import de.cuioss.jwt.validation.json.JwtHeader;
import de.cuioss.jwt.validation.json.MapRepresentation;
import lombok.NonNull;

import java.util.Arrays;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

/**
 * Record representing a decoded JWT token.
 * <p>
 * This record holds the parsed components of a JWT token after Base64 decoding and DSL-JSON parsing,
 * but before any validation occurs. It contains:
 * <ul>
 *   <li>The decoded header as a JwtHeader record</li>
 *   <li>The decoded payload (body) as a MapRepresentation</li>
 *   <li>The signature part as a String</li>
 *   <li>Convenience methods for accessing common JWT fields</li>
 *   <li>The original token parts and raw token string</li>
 * </ul>
 * <p>
 * <strong>Security Note:</strong> This record is not guaranteed to contain a validated token.
 * It is usually created by {@link NonValidatingJwtParser} and should be passed to
 * {@link TokenHeaderValidator}, {@link TokenSignatureValidator}, and {@link TokenClaimValidator}
 * for proper validation.
 * <p>
 * The record provides immutability guarantees and value-based equality by default, making it
 * ideal for representing decoded JWT data in the validation pipeline.
 * <p>
 * For more details on the token validation process, see the
 * <a href="https://github.com/cuioss/cui-jwt/tree/main/doc/specification/technical-components.adoc#token-validation-pipeline">Token Validation Pipeline</a>
 *
 * @param header the decoded header as a JwtHeader
 * @param body the decoded payload (body) as a MapRepresentation
 * @param signature the signature part as a String
 * @param parts the original token parts (header.payload.signature)
 * @param rawToken the original raw token string
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public record DecodedJwt(
JwtHeader header,
MapRepresentation body,
String signature,
String[] parts,
String rawToken
) {
    /**
     * Gets the header of the JWT token.
     *
     * @return the JwtHeader, never null (minimal header with empty algorithm if not present)
     */
    public JwtHeader getHeader() {
        return header != null ? header : JwtHeader.of("");
    }

    /**
     * Gets the body of the JWT token.
     *
     * @return the body MapRepresentation, never null (empty MapRepresentation if not present)
     */
    public MapRepresentation getBody() {
        return body != null ? body : MapRepresentation.empty();
    }

    /**
     * Gets the signature of the JWT token.
     *
     * @return an Optional containing the signature if present
     */
    public Optional<String> getSignature() {
        return Optional.ofNullable(signature);
    }

    /**
     * Gets the issuer of the JWT token extracted from the body.
     *
     * @return an Optional containing the issuer if present
     */
    public Optional<String> getIssuer() {
        return body != null ? body.getString(ClaimName.ISSUER.getName()) : Optional.empty();
    }

    /**
     * Gets the kid (key ID) from the JWT token header.
     *
     * @return an Optional containing the kid if present
     */
    public Optional<String> getKid() {
        return header != null ? header.kid() : Optional.empty();
    }

    /**
     * Gets the alg (algorithm) from the JWT token header.
     *
     * @return an Optional containing the algorithm if present
     */
    public Optional<String> getAlg() {
        return header != null ? Optional.ofNullable(header.alg()) : Optional.empty();
    }

    /**
     * Gets the decoded signature bytes from the JWT token.
     * <p>
     * This method decodes the Base64URL-encoded signature string to raw bytes.
     * <p>
     * <strong>Preconditions:</strong>
     * <ul>
     *   <li>The JWT must have been properly parsed with 3 parts (header.payload.signature)</li>
     *   <li>The parts array must contain exactly 3 elements</li>
     *   <li>The signature part (parts[2]) must be a valid Base64URL-encoded string</li>
     * </ul>
     *
     * @return the decoded signature bytes, never null
     * @throws IllegalStateException if the JWT format is invalid (not 3 parts) or if the signature
     *                               cannot be decoded from Base64URL format
     */
    public byte[] getSignatureAsDecodedBytes() {
        // Validate precondition: parts array must exist and have exactly 3 elements
        if (parts == null || parts.length != 3) {
            throw new IllegalStateException(
                    "JWT format is invalid: expected 3 parts (header.payload.signature) but found %s"
                            .formatted(parts == null ? "null" : parts.length)
            );
        }

        // Decode the signature from Base64URL
        try {
            return Base64.getUrlDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Failed to decode signature from Base64URL format: %s".formatted(e.getMessage()),
                    e
            );
        }
    }

    /**
     * Gets the data to verify for signature validation.
     * <p>
     * This method returns the concatenated header and payload parts (header.payload) that should
     * be used for signature verification according to the JWT specification.
     * <p>
     * <strong>Preconditions:</strong>
     * <ul>
     *   <li>The JWT must have been properly parsed with 3 parts (header.payload.signature)</li>
     *   <li>The parts array must contain exactly 3 elements</li>
     * </ul>
     *
     * @return the data to verify as a string in the format "header.payload", never null
     * @throws IllegalStateException if the JWT format is invalid (not 3 parts)
     */
    public String getDataToVerify() {
        // Validate precondition: parts array must exist and have exactly 3 elements
        if (parts == null || parts.length != 3) {
            throw new IllegalStateException(
                    "JWT format is invalid: expected 3 parts (header.payload.signature) but found %s"
                            .formatted(parts == null ? "null" : parts.length)
            );
        }

        // Return the concatenated header and payload
        return "%s.%s".formatted(parts[0], parts[1]);
    }

    /**
     * Overrides equals to properly handle array comparison for the parts field.
     * Uses Arrays.equals() for proper content-based equality comparison of the array.
     *
     * @param obj the object to compare with
     * @return true if the objects are equal, false otherwise
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        DecodedJwt that = (DecodedJwt) obj;
        return Objects.equals(header, that.header) &&
                Objects.equals(body, that.body) &&
                Objects.equals(signature, that.signature) &&
                Arrays.equals(parts, that.parts) &&
                Objects.equals(rawToken, that.rawToken);
    }

    /**
     * Overrides hashCode to properly handle array hashing for the parts field.
     * Uses Arrays.hashCode() for consistent hash generation with the equals method.
     *
     * @return the hash code of this object
     */
    @Override
    public int hashCode() {
        return Objects.hash(header, body, signature, Arrays.hashCode(parts), rawToken);
    }

    /**
     * Overrides toString to properly handle array representation for the parts field.
     * Uses Arrays.toString() for proper array content representation.
     *
     * @return a string representation of this object
     */
    @Override
    public @NonNull String toString() {
        return "DecodedJwt[" +
                "header=" + header +
                ", body=" + body +
                ", signature=" + signature +
                ", parts=" + Arrays.toString(parts) +
                ", rawToken=" + rawToken +
                ']';
    }

    /**
     * Creates a builder for constructing DecodedJwt instances.
     *
     * @return a new DecodedJwtBuilder instance
     */
    public static DecodedJwtBuilder builder() {
        return new DecodedJwtBuilder();
    }

    /**
     * Builder class for creating DecodedJwt instances.
     * Provides a fluent API for constructing DecodedJwt records.
     */
    public static class DecodedJwtBuilder {
        private JwtHeader header;
        private MapRepresentation body;
        private String signature;
        private String[] parts;
        private String rawToken;

        public DecodedJwtBuilder header(JwtHeader header) {
            this.header = header;
            return this;
        }

        public DecodedJwtBuilder body(MapRepresentation body) {
            this.body = body;
            return this;
        }

        public DecodedJwtBuilder signature(String signature) {
            this.signature = signature;
            return this;
        }

        public DecodedJwtBuilder parts(String[] parts) {
            this.parts = parts;
            return this;
        }

        public DecodedJwtBuilder rawToken(String rawToken) {
            this.rawToken = rawToken;
            return this;
        }

        public DecodedJwt build() {
            return new DecodedJwt(header, body, signature, parts, rawToken);
        }
    }
}
