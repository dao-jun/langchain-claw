package org.monarch.langchain.claw.session;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.monarch.langchain.claw.common.SessionState;
import org.springframework.stereotype.Service;

@Service
public class SessionManager {

    private final SessionStore sessionStore;

    public SessionManager(SessionStore sessionStore) {
        this.sessionStore = sessionStore;
    }

    public UserSession getOrCreate(String userId, String maybeSessionId) {
        String sessionId = resolveSessionId(maybeSessionId);
        return sessionStore.getOrCreate(userId, sessionId);
    }

    public String resolveSessionId(String maybeSessionId) {
        return (maybeSessionId == null || maybeSessionId.isBlank())
            ? UUID.randomUUID().toString()
            : maybeSessionId;
    }

    public void appendMessage(String sessionId, String role, String content) {
        sessionStore.appendMessage(sessionId, role, content);
    }

    public List<String> recentMessages(String sessionId, int limit) {
        return sessionStore.recentMessages(sessionId, limit);
    }

    public void updateState(String userId, String sessionId, SessionState state, String pendingQuestion, String currentPlanJson) {
        sessionStore.updateState(userId, sessionId, state, pendingQuestion, currentPlanJson);
    }

    public Optional<UserSession> find(String userId, String sessionId) {
        return sessionStore.find(userId, sessionId);
    }
}
