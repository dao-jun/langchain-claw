package org.monarch.langchain.claw.api;

import java.time.Duration;
import jakarta.validation.Valid;
import org.monarch.langchain.claw.api.dto.ChatRequest;
import org.monarch.langchain.claw.api.dto.ChatResponse;
import org.monarch.langchain.claw.core.AgentOrchestrator;
import org.monarch.langchain.claw.core.SessionConcurrencyControl;
import org.monarch.langchain.claw.config.ModelConfigService;
import org.monarch.langchain.claw.skill.SkillManager;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final SessionConcurrencyControl concurrencyControl;
    private final AgentOrchestrator orchestrator;
    private final SkillManager skillManager;
    private final ModelConfigService modelConfigService;

    public ChatController(SessionConcurrencyControl concurrencyControl,
                          AgentOrchestrator orchestrator,
                          SkillManager skillManager,
                          ModelConfigService modelConfigService) {
        this.concurrencyControl = concurrencyControl;
        this.orchestrator = orchestrator;
        this.skillManager = skillManager;
        this.modelConfigService = modelConfigService;
    }

    @PostMapping
    public ChatResponse chat(@RequestHeader("X-User-Id") String userId,
                             @Valid @RequestBody ChatRequest request) {
        String sessionId = request.getSessionId() == null || request.getSessionId().isBlank() ? userId + "-new" : request.getSessionId();
        return concurrencyControl.executeInSession(sessionId,
            () -> {
                var result = orchestrator.process(userId, request.getSessionId(), request.getMessage());
                ChatResponse response = new ChatResponse();
                response.setSessionId(result.getSessionId());
                response.setState(result.getNextState());
                response.setMessage(result.getMessage());
                response.setPlan(result.getPlan());
                response.setStepOutputs(result.getStepOutputs());
                response.setActiveSkills(skillManager.getAvailableSkills(userId));
                response.setActiveModels(modelConfigService.resolveAll(userId));
                return response;
            }, Duration.ofSeconds(30)).join();
    }
}
