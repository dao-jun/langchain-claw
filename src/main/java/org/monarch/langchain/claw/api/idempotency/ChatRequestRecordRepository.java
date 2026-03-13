package org.monarch.langchain.claw.api.idempotency;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChatRequestRecordRepository extends JpaRepository<ChatRequestRecordEntity, Long> {

    Optional<ChatRequestRecordEntity> findByUserIdAndRequestId(String userId, String requestId);
}
