package com.aiinpocket.n3n.credential.service;

import com.aiinpocket.n3n.credential.dto.RecoveryKey;
import com.aiinpocket.n3n.credential.wordlist.BIP39WordList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class RecoveryKeyServiceTest {

    private RecoveryKeyService recoveryKeyService;
    private BIP39WordList wordList;

    @BeforeEach
    void setUp() {
        wordList = new BIP39WordList();
        wordList.init();  // 手動觸發 @PostConstruct 初始化
        recoveryKeyService = new RecoveryKeyService(wordList);
    }

    // ========== Generation Tests ==========

    @Nested
    @DisplayName("Generation Tests")
    class GenerationTests {

        @Test
        @DisplayName("generate() should return 12 words")
        void generate_shouldReturn12Words() {
            // When
            RecoveryKey key = recoveryKeyService.generate();

            // Then
            assertThat(key).isNotNull();
            assertThat(key.getWords()).hasSize(12);
        }

        @Test
        @DisplayName("generate() should return valid BIP39 words")
        void generate_shouldReturnValidBIP39Words() {
            // When
            RecoveryKey key = recoveryKeyService.generate();

            // Then
            for (String word : key.getWords()) {
                assertThat(wordList.contains(word.toLowerCase()))
                        .as("Word '%s' should be in BIP39 wordlist", word)
                        .isTrue();
            }
        }

        @Test
        @DisplayName("generate() should produce unique keys")
        void generate_shouldProduceUniqueKeys() {
            // When
            RecoveryKey key1 = recoveryKeyService.generate();
            RecoveryKey key2 = recoveryKeyService.generate();

            // Then
            assertThat(key1.getWords()).isNotEqualTo(key2.getWords());
            assertThat(key1.getKeyHash()).isNotEqualTo(key2.getKeyHash());
        }

        @Test
        @DisplayName("generate() should include key hash")
        void generate_shouldIncludeKeyHash() {
            // When
            RecoveryKey key = recoveryKeyService.generate();

            // Then
            assertThat(key.getKeyHash()).isNotNull();
            assertThat(key.getKeyHash()).isNotEmpty();
        }
    }

    // ========== Validation Tests ==========

    @Nested
    @DisplayName("Validation Tests")
    class ValidationTests {

        @Test
        @DisplayName("validate() should accept 12 valid words")
        void validate_shouldAccept12ValidWords() {
            // Given - Use first 12 words from BIP39 wordlist
            List<String> words = Arrays.asList(
                    wordList.getWord(0), wordList.getWord(1), wordList.getWord(2),
                    wordList.getWord(3), wordList.getWord(4), wordList.getWord(5),
                    wordList.getWord(6), wordList.getWord(7), wordList.getWord(8),
                    wordList.getWord(9), wordList.getWord(10), wordList.getWord(11)
            );
            String phrase = String.join(" ", words);

            // When
            boolean result = recoveryKeyService.validate(phrase);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("validate() should accept 8 valid words (legacy)")
        void validate_shouldAccept8ValidWords_legacy() {
            // Given - 8 word legacy format
            List<String> words = Arrays.asList(
                    wordList.getWord(0), wordList.getWord(1), wordList.getWord(2),
                    wordList.getWord(3), wordList.getWord(4), wordList.getWord(5),
                    wordList.getWord(6), wordList.getWord(7)
            );
            String phrase = String.join(" ", words);

            // When
            boolean result = recoveryKeyService.validate(phrase);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("validate() should reject invalid word count")
        void validate_shouldRejectInvalidWordCount() {
            // Given - 10 words (not 8 or 12)
            List<String> words = Arrays.asList(
                    wordList.getWord(0), wordList.getWord(1), wordList.getWord(2),
                    wordList.getWord(3), wordList.getWord(4), wordList.getWord(5),
                    wordList.getWord(6), wordList.getWord(7), wordList.getWord(8),
                    wordList.getWord(9)
            );
            String phrase = String.join(" ", words);

            // When
            boolean result = recoveryKeyService.validate(phrase);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("validate() should reject invalid words")
        void validate_shouldRejectInvalidWords() {
            // Given - Contains non-BIP39 word
            String phrase = "invalid-word-xyz " + String.join(" ",
                    wordList.getWord(1), wordList.getWord(2), wordList.getWord(3),
                    wordList.getWord(4), wordList.getWord(5), wordList.getWord(6),
                    wordList.getWord(7), wordList.getWord(8), wordList.getWord(9),
                    wordList.getWord(10), wordList.getWord(11)
            );

            // When
            boolean result = recoveryKeyService.validate(phrase);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("validate() should reject null")
        void validate_shouldRejectNull() {
            // When
            boolean result = recoveryKeyService.validate(null);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("validate() should reject empty string")
        void validate_shouldRejectEmptyString() {
            // When
            boolean result = recoveryKeyService.validate("");

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("validate() should be case insensitive")
        void validate_shouldBeCaseInsensitive() {
            // Given
            List<String> words = Arrays.asList(
                    wordList.getWord(0).toUpperCase(), wordList.getWord(1),
                    wordList.getWord(2).toUpperCase(), wordList.getWord(3),
                    wordList.getWord(4).toUpperCase(), wordList.getWord(5),
                    wordList.getWord(6).toUpperCase(), wordList.getWord(7),
                    wordList.getWord(8).toUpperCase(), wordList.getWord(9),
                    wordList.getWord(10).toUpperCase(), wordList.getWord(11)
            );
            String phrase = String.join(" ", words);

            // When
            boolean result = recoveryKeyService.validate(phrase);

            // Then
            assertThat(result).isTrue();
        }
    }

    // ========== Legacy Format Detection Tests ==========

    @Nested
    @DisplayName("Legacy Format Detection Tests")
    class LegacyFormatTests {

        @Test
        @DisplayName("isLegacyFormat() should return true for 8 words")
        void isLegacyFormat_shouldReturnTrueFor8Words() {
            // Given
            List<String> words = Arrays.asList(
                    wordList.getWord(0), wordList.getWord(1), wordList.getWord(2),
                    wordList.getWord(3), wordList.getWord(4), wordList.getWord(5),
                    wordList.getWord(6), wordList.getWord(7)
            );
            String phrase = String.join(" ", words);

            // When
            boolean result = recoveryKeyService.isLegacyFormat(phrase);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("isLegacyFormat() should return false for 12 words")
        void isLegacyFormat_shouldReturnFalseFor12Words() {
            // Given
            RecoveryKey key = recoveryKeyService.generate();
            String phrase = String.join(" ", key.getWords());

            // When
            boolean result = recoveryKeyService.isLegacyFormat(phrase);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("shouldUpgrade() should return true for legacy format")
        void shouldUpgrade_shouldReturnTrueForLegacyFormat() {
            // Given
            List<String> words = Arrays.asList(
                    wordList.getWord(0), wordList.getWord(1), wordList.getWord(2),
                    wordList.getWord(3), wordList.getWord(4), wordList.getWord(5),
                    wordList.getWord(6), wordList.getWord(7)
            );
            String phrase = String.join(" ", words);

            // When
            boolean result = recoveryKeyService.shouldUpgrade(phrase);

            // Then
            assertThat(result).isTrue();
        }
    }

    // ========== Master Key Derivation Tests ==========

    @Nested
    @DisplayName("Master Key Derivation Tests")
    class MasterKeyDerivationTests {

        @Test
        @DisplayName("deriveMasterKey() should return 32 bytes for 12-word key")
        void deriveMasterKey_shouldReturn32BytesFor12WordKey() {
            // Given
            RecoveryKey key = recoveryKeyService.generate();
            String phrase = String.join(" ", key.getWords());

            // When
            byte[] masterKey = recoveryKeyService.deriveMasterKey(phrase);

            // Then
            assertThat(masterKey).hasSize(32); // 256 bits
        }

        @Test
        @DisplayName("deriveMasterKey() should return 32 bytes for 8-word key (legacy)")
        void deriveMasterKey_shouldReturn32BytesFor8WordKey_legacy() {
            // Given - 8 word legacy format
            List<String> words = Arrays.asList(
                    wordList.getWord(0), wordList.getWord(1), wordList.getWord(2),
                    wordList.getWord(3), wordList.getWord(4), wordList.getWord(5),
                    wordList.getWord(6), wordList.getWord(7)
            );
            String phrase = String.join(" ", words);

            // When
            byte[] masterKey = recoveryKeyService.deriveMasterKey(phrase);

            // Then
            assertThat(masterKey).hasSize(32); // 256 bits
        }

        @Test
        @DisplayName("deriveMasterKey() should be deterministic")
        void deriveMasterKey_shouldBeDeterministic() {
            // Given
            RecoveryKey key = recoveryKeyService.generate();
            String phrase = String.join(" ", key.getWords());

            // When
            byte[] masterKey1 = recoveryKeyService.deriveMasterKey(phrase);
            byte[] masterKey2 = recoveryKeyService.deriveMasterKey(phrase);

            // Then
            assertThat(masterKey1).isEqualTo(masterKey2);
        }

        @Test
        @DisplayName("deriveMasterKey() should produce different keys for different phrases")
        void deriveMasterKey_shouldProduceDifferentKeysForDifferentPhrases() {
            // Given
            RecoveryKey key1 = recoveryKeyService.generate();
            RecoveryKey key2 = recoveryKeyService.generate();
            String phrase1 = String.join(" ", key1.getWords());
            String phrase2 = String.join(" ", key2.getWords());

            // When
            byte[] masterKey1 = recoveryKeyService.deriveMasterKey(phrase1);
            byte[] masterKey2 = recoveryKeyService.deriveMasterKey(phrase2);

            // Then
            assertThat(masterKey1).isNotEqualTo(masterKey2);
        }

        @Test
        @DisplayName("deriveMasterKey() should throw for invalid phrase")
        void deriveMasterKey_shouldThrowForInvalidPhrase() {
            // Given
            String invalidPhrase = "invalid phrase";

            // When/Then
            assertThatThrownBy(() -> recoveryKeyService.deriveMasterKey(invalidPhrase))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Invalid Recovery Key format");
        }
    }

    // ========== Hash Verification Tests ==========

    @Nested
    @DisplayName("Hash Verification Tests")
    class HashVerificationTests {

        @Test
        @DisplayName("verifyHash() should return true for matching hash")
        void verifyHash_shouldReturnTrueForMatchingHash() {
            // Given
            RecoveryKey key = recoveryKeyService.generate();
            String phrase = String.join(" ", key.getWords());
            String storedHash = key.getKeyHash();

            // When
            boolean result = recoveryKeyService.verifyHash(phrase, storedHash);

            // Then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("verifyHash() should return false for non-matching hash")
        void verifyHash_shouldReturnFalseForNonMatchingHash() {
            // Given
            RecoveryKey key = recoveryKeyService.generate();
            String phrase = String.join(" ", key.getWords());
            String wrongHash = "wrong-hash-value";

            // When
            boolean result = recoveryKeyService.verifyHash(phrase, wrongHash);

            // Then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("verifyHash() should return false for invalid phrase")
        void verifyHash_shouldReturnFalseForInvalidPhrase() {
            // Given
            String invalidPhrase = "not a valid phrase";
            String someHash = "some-hash";

            // When
            boolean result = recoveryKeyService.verifyHash(invalidPhrase, someHash);

            // Then
            assertThat(result).isFalse();
        }
    }

    // ========== Word Count Tests ==========

    @Nested
    @DisplayName("Word Count Tests")
    class WordCountTests {

        @Test
        @DisplayName("getExpectedWordCount() should return 12")
        void getExpectedWordCount_shouldReturn12() {
            // When
            int count = recoveryKeyService.getExpectedWordCount();

            // Then
            assertThat(count).isEqualTo(12);
        }

        @Test
        @DisplayName("getLegacyWordCount() should return 8")
        void getLegacyWordCount_shouldReturn8() {
            // When
            int count = recoveryKeyService.getLegacyWordCount();

            // Then
            assertThat(count).isEqualTo(8);
        }
    }
}
