package org.monarch.langchain.claw.skill;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "user_skills")
public class UserSkillEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "skill_name", nullable = false)
    private String skillName;

    @Column(name = "skill_version", nullable = false)
    private String skillVersion;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "executor_type", nullable = false)
    private String executorType;

    @Column(name = "prompt_template", columnDefinition = "TEXT")
    private String promptTemplate;

    @Column(name = "tool_names", nullable = false)
    private String toolNames;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "installed_at", nullable = false)
    private Instant installedAt;

    @PrePersist
    void prePersist() {
        installedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getSkillName() {
        return skillName;
    }

    public void setSkillName(String skillName) {
        this.skillName = skillName;
    }

    public String getSkillVersion() {
        return skillVersion;
    }

    public void setSkillVersion(String skillVersion) {
        this.skillVersion = skillVersion;
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

    public String getPromptTemplate() {
        return promptTemplate;
    }

    public void setPromptTemplate(String promptTemplate) {
        this.promptTemplate = promptTemplate;
    }

    public String getToolNames() {
        return toolNames;
    }

    public void setToolNames(String toolNames) {
        this.toolNames = toolNames;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
