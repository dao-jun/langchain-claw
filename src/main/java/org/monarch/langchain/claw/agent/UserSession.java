package org.monarch.langchain.claw.agent;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.monarch.langchain.claw.models.Plan;
import org.monarch.langchain.claw.models.SessionState;

import java.util.HashMap;
import java.util.Map;

public class UserSession {
    public final String userId;
    public SessionState state = SessionState.INIT;
    public final ChatMemory shortTermMemory = MessageWindowChatMemory.builder()
            .maxMessages(20)          // 保留最近20条消息
            .build();
    public final EmbeddingStore<TextSegment> longTermMemory = new InMemoryEmbeddingStore<>(); // 内存向量存储

    public Plan currentPlan;
    public int currentStepIndex = 0;         // 当前执行到第几步
    public Map<String, Object> context = new HashMap<>(); // 共享上下文数据

    // 用于人工介入时的回调
    public Runnable onUserInputNeeded;

    public UserSession(String userId) {
        this.userId = userId;
    }
}
