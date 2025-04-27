/*
 * Copyright 2023 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.cuioss.jwt.validation.flow;

import de.cuioss.jwt.validation.JWTValidationLogMessages;
import de.cuioss.jwt.validation.jwks.JwksLoader;
import de.cuioss.jwt.validation.security.BouncyCastleProviderSingleton;
import de.cuioss.jwt.validation.security.SecurityEventCounter;
import de.cuioss.tools.logging.CuiLogger;
import jakarta.annotation.Nonnull;
import lombok.Getter;
import lombok.NonNull;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

/**
 * Validator for JWT validation signatures.
 * <p>
 * This class validates the signature of a JWT validation using a public key
 * retrieved from a configured JwksLoader.
 * <p>
 * It assumes that header validation (algorithm, issuer) has already been
 * performed by {@link TokenHeaderValidator}.
 * <p>
 * This class uses Bouncy Castle (bcprov-jdk18on) for cryptographic operations, specifically:
 * <ul>
 *   <li>{@link org.bouncycastle.jce.provider.BouncyCastleProvider} - As the security provider for signature verification</li>
 * </ul>
 * <p>
 * Bouncy Castle is registered as a security provider in the static initializer block to ensure
 * it's available for all signature verification operations. The class uses Bouncy Castle to support
 * a wide range of signature algorithms:
 * <ul>
 *   <li>RSA signatures: RS256, RS384, RS512</li>
 *   <li>ECDSA signatures: ES256, ES384, ES512</li>
 *   <li>RSA-PSS signatures: PS256, PS384, PS512</li>
 * </ul>
 * <p>
 * Using Bouncy Castle ensures consistent cryptographic operations across different JVM implementations
 * and provides support for modern cryptographic algorithms that may not be available in all JVM versions.
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
     * Validates the signature of a decoded JWT validation.
     *
     * @param decodedJwt the decoded JWT validation to validate
     * @return true if the signature is valid, false otherwise
     */
    public boolean validateSignature(@Nonnull DecodedJwt decodedJwt) {
        LOGGER.debug("Validating validation signature");

        // Get the kid from the validation header
        var kid = decodedJwt.getKid();
        if (kid.isEmpty()) {
            LOGGER.warn(JWTValidationLogMessages.WARN.MISSING_CLAIM.format("kid"));
            securityEventCounter.increment(SecurityEventCounter.EventType.MISSING_CLAIM);
            return false;
        }

        // Get the algorithm from the validation header
        var algorithm = decodedJwt.getAlg();
        if (algorithm.isEmpty()) {
            LOGGER.warn(JWTValidationLogMessages.WARN.MISSING_CLAIM.format("alg"));
            securityEventCounter.increment(SecurityEventCounter.EventType.MISSING_CLAIM);
            return false;
        }

        // Get the signature from the validation
        var signature = decodedJwt.getSignature();
        if (signature.isEmpty()) {
            LOGGER.warn(JWTValidationLogMessages.WARN.MISSING_CLAIM.format("signature"));
            securityEventCounter.increment(SecurityEventCounter.EventType.MISSING_CLAIM);
            return false;
        }

        // Get the key from the JwksLoader
        var keyInfo = jwksLoader.getKeyInfo(kid.get());
        if (keyInfo.isEmpty()) {
            LOGGER.warn(JWTValidationLogMessages.WARN.KEY_NOT_FOUND.format(kid.get()));
            securityEventCounter.increment(SecurityEventCounter.EventType.KEY_NOT_FOUND);
            return false;
        }

        // Verify that the key's algorithm matches the validation's algorithm
        if (!isAlgorithmCompatible(algorithm.get(), keyInfo.get().getAlgorithm())) {
            LOGGER.warn(JWTValidationLogMessages.WARN.UNSUPPORTED_ALGORITHM.format(algorithm.get()));
            securityEventCounter.increment(SecurityEventCounter.EventType.UNSUPPORTED_ALGORITHM);
            return false;
        }

        // Verify the signature
        try {
            LOGGER.debug("All checks passed, verifying signature");
            return verifySignature(decodedJwt, keyInfo.get().getKey(), algorithm.get());
        } catch (Exception e) {
            LOGGER.warn(JWTValidationLogMessages.ERROR.SIGNATURE_VALIDATION_FAILED.format(e.getMessage()), e);
            securityEventCounter.increment(SecurityEventCounter.EventType.SIGNATURE_VALIDATION_FAILED);
            return false;
        }
    }

    /**
     * Verifies the signature of a JWT validation using the provided public key and algorithm.
     *
     * @param decodedJwt the decoded JWT validation
     * @param publicKey  the public key to use for verification
     * @param algorithm  the algorithm to use for verification
     * @return true if the signature is valid, false otherwise
     */
    private boolean verifySignature(DecodedJwt decodedJwt, PublicKey publicKey, String algorithm) {
        LOGGER.trace("Verifying signature:\nDecodedJwt: %s\nPublicKey: %s\nAlgorithm: %s", decodedJwt, publicKey, algorithm);
        // Get the parts of the validation
        String[] parts = decodedJwt.getParts();
        if (parts.length != 3) {
            LOGGER.warn(JWTValidationLogMessages.WARN.INVALID_JWT_FORMAT.format(parts.length));
            securityEventCounter.increment(SecurityEventCounter.EventType.INVALID_JWT_FORMAT);
            return false;
        }

        // Get the data to verify (header.payload)
        String dataToVerify = parts[0] + "." + parts[1];
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
            }
            return isValid;
        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidKeyException | SignatureException e) {
            LOGGER.warn(e, JWTValidationLogMessages.ERROR.SIGNATURE_VALIDATION_FAILED.format(e.getMessage()));
            securityEventCounter.increment(SecurityEventCounter.EventType.SIGNATURE_VALIDATION_FAILED);
            return false;
        }


    }

    /**
     * Gets a Signature verifier for the specified algorithm.
     *
     * @param algorithm the algorithm to use
     * @return a Signature verifier
     * @throws IllegalArgumentException if the algorithm is not supported
     */
    private Signature getSignatureVerifier(String algorithm) throws NoSuchAlgorithmException, NoSuchProviderException {
        String jcaAlgorithm = switch (algorithm) {
            case "RS256" -> "SHA256withRSA";
            case "RS384" -> "SHA384withRSA";
            case "RS512" -> "SHA512withRSA";
            case "ES256" -> "SHA256withECDSA";
            case "ES384" -> "SHA384withECDSA";
            case "ES512" -> "SHA512withECDSA";
            case "PS256" -> "SHA256withRSAandMGF1";
            case "PS384" -> "SHA384withRSAandMGF1";
            case "PS512" -> "SHA512withRSAandMGF1";
            default -> throw new IllegalArgumentException("Unsupported algorithm: " + algorithm);
        };
        return Signature.getInstance(jcaAlgorithm, BouncyCastleProviderSingleton.getInstance().getProviderName());
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
        if (keyAlgorithm.equals("RSA")) {
            return tokenAlgorithm.startsWith("RS") || tokenAlgorithm.startsWith("PS");
        }
        // For EC keys
        if (keyAlgorithm.equals("EC")) {
            return tokenAlgorithm.startsWith("ES");
        }
        // For exact matches
        return tokenAlgorithm.equals(keyAlgorithm);
    }
}
