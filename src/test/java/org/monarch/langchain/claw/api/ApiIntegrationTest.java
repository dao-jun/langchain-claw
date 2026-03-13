package org.monarch.langchain.claw.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

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

    @Test
    void shouldExposeStructuredChainPlanInChatResponse() throws Exception {
        mockMvc.perform(post("/api/v1/chat")
                .header("X-User-Id", "api-user-3")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "message": "请查询北京和上海天气，并计算平均温度"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("COMPLETED"))
            .andExpect(jsonPath("$.plan.steps[0].stepId").value("weather-1"))
            .andExpect(jsonPath("$.plan.steps[2].dependsOn[0]").value("weather-1"))
            .andExpect(jsonPath("$.plan.steps[2].output.numericValue").value(25.0))
            .andExpect(jsonPath("$.stepOutputs[2]").value(containsString("= 25")));
    }

    @Test
    void shouldReplayCachedChatResponseForDuplicateRequestId() throws Exception {
        MvcResult firstResult = mockMvc.perform(post("/api/v1/chat")
                .header("X-User-Id", "api-user-4")
                .header("X-Request-Id", "req-idempotent-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "message": "请计算 8*(2+1)"
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn();

        MvcResult duplicateResult = mockMvc.perform(post("/api/v1/chat")
                .header("X-User-Id", "api-user-4")
                .header("X-Request-Id", "req-idempotent-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "message": "请计算 8*(2+1)"
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode firstJson = objectMapper.readTree(firstResult.getResponse().getContentAsString());
        JsonNode duplicateJson = objectMapper.readTree(duplicateResult.getResponse().getContentAsString());
        assertThat(duplicateJson).isEqualTo(firstJson);
        assertThat(duplicateJson.get("traceId").asText()).isEqualTo(firstJson.get("traceId").asText());
        assertThat(duplicateJson.get("sessionId").asText()).isEqualTo(firstJson.get("sessionId").asText());
    }

    @Test
    void shouldRejectDifferentPayloadForSameRequestId() throws Exception {
        mockMvc.perform(post("/api/v1/chat")
                .header("X-User-Id", "api-user-5")
                .header("X-Request-Id", "req-idempotent-002")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "message": "请计算 8*(2+1)"
                    }
                    """))
            .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/chat")
                .header("X-User-Id", "api-user-5")
                .header("X-Request-Id", "req-idempotent-002")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "message": "请计算 9*(2+1)"
                    }
                    """))
            .andExpect(status().isConflict());
    }
}
