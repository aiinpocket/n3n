package com.aiinpocket.n3n.ai.provider;

/**
 * 助手類 AI 呼叫的任務類型：讓多個已設定的供應商能依任務自動分工，
 * 而不是全部擠在同一個預設供應商上。
 *
 * - LIGHT：分類、擷取、設定修復、白話翻譯等小任務——挑快而省的供應商
 * - HEAVY：流程生成、執行分析、優化建議等重任務——挑推理最強的供應商
 * - DEFAULT：不指定，沿用平台預設解析順序
 */
public enum AiTaskType {
    LIGHT,
    HEAVY,
    DEFAULT
}
