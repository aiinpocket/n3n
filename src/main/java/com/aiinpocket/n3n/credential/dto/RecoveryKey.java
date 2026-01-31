package com.aiinpocket.n3n.credential.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Recovery Key DTO
 *
 * 代表一組 8 個英文單詞的 Recovery Key（助記詞）。
 * Recovery Key 用於備份和還原加密金鑰，類似加密貨幣錢包的助記詞機制。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryKey {

    /**
     * 8 個英文單詞
     */
    private List<String> words;

    /**
     * Recovery Key 的 SHA-256 Hash（用於驗證，不儲存原始 Key）
     */
    private String keyHash;

    /**
     * 金鑰版本
     */
    private Integer keyVersion;

    /**
     * 將單詞列表轉換為空格分隔的字串
     */
    public String toPhrase() {
        if (words == null || words.isEmpty()) {
            return "";
        }
        return String.join(" ", words);
    }

    /**
     * 從空格分隔的字串建立 RecoveryKey
     */
    public static RecoveryKey fromPhrase(String phrase) {
        if (phrase == null || phrase.isBlank()) {
            return new RecoveryKey();
        }
        String[] wordArray = phrase.trim().toLowerCase().split("\\s+");
        return RecoveryKey.builder()
                .words(List.of(wordArray))
                .build();
    }

    /**
     * 取得單詞數量
     */
    public int getWordCount() {
        return words == null ? 0 : words.size();
    }

    /**
     * 遮蔽顯示（只顯示第一個和最後一個單詞）
     * 例如: "apple *** *** *** *** *** *** honey"
     */
    public String toMaskedPhrase() {
        if (words == null || words.size() < 2) {
            return "***";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(words.get(0));
        for (int i = 1; i < words.size() - 1; i++) {
            sb.append(" ***");
        }
        sb.append(" ").append(words.get(words.size() - 1));
        return sb.toString();
    }
}
