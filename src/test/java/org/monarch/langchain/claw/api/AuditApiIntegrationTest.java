package org.monarch.langchain.claw.api;

import static org.assertj.core.api.Assertions.assertThat;
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
class AuditApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldAuditToolCallsAndExposeRequestTraceMetadata() throws Exception {
        MvcResult chatResult = mockMvc.perform(post("/api/v1/chat")
                .header("X-User-Id", "audit-user")
                .header("X-Request-Id", "req-audit-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "message": "请计算 8*(2+1)"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.state").value("COMPLETED"))
            .andExpect(jsonPath("$.requestId").value("req-audit-001"))
            .andExpect(jsonPath("$.traceId").isNotEmpty())
            .andReturn();

        JsonNode chatJson = objectMapper.readTree(chatResult.getResponse().getContentAsString());
        String sessionId = chatJson.get("sessionId").asText();
        String traceId = chatJson.get("traceId").asText();

        MvcResult auditResult = mockMvc.perform(get("/api/v1/audit/tool-calls")
                .header("X-User-Id", "audit-user")
                .param("sessionId", sessionId)
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].toolName").value("calculator"))
            .andExpect(jsonPath("$[0].success").value(true))
            .andExpect(jsonPath("$[0].requestId").value("req-audit-001"))
            .andExpect(jsonPath("$[0].traceId").value(traceId))
            .andReturn();

        JsonNode auditJson = objectMapper.readTree(auditResult.getResponse().getContentAsString());
        assertThat(auditJson).hasSize(1);
        assertThat(auditJson.get(0).get("toolInput").asText()).contains("8*(2+1)");
        assertThat(auditJson.get(0).get("toolOutput").asText()).contains("24");
    }

    @Test
    void shouldRespectAuditLimitForMultiStepRequests() throws Exception {
        MvcResult chatResult = mockMvc.perform(post("/api/v1/chat")
                .header("X-User-Id", "audit-user-2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "message": "请告诉我北京和上海的天气"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.stepOutputs[0]").exists())
            .andReturn();

        String sessionId = objectMapper.readTree(chatResult.getResponse().getContentAsString()).get("sessionId").asText();

        MvcResult auditResult = mockMvc.perform(get("/api/v1/audit/tool-calls")
                .header("X-User-Id", "audit-user-2")
                .param("sessionId", sessionId)
                .param("limit", "1"))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode auditJson = objectMapper.readTree(auditResult.getResponse().getContentAsString());
        assertThat(auditJson).hasSize(1);
        assertThat(auditJson.get(0).get("toolName").asText()).isEqualTo("weather");
    }

    @Test
    void shouldAvoidDuplicateToolCallsForIdempotentReplay() throws Exception {
        mockMvc.perform(post("/api/v1/chat")
                .header("X-User-Id", "audit-user-3")
                .header("X-Request-Id", "req-audit-idempotent-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "message": "请计算 8*(2+1)"
                    }
                    """))
            .andExpect(status().isOk());

        MvcResult duplicateChatResult = mockMvc.perform(post("/api/v1/chat")
                .header("X-User-Id", "audit-user-3")
                .header("X-Request-Id", "req-audit-idempotent-001")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "message": "请计算 8*(2+1)"
                    }
                    """))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode chatJson = objectMapper.readTree(duplicateChatResult.getResponse().getContentAsString());
        String sessionId = chatJson.get("sessionId").asText();

        MvcResult auditResult = mockMvc.perform(get("/api/v1/audit/tool-calls")
                .header("X-User-Id", "audit-user-3")
                .param("sessionId", sessionId)
                .param("limit", "10"))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode auditJson = objectMapper.readTree(auditResult.getResponse().getContentAsString());
        assertThat(auditJson).hasSize(1);
        assertThat(auditJson.get(0).get("requestId").asText()).isEqualTo("req-audit-idempotent-001");
    }
}
