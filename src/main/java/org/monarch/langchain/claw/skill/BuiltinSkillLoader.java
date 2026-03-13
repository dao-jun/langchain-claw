package org.monarch.langchain.claw.skill;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

@Component
public class BuiltinSkillLoader {

    public List<SkillDefinition> load() {
        try {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            Resource[] resources = resolver.getResources("classpath*:skills/builtin/*/skill.yaml");
            List<SkillDefinition> skills = new ArrayList<>();
            Yaml yaml = new Yaml();
            for (Resource resource : resources) {
                @SuppressWarnings("unchecked")
                Map<String, Object> payload = yaml.load(resource.getInputStream());
                SkillDefinition definition = new SkillDefinition();
                definition.setName((String) payload.get("name"));
                definition.setVersion(String.valueOf(payload.getOrDefault("version", "1.0.0")));
                definition.setDescription((String) payload.getOrDefault("description", ""));
                definition.setExecutorType((String) payload.getOrDefault("executorType", definition.getName()));
                definition.setScope(SkillScope.BUILTIN);
                Object tools = payload.get("tools");
                if (tools instanceof List<?> list) {
                    definition.setTools(list.stream().map(String::valueOf).toList());
                }
                Object promptFile = payload.get("promptFile");
                if (promptFile != null) {
                    Resource promptResource = resource.createRelative(String.valueOf(promptFile));
                    definition.setPrompt(new String(promptResource.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                }
                Object prompt = payload.get("prompt");
                if (prompt != null) {
                    definition.setPrompt(String.valueOf(prompt));
                }
                skills.add(definition);
            }
            return skills;
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load builtin skills", ex);
        }
    }
}
