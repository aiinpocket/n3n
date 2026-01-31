package com.aiinpocket.n3n.credential.wordlist;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * BIP-39 English Word List
 *
 * 提供 2048 個英文單詞用於產生 Recovery Key 助記詞。
 * 詞庫來源：https://github.com/bitcoin/bips/blob/master/bip-0039/english.txt
 */
@Component
@Slf4j
public class BIP39WordList {

    private static final String WORDLIST_PATH = "wordlist/bip39-english.txt";
    private static final int EXPECTED_WORD_COUNT = 2048;

    private List<String> words;
    private Set<String> wordSet;

    @PostConstruct
    public void init() {
        loadWordList();
    }

    private void loadWordList() {
        words = new ArrayList<>(EXPECTED_WORD_COUNT);
        wordSet = new HashSet<>(EXPECTED_WORD_COUNT);

        try {
            ClassPathResource resource = new ClassPathResource(WORDLIST_PATH);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String word = line.trim().toLowerCase();
                    if (!word.isEmpty()) {
                        words.add(word);
                        wordSet.add(word);
                    }
                }
            }

            if (words.size() != EXPECTED_WORD_COUNT) {
                throw new IllegalStateException(
                    "BIP39 word list should contain " + EXPECTED_WORD_COUNT +
                    " words, but found " + words.size());
            }

            log.info("BIP39 word list loaded: {} words", words.size());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load BIP39 word list", e);
        }
    }

    /**
     * 取得指定索引的單詞
     * @param index 0-2047
     */
    public String getWord(int index) {
        if (index < 0 || index >= words.size()) {
            throw new IndexOutOfBoundsException("Word index must be 0-" + (words.size() - 1));
        }
        return words.get(index);
    }

    /**
     * 取得單詞在詞庫中的索引
     * @return 索引 (0-2047)，如果不存在則返回 -1
     */
    public int getIndex(String word) {
        if (word == null) {
            return -1;
        }
        return words.indexOf(word.toLowerCase().trim());
    }

    /**
     * 檢查單詞是否在詞庫中
     */
    public boolean contains(String word) {
        if (word == null) {
            return false;
        }
        return wordSet.contains(word.toLowerCase().trim());
    }

    /**
     * 取得詞庫大小
     */
    public int size() {
        return words.size();
    }

    /**
     * 取得所有單詞（唯讀）
     */
    public List<String> getWords() {
        return List.copyOf(words);
    }
}
