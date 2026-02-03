package com.aiinpocket.n3n.gateway.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.KeyAgreement;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * Cryptographic operations for Agent communication.
 * Implements X25519 key exchange and AES-256-GCM encryption.
 */
@Component
@Slf4j
public class AgentCrypto {

    private static final String KEY_AGREEMENT_ALGORITHM = "X25519";
    private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128; // bits
    private static final int GCM_NONCE_LENGTH = 12; // bytes
    private static final int AES_KEY_LENGTH = 32; // bytes (256 bits)

    private final SecureRandom secureRandom = new SecureRandom();

    /**
     * Generate a new X25519 key pair for key exchange.
     */
    public KeyPair generateKeyPair() throws GeneralSecurityException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(KEY_AGREEMENT_ALGORITHM);
        return keyGen.generateKeyPair();
    }

    /**
     * Perform X25519 key agreement to derive shared secret.
     */
    public byte[] deriveSharedSecret(PrivateKey privateKey, PublicKey peerPublicKey)
            throws GeneralSecurityException {
        KeyAgreement keyAgreement = KeyAgreement.getInstance(KEY_AGREEMENT_ALGORITHM);
        keyAgreement.init(privateKey);
        keyAgreement.doPhase(peerPublicKey, true);
        return keyAgreement.generateSecret();
    }

    /**
     * Derive encryption keys from shared secret using HKDF.
     */
    public DerivedKeys deriveKeys(byte[] sharedSecret, byte[] salt, byte[] info)
            throws GeneralSecurityException {
        // HKDF-Extract
        byte[] prk = hkdfExtract(salt, sharedSecret);

        // HKDF-Expand for different keys
        byte[] encryptKeyC2S = hkdfExpand(prk, concat(info, "encrypt-c2s".getBytes()), AES_KEY_LENGTH);
        byte[] encryptKeyS2C = hkdfExpand(prk, concat(info, "encrypt-s2c".getBytes()), AES_KEY_LENGTH);
        byte[] authKey = hkdfExpand(prk, concat(info, "auth".getBytes()), AES_KEY_LENGTH);

        return new DerivedKeys(encryptKeyC2S, encryptKeyS2C, authKey);
    }

    /**
     * Encrypt a message using AES-256-GCM.
     */
    public EncryptedMessage encrypt(byte[] plaintext, byte[] key, byte[] aad)
            throws GeneralSecurityException {
        // Generate random nonce
        byte[] nonce = new byte[GCM_NONCE_LENGTH];
        secureRandom.nextBytes(nonce);

        // Encrypt
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce);
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);

        if (aad != null && aad.length > 0) {
            cipher.updateAAD(aad);
        }

        byte[] ciphertextWithTag = cipher.doFinal(plaintext);

        // Split ciphertext and tag (GCM appends tag to ciphertext)
        int ciphertextLength = ciphertextWithTag.length - (GCM_TAG_LENGTH / 8);
        byte[] ciphertext = new byte[ciphertextLength];
        byte[] tag = new byte[GCM_TAG_LENGTH / 8];
        System.arraycopy(ciphertextWithTag, 0, ciphertext, 0, ciphertextLength);
        System.arraycopy(ciphertextWithTag, ciphertextLength, tag, 0, tag.length);

        return new EncryptedMessage(nonce, ciphertext, tag);
    }

    /**
     * Decrypt a message using AES-256-GCM.
     */
    public byte[] decrypt(byte[] ciphertext, byte[] tag, byte[] key, byte[] nonce, byte[] aad)
            throws GeneralSecurityException {
        // Combine ciphertext and tag for GCM
        byte[] ciphertextWithTag = new byte[ciphertext.length + tag.length];
        System.arraycopy(ciphertext, 0, ciphertextWithTag, 0, ciphertext.length);
        System.arraycopy(tag, 0, ciphertextWithTag, ciphertext.length, tag.length);

        // Decrypt
        Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
        GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, nonce);
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");
        cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);

        if (aad != null && aad.length > 0) {
            cipher.updateAAD(aad);
        }

        return cipher.doFinal(ciphertextWithTag);
    }

    /**
     * Parse a public key from Base64-encoded bytes.
     * Supports both raw X25519 keys (32 bytes) and X509-encoded keys.
     */
    public PublicKey parsePublicKey(String base64Key) throws GeneralSecurityException {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);

        // X25519 raw public key is exactly 32 bytes
        // If we receive raw key, wrap it in X509 format
        if (keyBytes.length == 32) {
            // X509 header for X25519: OID 1.3.101.110
            // Sequence { Sequence { OID }, BitString { key } }
            byte[] x509Header = new byte[] {
                0x30, 0x2A,  // SEQUENCE, length 42
                0x30, 0x05,  // SEQUENCE, length 5
                0x06, 0x03, 0x2B, 0x65, 0x6E,  // OID 1.3.101.110 (X25519)
                0x03, 0x21, 0x00  // BIT STRING, length 33, no unused bits
            };
            byte[] x509Encoded = new byte[x509Header.length + keyBytes.length];
            System.arraycopy(x509Header, 0, x509Encoded, 0, x509Header.length);
            System.arraycopy(keyBytes, 0, x509Encoded, x509Header.length, keyBytes.length);
            keyBytes = x509Encoded;
            log.debug("Converted raw X25519 key to X509 format");
        }

        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_AGREEMENT_ALGORITHM);
        return keyFactory.generatePublic(keySpec);
    }

    /**
     * Encode a public key to Base64.
     * Outputs raw 32-byte X25519 public key for cross-platform compatibility.
     */
    public String encodePublicKey(PublicKey publicKey) {
        byte[] encoded = publicKey.getEncoded();

        // X509 encoded X25519 key is 44 bytes (12 byte header + 32 byte key)
        // Extract raw 32 byte key for compatibility with Swift/CryptoKit
        if (encoded.length == 44) {
            byte[] rawKey = new byte[32];
            System.arraycopy(encoded, 12, rawKey, 0, 32);
            log.debug("Extracted raw X25519 key from X509 format");
            return Base64.getEncoder().encodeToString(rawKey);
        }

        return Base64.getEncoder().encodeToString(encoded);
    }

    /**
     * Generate a random 6-digit pairing code.
     */
    public String generatePairingCode() {
        int code = secureRandom.nextInt(1000000);
        return String.format("%06d", code);
    }

    /**
     * Generate a random secret for pairing.
     */
    public byte[] generatePairingSecret() {
        byte[] secret = new byte[32];
        secureRandom.nextBytes(secret);
        return secret;
    }

    /**
     * Compute SHA-256 fingerprint.
     */
    public String computeFingerprint(byte[] data) throws GeneralSecurityException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        return Base64.getEncoder().encodeToString(hash);
    }

    /**
     * Generate a random sequence number starting point.
     */
    public long generateInitialSequence() {
        return Math.abs(secureRandom.nextLong() % 1000000);
    }

    // HKDF implementation

    private byte[] hkdfExtract(byte[] salt, byte[] ikm) throws GeneralSecurityException {
        if (salt == null || salt.length == 0) {
            salt = new byte[32]; // Zero salt
        }
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(salt, "HmacSHA256"));
        return mac.doFinal(ikm);
    }

    private byte[] hkdfExpand(byte[] prk, byte[] info, int length) throws GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));

        ByteBuffer output = ByteBuffer.allocate(length);
        byte[] t = new byte[0];
        byte counter = 1;

        while (output.position() < length) {
            mac.update(t);
            mac.update(info);
            mac.update(counter++);
            t = mac.doFinal();

            int copyLength = Math.min(t.length, length - output.position());
            output.put(t, 0, copyLength);
        }

        return output.array();
    }

    private byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    // Data classes

    public record DerivedKeys(
        byte[] encryptKeyClientToServer,
        byte[] encryptKeyServerToClient,
        byte[] authKey
    ) {}

    public record EncryptedMessage(
        byte[] nonce,
        byte[] ciphertext,
        byte[] tag
    ) {
        public String toCompact() {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(nonce) + "." +
                   Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext) + "." +
                   Base64.getUrlEncoder().withoutPadding().encodeToString(tag);
        }

        public static EncryptedMessage fromCompact(String compact) {
            String[] parts = compact.split("\\.");
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid encrypted message format");
            }
            return new EncryptedMessage(
                Base64.getUrlDecoder().decode(parts[0]),
                Base64.getUrlDecoder().decode(parts[1]),
                Base64.getUrlDecoder().decode(parts[2])
            );
        }
    }
}
