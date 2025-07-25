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

import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.jwt.validation.exception.TokenValidationException;
import de.cuioss.jwt.validation.jwks.JwksLoader;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.jwt.validation.util.EcdsaSignatureFormatConverter;
import de.cuioss.tools.logging.CuiLogger;
import lombok.Getter;
import lombok.NonNull;

import java.nio.charset.StandardCharsets;
import java.security.*;

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

    @NonNull
    private final SignatureTemplateManager signatureTemplateManager;

    /**
     * Constructs a TokenSignatureValidator with the specified JwksLoader and SecurityEventCounter.
     *
     * @param jwksLoader           the JWKS loader to use for key retrieval
     * @param securityEventCounter the counter for security events
     */
    public TokenSignatureValidator(@NonNull JwksLoader jwksLoader, @NonNull SecurityEventCounter securityEventCounter) {
        this.jwksLoader = jwksLoader;
        this.securityEventCounter = securityEventCounter;
        this.signatureTemplateManager = new SignatureTemplateManager();
    }

    /**
     * Validates the signature of a decoded JWT Token.
     * <p>
     * <strong>Preconditions:</strong> This validator assumes that the following have already been validated:
     * <ul>
     *   <li>Token format (3 parts: header.payload.signature)</li>
     *   <li>Algorithm (alg) claim presence and support  </li>
     *   <li>Header structure and basic JWT format</li>
     * </ul>
     * <p>
     * This validator is responsible for:
     * <ul>
     *   <li>Key ID (kid) claim presence validation</li>
     *   <li>Key retrieval from JWKS</li>
     *   <li>Algorithm compatibility between token and key</li>
     *   <li>Cryptographic signature verification</li>
     * </ul>
     *
     * @param decodedJwt the decoded JWT Token to validate
     * @throws TokenValidationException if the signature is invalid
     */
    @SuppressWarnings("java:S3655") // owolff: False Positive: isPresent is checked before calling get()
    public void validateSignature(@NonNull DecodedJwt decodedJwt) {
        LOGGER.debug("Validating validation signature");

        // Get the kid from the validation header - precondition: already validated by TokenHeaderValidator
        var kid = decodedJwt.getKid().orElseThrow(() ->
                new IllegalStateException("Key ID (kid) should have been validated by TokenHeaderValidator"));

        // Get the algorithm from the validation header - precondition: already validated by TokenHeaderValidator
        var algorithm = decodedJwt.getAlg().orElseThrow(() ->
                new IllegalStateException("Algorithm (alg) should have been validated by TokenHeaderValidator"));

        // Signature validation is performed in verifySignature method

        // Get the key from the JwksLoader
        var keyInfo = jwksLoader.getKeyInfo(kid);
        if (keyInfo.isEmpty()) {
            LOGGER.warn(JWTValidationLogMessages.WARN.KEY_NOT_FOUND.format(kid));
            securityEventCounter.increment(SecurityEventCounter.EventType.KEY_NOT_FOUND);
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.KEY_NOT_FOUND,
                    "Key not found for key ID: %s. Please verify the key exists in the JWKS endpoint or configuration.".formatted(kid)
            );
        }

        // Verify that the key's algorithm matches the validation's algorithm
        if (!isAlgorithmCompatible(algorithm, keyInfo.get().algorithm())) {
            LOGGER.warn(JWTValidationLogMessages.WARN.UNSUPPORTED_ALGORITHM.format(algorithm));
            securityEventCounter.increment(SecurityEventCounter.EventType.UNSUPPORTED_ALGORITHM);
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.UNSUPPORTED_ALGORITHM,
                    "Algorithm not compatible with key: %s is not compatible with %s".formatted(algorithm, keyInfo.get().algorithm())
            );
        }

        // Verify the signature
        try {
            LOGGER.debug("All checks passed, verifying signature");
            verifySignature(decodedJwt, keyInfo.get().key(), algorithm);
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

        // Get the data to verify and signature bytes from DecodedJwt
        String dataToVerify;
        byte[] signatureBytes;
        try {
            dataToVerify = decodedJwt.getDataToVerify();
            signatureBytes = decodedJwt.getSignatureAsDecodedBytes();
        } catch (IllegalStateException e) {
            LOGGER.warn(JWTValidationLogMessages.ERROR.SIGNATURE_VALIDATION_FAILED.format(e.getMessage()), e);
            securityEventCounter.increment(SecurityEventCounter.EventType.SIGNATURE_VALIDATION_FAILED);
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.SIGNATURE_VALIDATION_FAILED,
                    "Failed to extract JWT data for signature verification: %s".formatted(e.getMessage()),
                    e
            );
        }

        byte[] dataBytes = dataToVerify.getBytes(StandardCharsets.UTF_8);

        // Initialize the signature verifier with the appropriate algorithm
        try {
            Signature verifier = signatureTemplateManager.getSignatureInstance(algorithm);
            verifier.initVerify(publicKey);
            verifier.update(dataBytes);

            // Convert ECDSA signatures from IEEE P1363 to ASN.1/DER format if needed
            byte[] verificationSignature = signatureBytes;
            if (isEcdsaAlgorithm(algorithm)) {
                LOGGER.debug("Converting ECDSA signature from IEEE P1363 to ASN.1/DER format for algorithm: %s", algorithm);
                verificationSignature = EcdsaSignatureFormatConverter.toJCACompatibleSignature(signatureBytes, algorithm);
            }

            // Verify the signature
            boolean isValid = verifier.verify(verificationSignature);
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
        } catch (InvalidKeyException | SignatureException e) {
            LOGGER.warn(e, JWTValidationLogMessages.ERROR.SIGNATURE_VALIDATION_FAILED.format(e.getMessage()));
            securityEventCounter.increment(SecurityEventCounter.EventType.SIGNATURE_VALIDATION_FAILED);
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.SIGNATURE_VALIDATION_FAILED,
                    "Signature validation failed: %s".formatted(e.getMessage()),
                    e
            );
        } catch (SignatureTemplateManager.UnsupportedAlgorithmException e) {
            LOGGER.warn(e, JWTValidationLogMessages.ERROR.SIGNATURE_VALIDATION_FAILED.format(e.getMessage()));
            securityEventCounter.increment(SecurityEventCounter.EventType.UNSUPPORTED_ALGORITHM);
            throw new TokenValidationException(
                    SecurityEventCounter.EventType.UNSUPPORTED_ALGORITHM,
                    "Algorithm not supported: %s".formatted(e.getMessage()),
                    e
            );
        }
    }

    /**
     * Checks if the algorithm is an ECDSA algorithm that requires signature format conversion.
     *
     * @param algorithm the signature algorithm to check
     * @return true if the algorithm is ECDSA (ES256, ES384, ES512), false otherwise
     */
    private boolean isEcdsaAlgorithm(String algorithm) {
        return "ES256".equals(algorithm) || "ES384".equals(algorithm) || "ES512".equals(algorithm);
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
