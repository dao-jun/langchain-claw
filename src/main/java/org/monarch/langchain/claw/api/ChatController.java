package org.monarch.langchain.claw.api;

import java.time.Duration;
import java.util.UUID;
import jakarta.validation.Valid;
import org.monarch.langchain.claw.api.dto.ChatRequest;
import org.monarch.langchain.claw.api.dto.ChatResponse;
import org.monarch.langchain.claw.api.idempotency.ChatRequestIdempotencyService;
import org.monarch.langchain.claw.core.AgentOrchestrator;
import org.monarch.langchain.claw.core.SessionConcurrencyControl;
import org.monarch.langchain.claw.config.ModelConfigService;
import org.monarch.langchain.claw.session.SessionManager;
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
    private final SessionManager sessionManager;
    private final ChatRequestIdempotencyService chatRequestIdempotencyService;

    public ChatController(SessionConcurrencyControl concurrencyControl,
                          AgentOrchestrator orchestrator,
                          SkillManager skillManager,
                          ModelConfigService modelConfigService,
                          SessionManager sessionManager,
                          ChatRequestIdempotencyService chatRequestIdempotencyService) {
        this.concurrencyControl = concurrencyControl;
        this.orchestrator = orchestrator;
        this.skillManager = skillManager;
        this.modelConfigService = modelConfigService;
        this.sessionManager = sessionManager;
        this.chatRequestIdempotencyService = chatRequestIdempotencyService;
    }

    @PostMapping
    public ChatResponse chat(@RequestHeader("X-User-Id") String userId,
                             @RequestHeader(value = "X-Request-Id", required = false) String requestId,
                             @Valid @RequestBody ChatRequest request) {
        String effectiveRequestId = (requestId == null || requestId.isBlank()) ? UUID.randomUUID().toString() : requestId;
        String requestFingerprint = chatRequestIdempotencyService.createFingerprint(request.getSessionId(), request.getMessage());
        return chatRequestIdempotencyService.execute(
            userId,
            effectiveRequestId,
            requestFingerprint,
            () -> {
                String sessionId = sessionManager.resolveSessionId(request.getSessionId());
                String traceId = UUID.randomUUID().toString();
                return concurrencyControl.executeInSession(sessionId,
                    () -> {
                        var result = orchestrator.process(userId, sessionId, request.getMessage(), effectiveRequestId, traceId);
                        ChatResponse response = new ChatResponse();
                        response.setSessionId(result.getSessionId());
                        response.setState(result.getNextState());
                        response.setMessage(result.getMessage());
                        response.setPlan(result.getPlan());
                        response.setRequestId(result.getRequestId());
                        response.setTraceId(result.getTraceId());
                        response.setStepOutputs(result.getStepOutputs());
                        response.setActiveSkills(skillManager.getAvailableSkills(userId));
                        response.setActiveModels(modelConfigService.resolveAll(userId));
                        return response;
                    }, Duration.ofSeconds(30)).join();
            });
    }
}
