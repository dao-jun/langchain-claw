package org.monarch.langchain.claw.session;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.monarch.langchain.claw.common.SessionState;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public class JpaSessionStore implements SessionStore {

    private final UserSessionRepository sessionRepository;
    private final SessionMessageRepository messageRepository;
    private final ConcurrentHashMap<String, Object> messageLocks = new ConcurrentHashMap<>();

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
        UserSessionEntity entity = toEntity(session);
        UserSessionEntity saved = sessionRepository.saveAndFlush(entity);
        return toModel(saved);
    }

    @Override
    public void appendMessage(String sessionId, String role, String content) {
        Object lock = messageLocks.computeIfAbsent(sessionId, ignored -> new Object());
        synchronized (lock) {
            SessionMessageEntity message = new SessionMessageEntity();
            message.setSessionId(sessionId);
            SessionMessageEntity latestMessage = messageRepository.findTopBySessionIdOrderByMessageOrderDesc(sessionId);
            message.setMessageOrder(latestMessage == null ? 1 : latestMessage.getMessageOrder() + 1);
            message.setRole(role);
            message.setContent(content);
            messageRepository.save(message);
        }
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
        UserSessionEntity entity = sessionRepository.findBySessionIdAndUserId(sessionId, userId)
            .orElseGet(() -> toEntity(newSession(userId, sessionId)));
        SessionStateMachine.validate(sessionId, entity.getState(), state);
        entity.setState(state);
        entity.setPendingQuestion(pendingQuestion);
        entity.setCurrentPlanJson(currentPlanJson);
        sessionRepository.saveAndFlush(entity);
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
        session.setId(entity.getId());
        session.setSessionId(entity.getSessionId());
        session.setUserId(entity.getUserId());
        session.setState(entity.getState());
        session.setCurrentPlanJson(entity.getCurrentPlanJson());
        session.setCurrentStepIndex(entity.getCurrentStepIndex());
        session.setPendingQuestion(entity.getPendingQuestion());
        session.setVersion(entity.getVersion());
        session.setCreatedAt(entity.getCreatedAt());
        session.setUpdatedAt(entity.getUpdatedAt());
        return session;
    }

    private UserSessionEntity toEntity(UserSession session) {
        UserSessionEntity entity = new UserSessionEntity();
        entity.setId(session.getId());
        entity.setVersion(session.getVersion());
        entity.setSessionId(session.getSessionId());
        entity.setUserId(session.getUserId());
        entity.setState(session.getState());
        entity.setCurrentPlanJson(session.getCurrentPlanJson());
        entity.setCurrentStepIndex(session.getCurrentStepIndex());
        entity.setPendingQuestion(session.getPendingQuestion());
        entity.setCreatedAt(session.getCreatedAt());
        entity.setUpdatedAt(session.getUpdatedAt());
        return entity;
    }
}
