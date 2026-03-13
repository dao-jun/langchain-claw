package org.monarch.langchain.claw.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;

public class InstallSkillRequest {

    @NotBlank
    private String name;
    @NotBlank
    private String version;
    @NotBlank
    private String description;
    @NotBlank
    private String executorType;
    private String prompt;
    private List<String> tools = new ArrayList<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getExecutorType() {
        return executorType;
    }

    public void setExecutorType(String executorType) {
        this.executorType = executorType;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public List<String> getTools() {
        return tools;
    }

    public void setTools(List<String> tools) {
        this.tools = tools;
    }
}
