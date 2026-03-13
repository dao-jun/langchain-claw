package org.monarch.langchain.claw.api;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldInstallUserSkillAndExposeModelConfigs() throws Exception {
        mockMvc.perform(post("/api/v1/skills")
                .header("X-User-Id", "api-user")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "custom-helper",
                      "version": "1.0.0",
                      "description": "自定义用户技能",
                      "executorType": "conversation",
                      "prompt": "请用更正式的语气回答",
                      "tools": ["conversation"]
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.name").value("custom-helper"));

        mockMvc.perform(post("/api/v1/model-configs")
                .header("X-User-Id", "api-user")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "agentType": "PLAN",
                      "provider": "zhipu",
                      "modelName": "glm-4-plus",
                      "temperature": 0.1,
                      "maxTokens": 4096
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.provider").value("zhipu"));

        mockMvc.perform(get("/api/v1/model-configs")
                .header("X-User-Id", "api-user"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].agentType").exists());
    }

    @Test
    void shouldExposeHealthAndChatFlow() throws Exception {
        mockMvc.perform(get("/api/v1/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(post("/api/v1/chat")
                .header("X-User-Id", "api-user-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "message": "请计算 8*(2+1)"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("COMPLETED"))
            .andExpect(jsonPath("$.message").value(containsString("24")));
    }
}
