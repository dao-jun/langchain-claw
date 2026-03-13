package org.monarch.langchain.claw.memory;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LongTermMemoryRepository extends JpaRepository<LongTermMemoryEntity, Long> {
    List<LongTermMemoryEntity> findTop20ByUserIdOrderByCreatedAtDesc(String userId);
}
