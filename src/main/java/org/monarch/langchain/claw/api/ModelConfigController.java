package org.monarch.langchain.claw.api;

import jakarta.validation.Valid;
import java.util.List;
import org.monarch.langchain.claw.api.dto.ModelConfigRequest;
import org.monarch.langchain.claw.config.ModelConfigService;
import org.monarch.langchain.claw.config.ModelSelection;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/model-configs")
public class ModelConfigController {

    private final ModelConfigService modelConfigService;

    public ModelConfigController(ModelConfigService modelConfigService) {
        this.modelConfigService = modelConfigService;
    }

    @GetMapping
    public List<ModelSelection> list(@RequestHeader("X-User-Id") String userId) {
        return modelConfigService.resolveAll(userId);
    }

    @PostMapping
    public ModelSelection save(@RequestHeader("X-User-Id") String userId, @Valid @RequestBody ModelConfigRequest request) {
        ModelSelection selection = new ModelSelection();
        selection.setAgentType(request.getAgentType());
        selection.setProvider(request.getProvider());
        selection.setModelName(request.getModelName());
        selection.setTemperature(request.getTemperature());
        selection.setMaxTokens(request.getMaxTokens());
        return modelConfigService.save(userId, selection);
    }
}
