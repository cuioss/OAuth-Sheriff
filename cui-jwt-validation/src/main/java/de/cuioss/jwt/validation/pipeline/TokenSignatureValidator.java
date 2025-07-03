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
package de.cuioss.jwt.validation.pipeline;

import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.jwks.JwksLoader;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.tools.logging.CuiLogger;
import jakarta.annotation.Nonnull;
import lombok.Getter;
import lombok.NonNull;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.MGF1ParameterSpec;
import java.security.spec.PSSParameterSpec;
import java.util.Base64;

/**
 * Validator for JWT Token signatures.
 * <p>
 * This class validates the signature of a JWT Token using a public key
 * retrieved from a configured JwksLoader.
 * <p>
 * It assumes that header validation (algorithm, issuer) has already been
 * performed by {@link TokenHeaderValidator}.
 * <p>
 * This class uses standard JDK cryptographic providers for signature verification, supporting
 * all standard JOSE algorithms as defined in RFC 7518:
 * <ul>
 *   <li>RSA signatures: RS256, RS384, RS512</li>
 *   <li>ECDSA signatures: ES256, ES384, ES512</li>
 *   <li>RSA-PSS signatures: PS256, PS384, PS512</li>
 * </ul>
 * <p>
 * All algorithms are supported by the standard JDK cryptographic providers in Java 11+,
 * ensuring excellent compatibility with GraalVM native image compilation and optimal performance.
 * <p>
 * For more details on signature validation, see the
 * <a href="https://github.com/cuioss/cui-jwt/tree/main/doc/specification/technical-components.adoc#token-validation-pipeline">Token Validation Pipeline</a>
 * and <a href="https://github.com/cuioss/cui-jwt/tree/main/doc/specification/security.adoc">Security Specification</a>
 *
 * @author Oliver Wolff
 * @since 1.0
 */
public class TokenSignatureValidator {

    private static final CuiLogger LOGGER = new CuiLogger(TokenSignatureValidator.class);

    @Getter
    @NonNull
    private final JwksLoader jwksLoader;

    @NonNull
    private final SecurityEventCounter securityEventCounter;

    /**
     * Constructs a TokenSignatureValidator with the specified JwksLoader and SecurityEventCounter.
     *
     * @param jwksLoader           the JWKS loader to use for key retrieval
     * @param securityEventCounter the counter for security events
     */
    public TokenSignatureValidator(@NonNull JwksLoader jwksLoader, @NonNull SecurityEventCounter securityEventCounter) {
        this.jwksLoader = jwksLoader;
        this.securityEventCounter = securityEventCounter;
    }

    /**
     * Validates the signature of a decoded JWT Token.
     *
     * @param decodedJwt the decoded JWT Token to validate
     * @throws TokenValidationException if the signature is invalid
     */
    @SuppressWarnings("java:S3655") // owolff: False Positive: isPresent is checked before calling get()
    public void validateSignature(@Nonnull DecodedJwt decodedJwt) {
        LOGGER.debug("Validating validation signature");

        // Get the kid from the validation header
        var kid = decodedJwt.getKid();
        if (kid.isEmpty()) {
            LOGGER.warn(JWTValidationLogMessages.WARN.MISSING_CLAIM.format("kid"));
            securityEventCounter.increment(SecurityEventCounter.EventType.MISSING_CLAIM);
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.MISSING_CLAIM,
                    "Missing required key ID (kid) claim in token header. Available header claims: " + (decodedJwt.getHeader().isPresent() ? decodedJwt.getHeader().get().keySet() : "none")
            );
        }

