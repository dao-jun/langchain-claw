package org.monarch.langchain.claw.config;

import java.util.List;
import java.util.Optional;
import org.monarch.langchain.claw.agent.AgentType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserModelConfigRepository extends JpaRepository<UserModelConfigEntity, Long> {
    List<UserModelConfigEntity> findByUserId(String userId);

    Optional<UserModelConfigEntity> findByUserIdAndAgentType(String userId, AgentType agentType);
}
