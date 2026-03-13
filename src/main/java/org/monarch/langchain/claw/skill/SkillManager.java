package org.monarch.langchain.claw.skill;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.monarch.langchain.claw.api.dto.InstallSkillRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class SkillManager {

    private final List<SkillDefinition> builtinSkills;
    private final UserSkillRepository userSkillRepository;

    public SkillManager(BuiltinSkillLoader builtinSkillLoader, UserSkillRepository userSkillRepository) {
        this.builtinSkills = builtinSkillLoader.load();
        this.userSkillRepository = userSkillRepository;
    }

    @Transactional(readOnly = true)
    public List<SkillDefinition> getAvailableSkills(String userId) {
        List<SkillDefinition> allSkills = new ArrayList<>(builtinSkills);
        allSkills.addAll(userSkillRepository.findByUserIdAndEnabledTrue(userId).stream().map(this::toModel).toList());
        return allSkills;
    }

    public SkillDefinition install(String userId, InstallSkillRequest request) {
        UserSkillEntity entity = new UserSkillEntity();
        entity.setUserId(userId);
        entity.setSkillName(request.getName());
        entity.setSkillVersion(request.getVersion());
        entity.setDescription(request.getDescription());
        entity.setExecutorType(request.getExecutorType());
        entity.setPromptTemplate(request.getPrompt());
        entity.setToolNames(String.join(",", request.getTools()));
        return toModel(userSkillRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public boolean hasSkill(String userId, String skillName) {
        return getAvailableSkills(userId).stream().anyMatch(skill -> skill.getName().equalsIgnoreCase(skillName) && skill.isEnabled());
    }

    private SkillDefinition toModel(UserSkillEntity entity) {
        SkillDefinition definition = new SkillDefinition();
        definition.setName(entity.getSkillName());
        definition.setVersion(entity.getSkillVersion());
        definition.setDescription(entity.getDescription());
        definition.setExecutorType(entity.getExecutorType());
        definition.setPrompt(entity.getPromptTemplate());
        definition.setTools(entity.getToolNames().isBlank() ? List.of() : Arrays.stream(entity.getToolNames().split(",")).map(String::trim).toList());
        definition.setScope(SkillScope.USER);
        definition.setUserId(entity.getUserId());
        definition.setEnabled(entity.isEnabled());
        return definition;
    }
}
