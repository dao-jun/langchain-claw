package org.monarch.langchain.claw.config;

import java.util.Arrays;
import java.util.List;
import org.monarch.langchain.claw.agent.AgentType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class ModelConfigService {

    private final UserModelConfigRepository repository;

    public ModelConfigService(UserModelConfigRepository repository) {
        this.repository = repository;
    }

    public ModelSelection save(String userId, ModelSelection selection) {
        UserModelConfigEntity entity = repository.findByUserIdAndAgentType(userId, selection.getAgentType())
            .orElseGet(UserModelConfigEntity::new);
        entity.setUserId(userId);
        entity.setAgentType(selection.getAgentType());
        entity.setProvider(selection.getProvider());
        entity.setModelName(selection.getModelName());
        entity.setTemperature(selection.getTemperature());
        entity.setMaxTokens(selection.getMaxTokens());
        return toModel(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public ModelSelection resolve(String userId, AgentType agentType) {
        return repository.findByUserIdAndAgentType(userId, agentType)
            .map(this::toModel)
            .orElseGet(() -> defaultSelection(agentType));
    }

    @Transactional(readOnly = true)
    public List<ModelSelection> resolveAll(String userId) {
        return Arrays.stream(AgentType.values()).map(agentType -> resolve(userId, agentType)).toList();
    }

    private ModelSelection defaultSelection(AgentType agentType) {
        ModelSelection selection = new ModelSelection();
        selection.setAgentType(agentType);
        selection.setProvider("rule-based");
        selection.setModelName("deterministic-" + agentType.name().toLowerCase());
        selection.setTemperature(0.0d);
        selection.setMaxTokens(2048);
        return selection;
    }

    private ModelSelection toModel(UserModelConfigEntity entity) {
        ModelSelection selection = new ModelSelection();
        selection.setAgentType(entity.getAgentType());
        selection.setProvider(entity.getProvider());
        selection.setModelName(entity.getModelName());
        selection.setTemperature(entity.getTemperature());
        selection.setMaxTokens(entity.getMaxTokens());
        return selection;
    }
}
