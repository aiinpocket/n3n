package com.aiinpocket.n3n.ai.agent;

import lombok.Builder;
import lombok.Data;
import java.util.*;

/**
 * 使用者意圖
 */
@Data
@Builder
public class Intent {

    /** 主要意圖類型 */
    private IntentType type;

    /** 意圖信心度 (0.0 - 1.0) */
    @Builder.Default
    private double confidence = 0.0;

    /** 提取的實體 */
    @Builder.Default
    private Map<String, Object> entities = new HashMap<>();

    /** 子意圖（複合任務） */
    private List<Intent> subIntents;

    /** 原始理解描述 */
    private String understanding;

    public enum IntentType {
        // Discovery 相關
        SEARCH_NODE,           // 搜尋節點/元件
        GET_DOCUMENTATION,     // 取得文件說明
        FIND_EXAMPLES,         // 尋找範例
        SEARCH_SKILL,          // 搜尋技能

        // Builder 相關
        CREATE_FLOW,           // 建立新流程
        ADD_NODE,              // 新增節點
        REMOVE_NODE,           // 移除節點
        CONNECT_NODES,         // 連接節點
        CONFIGURE_NODE,        // 配置節點
        MODIFY_FLOW,           // 修改現有流程
        OPTIMIZE_FLOW,         // 優化流程

        // Responder 相關
        EXPLAIN,               // 解釋說明
        CLARIFY,               // 澄清問題
        CONFIRM,               // 確認操作

        // 複合意圖
        COMPOUND,              // 多個意圖組合

        // 其他
        UNKNOWN,               // 無法識別
        CHITCHAT               // 閒聊
    }

    /**
     * 判斷是否為 Discovery 相關意圖
     */
    public boolean isDiscoveryIntent() {
        return type == IntentType.SEARCH_NODE ||
               type == IntentType.GET_DOCUMENTATION ||
               type == IntentType.FIND_EXAMPLES ||
               type == IntentType.SEARCH_SKILL;
    }

    /**
     * 判斷是否為 Builder 相關意圖
     */
    public boolean isBuilderIntent() {
        return type == IntentType.CREATE_FLOW ||
               type == IntentType.ADD_NODE ||
               type == IntentType.REMOVE_NODE ||
               type == IntentType.CONNECT_NODES ||
               type == IntentType.CONFIGURE_NODE ||
               type == IntentType.MODIFY_FLOW ||
               type == IntentType.OPTIMIZE_FLOW;
    }

    /**
     * 判斷是否為 Responder 相關意圖
     */
    public boolean isResponderIntent() {
        return type == IntentType.EXPLAIN ||
               type == IntentType.CLARIFY ||
               type == IntentType.CONFIRM ||
               type == IntentType.CHITCHAT ||
               type == IntentType.UNKNOWN;
    }
}
