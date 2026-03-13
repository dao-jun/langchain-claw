package org.monarch.langchain.claw.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.monarch.langchain.claw.agent.AgentType;

public class ModelConfigRequest {

    @NotNull
    private AgentType agentType;
    @NotBlank
    private String provider;
    @NotBlank
    private String modelName;
    private Double temperature;
    private Integer maxTokens;

    public AgentType getAgentType() {
        return agentType;
    }

    public void setAgentType(AgentType agentType) {
        this.agentType = agentType;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public Double getTemperature() {
        return temperature;
    }

    public void setTemperature(Double temperature) {
        this.temperature = temperature;
    }

    public Integer getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
    }
}