        // Get the algorithm from the validation header
        var algorithm = decodedJwt.getAlg();
        if (algorithm.isEmpty()) {
            LOGGER.warn(JWTValidationLogMessages.WARN.MISSING_CLAIM.format("alg"));
            securityEventCounter.increment(SecurityEventCounter.EventType.MISSING_CLAIM);
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.MISSING_CLAIM,
                    "Missing required algorithm (alg) claim in token header. Available header claims: " + (decodedJwt.getHeader().isPresent() ? decodedJwt.getHeader().get().keySet() : "none")
            );
        }

        // Get the signature from the validation
        var signature = decodedJwt.getSignature();
        if (signature.isEmpty()) {
            LOGGER.warn(JWTValidationLogMessages.WARN.MISSING_CLAIM.format("signature"));
            securityEventCounter.increment(SecurityEventCounter.EventType.MISSING_CLAIM);
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.MISSING_CLAIM,
                    "Missing required signature in token. Token parts: " + (decodedJwt.parts() != null ? decodedJwt.parts().length : 0)
            );
        }

        // Get the key from the JwksLoader
        var keyInfo = jwksLoader.getKeyInfo(kid.get());
        if (keyInfo.isEmpty()) {
            LOGGER.warn(JWTValidationLogMessages.WARN.KEY_NOT_FOUND.format(kid.get()));
            securityEventCounter.increment(SecurityEventCounter.EventType.KEY_NOT_FOUND);
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.KEY_NOT_FOUND,
                    "Key not found for key ID: %s. Please verify the key exists in the JWKS endpoint or configuration.".formatted(kid.get())
            );
        }

        // Verify that the key's algorithm matches the validation's algorithm
        if (!isAlgorithmCompatible(algorithm.get(), keyInfo.get().algorithm())) {
            LOGGER.warn(JWTValidationLogMessages.WARN.UNSUPPORTED_ALGORITHM.format(algorithm.get()));
            securityEventCounter.increment(SecurityEventCounter.EventType.UNSUPPORTED_ALGORITHM);
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.UNSUPPORTED_ALGORITHM,
                    "Algorithm not compatible with key: %s is not compatible with %s".formatted(algorithm.get(), keyInfo.get().algorithm())
            );
        }

        // Verify the signature
        try {
            LOGGER.debug("All checks passed, verifying signature");
            verifySignature(decodedJwt, keyInfo.get().key(), algorithm.get());
        } catch (IllegalArgumentException e) {
            LOGGER.warn(JWTValidationLogMessages.ERROR.SIGNATURE_VALIDATION_FAILED.format(e.getMessage()), e);
            securityEventCounter.increment(SecurityEventCounter.EventType.SIGNATURE_VALIDATION_FAILED);
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.SIGNATURE_VALIDATION_FAILED,
                    "Signature validation failed: %s".formatted(e.getMessage()),
                    e
            );
        }
    }

    /**
     * Verifies the signature of a JWT Token using the provided public key and algorithm.
     *
     * @param decodedJwt the decoded JWT Token
     * @param publicKey  the public key to use for verification
     * @param algorithm  the algorithm to use for verification
     * @throws TokenValidationException if the signature is invalid
     */
    private void verifySignature(DecodedJwt decodedJwt, PublicKey publicKey, String algorithm) {
        LOGGER.trace("Verifying signature:\nDecodedJwt: %s\nPublicKey: %s\nAlgorithm: %s", decodedJwt, publicKey, algorithm);
        // Get the parts of the validation
        String[] parts = decodedJwt.parts();
        if (parts.length != 3) {
            LOGGER.warn(JWTValidationLogMessages.WARN.INVALID_JWT_FORMAT.format(parts.length));
            securityEventCounter.increment(SecurityEventCounter.EventType.INVALID_JWT_FORMAT);
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.INVALID_JWT_FORMAT,
                    "Invalid JWT format: expected 3 parts but found %s".formatted(parts.length)
            );
        }

        // Get the data to verify (header.payload)
        String dataToVerify = "%s.%s".formatted(parts[0], parts[1]);
        byte[] dataBytes = dataToVerify.getBytes(StandardCharsets.UTF_8);

        // Get the signature bytes
        byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[2]);

        // Initialize the signature verifier with the appropriate algorithm
        try {
            Signature verifier = getSignatureVerifier(algorithm);
            verifier.initVerify(publicKey);
            verifier.update(dataBytes);
            // Verify the signature
            boolean isValid = verifier.verify(signatureBytes);
            if (isValid) {
                LOGGER.debug("Signature is valid");
            } else {
                LOGGER.warn(JWTValidationLogMessages.ERROR.SIGNATURE_VALIDATION_FAILED.format("Invalid signature"));
                securityEventCounter.increment(SecurityEventCounter.EventType.SIGNATURE_VALIDATION_FAILED);
                throw new TokenValidationException(
                        SecurityEventCounter.EventType.SIGNATURE_VALIDATION_FAILED,
                        "Invalid signature"
                );
            }
        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidKeyException | SignatureException | InvalidAlgorithmParameterException e) {
            LOGGER.warn(e, JWTValidationLogMessages.ERROR.SIGNATURE_VALIDATION_FAILED.format(e.getMessage()));
            securityEventCounter.increment(SecurityEventCounter.EventType.SIGNATURE_VALIDATION_FAILED);
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.SIGNATURE_VALIDATION_FAILED,
                    "Signature validation failed: %s".formatted(e.getMessage()),
                    e
            );
        }
    }

    /**
     * Gets a Signature verifier for the specified algorithm.
     *
     * @param algorithm the algorithm to use
     * @return a Signature verifier
     * @throws IllegalArgumentException if the algorithm is not supported
     */
    private Signature getSignatureVerifier(String algorithm) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidAlgorithmParameterException {
        Signature signature;

        switch (algorithm) {
            case "RS256" -> signature = Signature.getInstance("SHA256withRSA");
            case "RS384" -> signature = Signature.getInstance("SHA384withRSA");
            case "RS512" -> signature = Signature.getInstance("SHA512withRSA");
            case "ES256" -> signature = Signature.getInstance("SHA256withECDSA");
            case "ES384" -> signature = Signature.getInstance("SHA384withECDSA");
            case "ES512" -> signature = Signature.getInstance("SHA512withECDSA");
            case "PS256" -> {
                signature = Signature.getInstance("RSASSA-PSS");
                signature.setParameter(new PSSParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, 32, 1));
            }
            case "PS384" -> {
                signature = Signature.getInstance("RSASSA-PSS");
                signature.setParameter(new PSSParameterSpec("SHA-384", "MGF1", MGF1ParameterSpec.SHA384, 48, 1));
            }
            case "PS512" -> {
                signature = Signature.getInstance("RSASSA-PSS");
                signature.setParameter(new PSSParameterSpec("SHA-512", "MGF1", MGF1ParameterSpec.SHA512, 64, 1));
            }
            default -> throw new IllegalArgumentException("Unsupported algorithm: %s".formatted(algorithm));
        }

        return signature;
    }

    /**
     * Checks if the validation algorithm is compatible with the key algorithm.
     *
     * @param tokenAlgorithm the algorithm from the validation header
     * @param keyAlgorithm   the algorithm from the key
     * @return true if the algorithms are compatible, false otherwise
     */
    private boolean isAlgorithmCompatible(String tokenAlgorithm, String keyAlgorithm) {
        // For RSA keys
        if ("RSA".equals(keyAlgorithm)) {
            return tokenAlgorithm.startsWith("RS") || tokenAlgorithm.startsWith("PS");
        }
        // For EC keys
        if ("EC".equals(keyAlgorithm)) {
            return tokenAlgorithm.startsWith("ES");
        }
        // For exact matches
        return tokenAlgorithm.equals(keyAlgorithm);
    }
}
