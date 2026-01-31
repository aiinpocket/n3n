package com.aiinpocket.n3n.credential.service;

import com.aiinpocket.n3n.credential.dto.RecoveryKey;
import com.aiinpocket.n3n.credential.wordlist.BIP39WordList;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Recovery Key Service
 *
 * 負責 Recovery Key 的產生、驗證和 Master Key 衍生。
 *
 * Recovery Key 是 8 個英文單詞的助記詞，提供約 88 bits 的熵值 (log2(2048^8) ≈ 88)。
 * 雖然不如標準 BIP-39 的 12/24 個單詞安全，但對於企業內部使用足夠，
 * 且更容易記憶和備份。
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class RecoveryKeyService {

    private static final int WORD_COUNT = 8;
    private static final int PBKDF2_ITERATIONS = 310000; // OWASP 2023 recommendation
    private static final int DERIVED_KEY_LENGTH = 256; // bits

    private final BIP39WordList wordList;

    /**
     * 產生新的 Recovery Key
     *
     * @return 包含 8 個英文單詞的 RecoveryKey
     */
    public RecoveryKey generate() {
        SecureRandom random = new SecureRandom();
        List<String> words = new ArrayList<>(WORD_COUNT);

        for (int i = 0; i < WORD_COUNT; i++) {
            int index = random.nextInt(wordList.size());
            words.add(wordList.getWord(index));
        }

        String keyHash = calculateKeyHash(words);

        RecoveryKey recoveryKey = RecoveryKey.builder()
                .words(words)
                .keyHash(keyHash)
                .keyVersion(1)
                .build();

        log.info("Generated new Recovery Key (hash: {}...)", keyHash.substring(0, 8));
        return recoveryKey;
    }

    /**
     * 驗證 Recovery Key 格式
     *
     * @param phrase 空格分隔的單詞字串
     * @return true 如果格式有效
     */
    public boolean validate(String phrase) {
        if (phrase == null || phrase.isBlank()) {
            return false;
        }

        String[] words = phrase.trim().toLowerCase().split("\\s+");
        if (words.length != WORD_COUNT) {
            log.debug("Invalid word count: {} (expected {})", words.length, WORD_COUNT);
            return false;
        }

        for (String word : words) {
            if (!wordList.contains(word)) {
                log.debug("Invalid word: {}", word);
                return false;
            }
        }

        return true;
    }

    /**
     * 驗證 Recovery Key 是否與儲存的 Hash 匹配
     *
     * @param phrase 空格分隔的單詞字串
     * @param storedHash 儲存的 Hash 值
     * @return true 如果匹配
     */
    public boolean verifyHash(String phrase, String storedHash) {
        if (!validate(phrase) || storedHash == null) {
            return false;
        }

        RecoveryKey key = RecoveryKey.fromPhrase(phrase);
        String calculatedHash = calculateKeyHash(key.getWords());
        return MessageDigest.isEqual(
                calculatedHash.getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * 從 Recovery Key 衍生 Master Key
     *
     * 使用 PBKDF2-HMAC-SHA256 進行金鑰衍生。
     * Instance Salt 確保同一 Recovery Key 在不同環境產生不同的 Master Key。
     *
     * @param phrase Recovery Key 字串
     * @param instanceSalt 實例 Salt（環境特定）
     * @return 256-bit Master Key
     */
    public byte[] deriveMasterKey(String phrase, byte[] instanceSalt) {
        if (!validate(phrase)) {
            throw new IllegalArgumentException("Invalid Recovery Key format");
        }

        if (instanceSalt == null || instanceSalt.length < 16) {
            throw new IllegalArgumentException("Instance salt must be at least 16 bytes");
        }

        try {
            // 正規化 phrase
            String normalizedPhrase = normalizePhrase(phrase);

            PBEKeySpec spec = new PBEKeySpec(
                    normalizedPhrase.toCharArray(),
                    instanceSalt,
                    PBKDF2_ITERATIONS,
                    DERIVED_KEY_LENGTH
            );

            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] derivedKey = factory.generateSecret(spec).getEncoded();

            log.debug("Master key derived successfully (salt length: {})", instanceSalt.length);
            return derivedKey;

        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException("Master key derivation failed", e);
        }
    }

    /**
     * 產生 Instance Salt
     *
     * @return 32 bytes 的隨機 Salt
     */
    public byte[] generateInstanceSalt() {
        byte[] salt = new byte[32];
        new SecureRandom().nextBytes(salt);
        log.info("Generated new Instance Salt");
        return salt;
    }

    /**
     * 計算 Recovery Key 的 SHA-256 Hash
     *
     * 用於驗證 Recovery Key 而不儲存原始值。
     *
     * @param words 單詞列表
     * @return Base64 編碼的 SHA-256 Hash
     */
    public String calculateKeyHash(List<String> words) {
        try {
            String phrase = String.join(" ", words).toLowerCase();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(phrase.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * 計算 Master Key 的 Hash（用於驗證）
     *
     * @param masterKey Master Key bytes
     * @return Base64 編碼的 SHA-256 Hash
     */
    public String calculateMasterKeyHash(byte[] masterKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(masterKey);
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * 正規化 Recovery Key phrase
     * - 轉小寫
     * - 移除多餘空白
     * - 確保單詞間只有一個空格
     */
    private String normalizePhrase(String phrase) {
        return phrase.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    /**
     * 取得期望的單詞數量
     */
    public int getExpectedWordCount() {
        return WORD_COUNT;
    }
}
