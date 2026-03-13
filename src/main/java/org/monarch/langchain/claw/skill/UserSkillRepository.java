package org.monarch.langchain.claw.skill;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSkillRepository extends JpaRepository<UserSkillEntity, Long> {
    List<UserSkillEntity> findByUserIdAndEnabledTrue(String userId);
}
