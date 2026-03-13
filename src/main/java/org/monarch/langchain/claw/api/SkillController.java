package org.monarch.langchain.claw.api;

import jakarta.validation.Valid;
import java.util.List;
import org.monarch.langchain.claw.api.dto.InstallSkillRequest;
import org.monarch.langchain.claw.skill.SkillDefinition;
import org.monarch.langchain.claw.skill.SkillManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {

    private final SkillManager skillManager;

    public SkillController(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    @GetMapping
    public List<SkillDefinition> list(@RequestHeader("X-User-Id") String userId) {
        return skillManager.getAvailableSkills(userId);
    }

    @PostMapping
    public SkillDefinition install(@RequestHeader("X-User-Id") String userId, @Valid @RequestBody InstallSkillRequest request) {
        return skillManager.install(userId, request);
    }
}
