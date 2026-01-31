package com.aiinpocket.n3n.agent.context;

import com.aiinpocket.n3n.skill.BuiltinSkill;
import com.aiinpocket.n3n.skill.entity.Skill;
import com.aiinpocket.n3n.skill.repository.SkillRepository;
import com.aiinpocket.n3n.skill.service.BuiltinSkillRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 技能上下文建構器
 *
 * 負責建構 AI 可理解的技能上下文，讓 AI 知道系統中有哪些可用的技能。
 * 技能是預先準備好的 API，執行時不需要 AI 介入。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillContextBuilder {

    private final SkillRepository skillRepository;
    private final BuiltinSkillRegistry builtinSkillRegistry;

    /**
     * 建構完整的技能上下文
     *
     * @return 技能上下文 Map
     */
    public Map<String, Object> buildContext() {
        log.debug("Building skill context...");

        List<Map<String, Object>> allSkills = new ArrayList<>();
        Set<String> categories = new HashSet<>();

        // 1. 加入內建技能
        for (BuiltinSkill skill : builtinSkillRegistry.getAllBuiltinSkills()) {
            Map<String, Object> skillEntry = buildBuiltinSkillEntry(skill);
            allSkills.add(skillEntry);
            categories.add(skill.getCategory());
        }

        // 2. 加入資料庫中的自訂技能
        List<Skill> customSkills = skillRepository.findByIsEnabledTrue();
        for (Skill skill : customSkills) {
            Map<String, Object> skillEntry = buildCustomSkillEntry(skill);
            allSkills.add(skillEntry);
            if (skill.getCategory() != null) {
                categories.add(skill.getCategory());
            }
        }

        Map<String, Object> context = new LinkedHashMap<>();
        context.put("availableSkills", allSkills);
        context.put("skillCategories", categories.stream().sorted().collect(Collectors.toList()));
        context.put("totalSkills", allSkills.size());

        log.info("Built skill context with {} skills", allSkills.size());
        return context;
    }

    /**
     * 建構適用於 AI System Prompt 的技能描述文字
     *
     * @return 人類可讀的技能描述
     */
    public String buildContextPrompt() {
        Map<String, Object> context = buildContext();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> skills =
            (List<Map<String, Object>>) context.get("availableSkills");

        if (skills == null || skills.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 可用技能 (Skills)\n\n");
        sb.append("以下技能是預先準備好的自動化能力，在流程執行時會直接呼叫 API，");
        sb.append("不需要 AI 介入（省 token、穩定可靠）：\n\n");

        // 按類別分組
        Map<String, List<Map<String, Object>>> byCategory = skills.stream()
            .collect(Collectors.groupingBy(
                s -> (String) s.getOrDefault("category", "其他")
            ));

        for (Map.Entry<String, List<Map<String, Object>>> entry : byCategory.entrySet()) {
            sb.append("### ").append(getCategoryDisplayName(entry.getKey())).append("\n\n");

            for (Map<String, Object> skill : entry.getValue()) {
                sb.append("- **").append(skill.get("displayName")).append("**");
                sb.append(" (`").append(skill.get("name")).append("`)");
                if (skill.get("description") != null) {
                    sb.append(": ").append(skill.get("description"));
                }
                sb.append("\n");

                // 輸入參數
                @SuppressWarnings("unchecked")
                Map<String, Object> inputSchema =
                    (Map<String, Object>) skill.get("inputSchema");
                if (inputSchema != null) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> properties =
                        (Map<String, Object>) inputSchema.get("properties");
                    if (properties != null && !properties.isEmpty()) {
                        sb.append("  - 參數: ");
                        sb.append(properties.keySet().stream()
                            .map(k -> "`" + k + "`")
                            .collect(Collectors.joining(", ")));
                        sb.append("\n");
                    }
                }
            }
            sb.append("\n");
        }

        sb.append("**重要**：當使用者需要執行網路請求、資料處理、通知發送等操作時，");
        sb.append("優先使用這些技能而非建議自訂元件。技能執行時不消耗 AI token。\n\n");

        return sb.toString();
    }

    /**
     * 建構內建技能項目
     */
    private Map<String, Object> buildBuiltinSkillEntry(BuiltinSkill skill) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", skill.getName());
        entry.put("displayName", skill.getDisplayName());
        entry.put("description", skill.getDescription());
        entry.put("category", skill.getCategory());
        entry.put("isBuiltin", true);
        entry.put("inputSchema", skill.getInputSchema());
        entry.put("outputSchema", skill.getOutputSchema());
        return entry;
    }

    /**
     * 建構自訂技能項目
     */
    private Map<String, Object> buildCustomSkillEntry(Skill skill) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", skill.getName());
        entry.put("displayName", skill.getDisplayName());
        entry.put("description", skill.getDescription());
        entry.put("category", skill.getCategory());
        entry.put("isBuiltin", false);
        entry.put("inputSchema", skill.getInputSchema());
        entry.put("outputSchema", skill.getOutputSchema());
        return entry;
    }

    /**
     * 取得類別顯示名稱
     */
    private String getCategoryDisplayName(String category) {
        return switch (category) {
            case "web" -> "網頁操作 (Web)";
            case "http" -> "HTTP 請求";
            case "data" -> "資料處理 (Data)";
            case "notify" -> "通知 (Notification)";
            case "file" -> "檔案操作 (File)";
            case "system" -> "系統 (System)";
            default -> category;
        };
    }

    /**
     * 根據名稱搜尋技能
     */
    public List<Map<String, Object>> searchSkills(String query) {
        Map<String, Object> context = buildContext();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> skills =
            (List<Map<String, Object>>) context.get("availableSkills");

        if (skills == null || query == null || query.isEmpty()) {
            return skills != null ? skills : List.of();
        }

        String lowerQuery = query.toLowerCase();
        return skills.stream()
            .filter(s -> {
                String name = (String) s.get("name");
                String displayName = (String) s.get("displayName");
                String description = (String) s.get("description");

                return (name != null && name.toLowerCase().contains(lowerQuery)) ||
                       (displayName != null && displayName.toLowerCase().contains(lowerQuery)) ||
                       (description != null && description.toLowerCase().contains(lowerQuery));
            })
            .collect(Collectors.toList());
    }
}
