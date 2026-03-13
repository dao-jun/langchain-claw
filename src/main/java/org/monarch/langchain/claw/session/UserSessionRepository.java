package org.monarch.langchain.claw.session;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSessionRepository extends JpaRepository<UserSessionEntity, Long> {
    Optional<UserSessionEntity> findBySessionIdAndUserId(String sessionId, String userId);
}
