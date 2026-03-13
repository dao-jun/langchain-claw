package org.monarch.langchain.claw.session;

import java.util.List;
import java.util.Optional;
import org.monarch.langchain.claw.common.SessionState;

public interface SessionStore {
    UserSession getOrCreate(String userId, String sessionId);

    UserSession save(UserSession session);

    void appendMessage(String sessionId, String role, String content);

    List<String> recentMessages(String sessionId, int limit);

    Optional<UserSession> find(String userId, String sessionId);

    void updateState(String userId, String sessionId, SessionState state, String pendingQuestion, String currentPlanJson);
}
