package org.monarch.langchain.claw.audit;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolCallAuditRepository extends JpaRepository<ToolCallAuditEntity, Long> {

    List<ToolCallAuditEntity> findByUserIdAndSessionIdOrderByCreatedAtDesc(String userId, String sessionId, Pageable pageable);
}
