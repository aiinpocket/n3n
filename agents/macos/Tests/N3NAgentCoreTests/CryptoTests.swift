import XCTest
import CryptoKit
@testable import N3NAgentCore

final class CryptoTests: XCTestCase {

    let crypto = AgentCrypto.shared

    func testKeyPairGeneration() {
        let keyPair = crypto.generateKeyPair()

        XCTAssertNotNil(keyPair.privateKey)
        XCTAssertNotNil(keyPair.publicKey)
        XCTAssertEqual(keyPair.publicKey.rawRepresentation.count, 32)
    }

    func testKeyExchange() throws {
        // Generate key pairs for two parties
        let aliceKeyPair = crypto.generateKeyPair()
        let bobKeyPair = crypto.generateKeyPair()

        // Derive shared secrets (should be identical)
        let aliceSecret = try crypto.deriveSharedSecret(
            privateKey: aliceKeyPair.privateKey,
            peerPublicKey: bobKeyPair.publicKey
        )

        let bobSecret = try crypto.deriveSharedSecret(
            privateKey: bobKeyPair.privateKey,
            peerPublicKey: aliceKeyPair.publicKey
        )

        // Verify shared secrets match
        let aliceKeyData = aliceSecret.withUnsafeBytes { Data($0) }
        let bobKeyData = bobSecret.withUnsafeBytes { Data($0) }

        XCTAssertEqual(aliceKeyData, bobKeyData)
    }

    func testEncryptionDecryption() throws {
        let keyPair = crypto.generateKeyPair()
        let salt = "test-salt".data(using: .utf8)!
        let info = "test-info".data(using: .utf8)!

        // Create a symmetric key from the key pair (for testing)
        let sharedSecret = try crypto.deriveSharedSecret(
            privateKey: keyPair.privateKey,
            peerPublicKey: keyPair.publicKey
        )

        let keys = crypto.deriveKeys(sharedSecret: sharedSecret, salt: salt, info: info)

        // Test data
        let plaintext = "Hello, World! This is a test message.".data(using: .utf8)!
        let aad = "additional-authenticated-data".data(using: .utf8)!

        // Encrypt
        let encrypted = try crypto.encrypt(
            plaintext: plaintext,
            key: keys.encryptKeyClientToServer,
            aad: aad
        )

        XCTAssertNotEqual(encrypted.ciphertext, plaintext)
        XCTAssertEqual(encrypted.nonce.count, 12)
        XCTAssertEqual(encrypted.tag.count, 16)

        // Decrypt
        let decrypted = try crypto.decrypt(
            ciphertext: encrypted.ciphertext,
            tag: encrypted.tag,
            key: keys.encryptKeyClientToServer,
            nonce: encrypted.nonce,
            aad: aad
        )

        XCTAssertEqual(decrypted, plaintext)
    }

    func testPublicKeyEncoding() throws {
        let keyPair = crypto.generateKeyPair()

        // Encode
        let encoded = crypto.encodePublicKey(keyPair.publicKey)
        XCTAssertFalse(encoded.isEmpty)

        // Decode
        let decoded = try crypto.parsePublicKey(base64: encoded)
        XCTAssertEqual(decoded.rawRepresentation, keyPair.publicKey.rawRepresentation)
    }

    func testFingerprint() {
        let data = "test data".data(using: .utf8)!
        let fingerprint = crypto.computeFingerprint(data: data)

        XCTAssertFalse(fingerprint.isEmpty)

        // Same data should produce same fingerprint
        let fingerprint2 = crypto.computeFingerprint(data: data)
        XCTAssertEqual(fingerprint, fingerprint2)

        // Different data should produce different fingerprint
        let differentData = "different data".data(using: .utf8)!
        let differentFingerprint = crypto.computeFingerprint(data: differentData)
        XCTAssertNotEqual(fingerprint, differentFingerprint)
    }
}
