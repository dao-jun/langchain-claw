package org.monarch.langchain.claw.session;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.monarch.langchain.claw.common.SessionState;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class JpaSessionStore implements SessionStore {

    private final UserSessionRepository sessionRepository;
    private final SessionMessageRepository messageRepository;

    public JpaSessionStore(UserSessionRepository sessionRepository, SessionMessageRepository messageRepository) {
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
    }

    @Override
    public UserSession getOrCreate(String userId, String sessionId) {
        return find(userId, sessionId)
            .orElseGet(() -> save(newSession(userId, sessionId)));
    }

    @Override
    public UserSession save(UserSession session) {
        UserSessionEntity entity = sessionRepository.findBySessionIdAndUserId(session.getSessionId(), session.getUserId())
            .orElseGet(UserSessionEntity::new);
        entity.setSessionId(session.getSessionId());
        entity.setUserId(session.getUserId());
        entity.setState(session.getState());
        entity.setCurrentPlanJson(session.getCurrentPlanJson());
        entity.setCurrentStepIndex(session.getCurrentStepIndex());
        entity.setPendingQuestion(session.getPendingQuestion());
        UserSessionEntity saved = sessionRepository.save(entity);
        return toModel(saved);
    }

    @Override
    public void appendMessage(String sessionId, String role, String content) {
        SessionMessageEntity message = new SessionMessageEntity();
        message.setSessionId(sessionId);
        message.setMessageOrder((int) messageRepository.countBySessionId(sessionId) + 1);
        message.setRole(role);
        message.setContent(content);
        messageRepository.save(message);
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> recentMessages(String sessionId, int limit) {
        List<SessionMessageEntity> messages = messageRepository.findBySessionIdOrderByMessageOrderAsc(sessionId);
        int fromIndex = Math.max(messages.size() - limit, 0);
        return messages.subList(fromIndex, messages.size()).stream()
            .map(message -> message.getRole() + ": " + message.getContent())
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSession> find(String userId, String sessionId) {
        return sessionRepository.findBySessionIdAndUserId(sessionId, userId).map(this::toModel);
    }

    @Override
    public void updateState(String userId, String sessionId, SessionState state, String pendingQuestion, String currentPlanJson) {
        UserSession session = getOrCreate(userId, sessionId);
        session.setState(state);
        session.setPendingQuestion(pendingQuestion);
        session.setCurrentPlanJson(currentPlanJson);
        save(session);
    }

    private UserSession newSession(String userId, String sessionId) {
        UserSession session = new UserSession();
        session.setSessionId(sessionId);
        session.setUserId(userId);
        session.setState(SessionState.INIT);
        session.setCurrentStepIndex(0);
        session.setCreatedAt(Instant.now());
        session.setUpdatedAt(Instant.now());
        return session;
    }

    private UserSession toModel(UserSessionEntity entity) {
        UserSession session = new UserSession();
        session.setSessionId(entity.getSessionId());
        session.setUserId(entity.getUserId());
        session.setState(entity.getState());
        session.setCurrentPlanJson(entity.getCurrentPlanJson());
        session.setCurrentStepIndex(entity.getCurrentStepIndex());
        session.setPendingQuestion(entity.getPendingQuestion());
        session.setCreatedAt(entity.getCreatedAt());
        session.setUpdatedAt(entity.getUpdatedAt());
        return session;
    }
}
