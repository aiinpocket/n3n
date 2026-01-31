package com.aiinpocket.n3n.agent.service;

import com.aiinpocket.n3n.agent.context.ComponentContextBuilder;
import com.aiinpocket.n3n.agent.entity.AgentConversation;
import com.aiinpocket.n3n.agent.entity.AgentConversation.ConversationStatus;
import com.aiinpocket.n3n.agent.entity.AgentConversation.MessageRole;
import com.aiinpocket.n3n.agent.entity.AgentMessage;
import com.aiinpocket.n3n.agent.repository.AgentConversationRepository;
import com.aiinpocket.n3n.agent.repository.AgentMessageRepository;
import com.aiinpocket.n3n.ai.security.SessionIsolator;
import com.aiinpocket.n3n.auth.entity.User;
import com.aiinpocket.n3n.auth.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 對話管理服務
 *
 * 負責對話的建立、管理、訊息儲存等
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final AgentConversationRepository conversationRepository;
    private final AgentMessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ComponentContextBuilder contextBuilder;
    private final SessionIsolator sessionIsolator;

    /**
     * 建立新對話
     *
     * @param userId 使用者 ID
     * @param title 對話標題
     * @return 新建立的對話
     */
    @Transactional
    public AgentConversation createConversation(UUID userId, String title) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        // 建構元件上下文快照
        Map<String, Object> contextSnapshot = contextBuilder.buildContext();

        // 建立對話
        AgentConversation conversation = AgentConversation.builder()
            .user(user)
            .title(title != null ? title : "新對話")
            .status(ConversationStatus.ACTIVE)
            .contextSnapshot(contextSnapshot)
            .build();

        conversation = conversationRepository.save(conversation);

        // 建立 Session
        sessionIsolator.createSession(userId, conversation.getId());

        // 加入系統訊息（元件上下文）
        String systemPrompt = buildSystemPrompt(contextSnapshot);
        addMessage(conversation, MessageRole.SYSTEM, systemPrompt);

        log.info("Created conversation {} for user {}", conversation.getId(), userId);
        return conversation;
    }

    /**
     * 取得使用者的對話列表
     */
    public Page<AgentConversation> getUserConversations(UUID userId, Pageable pageable) {
        return conversationRepository.findByUserIdOrderByUpdatedAtDesc(userId, pageable);
    }

    /**
     * 取得使用者的活躍對話
     */
    public Page<AgentConversation> getActiveConversations(UUID userId, Pageable pageable) {
        return conversationRepository.findByUserIdAndStatusOrderByUpdatedAtDesc(
            userId, ConversationStatus.ACTIVE, pageable);
    }

    /**
     * 取得對話（驗證使用者權限）
     *
     * @param userId 使用者 ID
     * @param conversationId 對話 ID
     * @return 對話
     */
    @Transactional(readOnly = true)
    public AgentConversation getConversation(UUID userId, UUID conversationId) {
        // 驗證 Session 權限
        sessionIsolator.validateAccess(userId, conversationId);

        return conversationRepository.findByIdWithMessages(conversationId)
            .orElseThrow(() -> new EntityNotFoundException("Conversation not found: " + conversationId));
    }

    /**
     * 取得對話的所有訊息
     */
    public List<AgentMessage> getMessages(UUID userId, UUID conversationId) {
        sessionIsolator.validateAccess(userId, conversationId);
        return messageRepository.findByConversationIdOrderByCreatedAtAsc(conversationId);
    }

    /**
     * 取得對話的最後 N 則訊息
     */
    public List<AgentMessage> getLastMessages(UUID userId, UUID conversationId, int count) {
        sessionIsolator.validateAccess(userId, conversationId);
        return messageRepository.findLastMessages(conversationId, PageRequest.of(0, count));
    }

    /**
     * 新增使用者訊息
     */
    @Transactional
    public AgentMessage addUserMessage(UUID userId, UUID conversationId, String content) {
        sessionIsolator.validateAccess(userId, conversationId);

        AgentConversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new EntityNotFoundException("Conversation not found"));

        if (conversation.getStatus() != ConversationStatus.ACTIVE) {
            throw new IllegalStateException("Cannot add message to non-active conversation");
        }

        return addMessage(conversation, MessageRole.USER, content);
    }

    /**
     * 新增 AI 回應訊息
     */
    @Transactional
    public AgentMessage addAssistantMessage(
            UUID conversationId,
            String content,
            Map<String, Object> structuredData,
            String modelId,
            int tokenCount,
            long latencyMs) {

        AgentConversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new EntityNotFoundException("Conversation not found"));

        AgentMessage message = AgentMessage.builder()
            .conversation(conversation)
            .role(MessageRole.ASSISTANT)
            .content(content)
            .structuredData(structuredData)
            .modelId(modelId)
            .tokenCount(tokenCount)
            .latencyMs(latencyMs)
            .build();

        message = messageRepository.save(message);
        log.debug("Added assistant message to conversation {}", conversationId);
        return message;
    }

    /**
     * 更新對話標題
     */
    @Transactional
    public AgentConversation updateTitle(UUID userId, UUID conversationId, String newTitle) {
        sessionIsolator.validateAccess(userId, conversationId);

        AgentConversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new EntityNotFoundException("Conversation not found"));

        conversation.setTitle(newTitle);
        return conversationRepository.save(conversation);
    }

    /**
     * 完成對話（使用者確認流程或結束對話）
     */
    @Transactional
    public AgentConversation completeConversation(UUID userId, UUID conversationId, UUID flowId) {
        sessionIsolator.validateAccess(userId, conversationId);

        AgentConversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new EntityNotFoundException("Conversation not found"));

        conversation.setStatus(ConversationStatus.COMPLETED);
        conversation.setDraftFlowId(flowId);

        // 終止 Session
        sessionIsolator.terminateSession(userId, conversationId);

        return conversationRepository.save(conversation);
    }

    /**
     * 取消對話
     */
    @Transactional
    public AgentConversation cancelConversation(UUID userId, UUID conversationId) {
        sessionIsolator.validateAccess(userId, conversationId);

        AgentConversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new EntityNotFoundException("Conversation not found"));

        conversation.setStatus(ConversationStatus.CANCELLED);

        // 終止 Session
        sessionIsolator.terminateSession(userId, conversationId);

        return conversationRepository.save(conversation);
    }

    /**
     * 刪除對話（軟刪除：封存）
     */
    @Transactional
    public void archiveConversation(UUID userId, UUID conversationId) {
        sessionIsolator.validateAccess(userId, conversationId);

        AgentConversation conversation = conversationRepository.findById(conversationId)
            .orElseThrow(() -> new EntityNotFoundException("Conversation not found"));

        conversation.setStatus(ConversationStatus.ARCHIVED);
        conversationRepository.save(conversation);

        // 終止 Session
        try {
            sessionIsolator.terminateSession(userId, conversationId);
        } catch (Exception e) {
            // Session 可能已過期
            log.debug("Session already terminated for conversation {}", conversationId);
        }
    }

    /**
     * 取得對話的 token 使用統計
     */
    public int getTotalTokenCount(UUID conversationId) {
        return messageRepository.sumTokenCountByConversationId(conversationId);
    }

    /**
     * 新增訊息到對話
     */
    private AgentMessage addMessage(AgentConversation conversation, MessageRole role, String content) {
        AgentMessage message = AgentMessage.builder()
            .conversation(conversation)
            .role(role)
            .content(content)
            .tokenCount(estimateTokenCount(content))
            .build();

        return messageRepository.save(message);
    }

    /**
     * 建構系統提示
     */
    private String buildSystemPrompt(Map<String, Object> context) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是 N3N Flow Platform 的 AI 工作流程助手。\n\n");
        sb.append("你的任務是幫助使用者設計和建構工作流程。使用者會用自然語言描述他們的需求，");
        sb.append("你需要：\n");
        sb.append("1. 理解使用者的需求\n");
        sb.append("2. 推薦適合的現有元件\n");
        sb.append("3. 如果需要，建議新增元件\n");
        sb.append("4. 生成完整的流程定義\n\n");

        // 加入元件上下文
        String componentContext = contextBuilder.buildContextPrompt();
        sb.append(componentContext);

        sb.append("\n\n當你推薦元件或生成流程時，請使用以下 JSON 格式回應：\n");
        sb.append("```json\n");
        sb.append("{\n");
        sb.append("  \"understanding\": \"對使用者需求的理解\",\n");
        sb.append("  \"existingComponents\": [\n");
        sb.append("    { \"name\": \"元件名稱\", \"purpose\": \"用途說明\" }\n");
        sb.append("  ],\n");
        sb.append("  \"suggestedNewComponents\": [\n");
        sb.append("    { \"name\": \"新元件名稱\", \"description\": \"功能描述\" }\n");
        sb.append("  ],\n");
        sb.append("  \"flowDefinition\": {\n");
        sb.append("    \"nodes\": [...],\n");
        sb.append("    \"edges\": [...]\n");
        sb.append("  }\n");
        sb.append("}\n");
        sb.append("```\n");

        return sb.toString();
    }

    /**
     * 預估 token 數量
     */
    private int estimateTokenCount(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        // 粗略估計：中文約 1.5 tokens/字，英文約 0.25 tokens/字
        int chineseChars = 0;
        int otherChars = 0;
        for (char c : content.toCharArray()) {
            if (Character.UnicodeScript.of(c) == Character.UnicodeScript.HAN) {
                chineseChars++;
            } else {
                otherChars++;
            }
        }
        return (int) (chineseChars * 1.5 + otherChars * 0.25);
    }
}
