package org.monarch.langchain.claw.memory;

import java.util.List;

public interface MemoryManager {
    void storeConversation(String userId, String sessionId, String content);

    List<MemorySnippet> recall(String userId, String message, int limit);
}
