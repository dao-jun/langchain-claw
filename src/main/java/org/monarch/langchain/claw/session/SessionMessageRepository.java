package org.monarch.langchain.claw.session;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionMessageRepository extends JpaRepository<SessionMessageEntity, Long> {
    List<SessionMessageEntity> findBySessionIdOrderByMessageOrderAsc(String sessionId);

    long countBySessionId(String sessionId);

    SessionMessageEntity findTopBySessionIdOrderByMessageOrderDesc(String sessionId);
}
