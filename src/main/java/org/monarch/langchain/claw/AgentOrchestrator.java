package org.monarch.langchain.claw;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.zhipu.ZhipuAiChatModel;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import org.monarch.langchain.claw.agent.ExecutorAgent;
import org.monarch.langchain.claw.agent.PlanAgent;
import org.monarch.langchain.claw.agent.ReviewAgent;
import org.monarch.langchain.claw.agent.UserSession;
import org.monarch.langchain.claw.models.SessionState;
import org.monarch.langchain.claw.tools.CalculatorTool;
import org.monarch.langchain.claw.tools.WeatherTool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class AgentOrchestrator {
    private final Map<String, UserSession> sessions = new ConcurrentHashMap<>();
    private final ChatLanguageModel model;
    private final EmbeddingModel embeddingModel;
    private final PlanAgent planAgent;
    private final ReviewAgent reviewAgent;
    private final ExecutorAgent executorAgent;

    public AgentOrchestrator(String openAiApiKey) {
        this.model = ZhipuAiChatModel.builder().apiKey(openAiApiKey).build();
        this.embeddingModel = new AllMiniLmL6V2EmbeddingModel(); // 本地嵌入模型

        this.planAgent = new PlanAgent(model);
        this.reviewAgent = new ReviewAgent(model);
        this.executorAgent = new ExecutorAgent(model, new CalculatorTool(), new WeatherTool());
    }

    // 处理用户消息（入口）
    public String handleUserMessage(String userId, String message) {
        UserSession session = sessions.computeIfAbsent(userId, k -> new UserSession(userId));

        // 1. 将用户消息加入短期记忆
        session.shortTermMemory.add(message);

        // 2. 根据当前状态选择执行的 Agent
        try {
            if (session.state == SessionState.INIT) {
                // 初始状态 -> PlanAgent
                planAgent.execute(session, message);
                return "计划已生成，等待审核...";
            } else if (session.state == SessionState.PLAN_GENERATED) {
                // 计划已生成 -> ReviewAgent
                reviewAgent.execute(session, message);
                if (session.state == SessionState.PLAN_REVIEWED) {
                    return "计划审核通过，开始执行。";
                } else if (session.state == SessionState.WAITING_FOR_USER) {
                    return "需要您的确认：" + extractAskMessage(session);
                } else {
                    return "计划被拒绝，请重新描述需求。";
                }
            } else if (session.state == SessionState.PLAN_REVIEWED || session.state == SessionState.EXECUTING) {
                // 执行步骤
                executorAgent.execute(session, message);
                if (session.state == SessionState.COMPLETED) {
                    return "所有步骤执行完成！";
                } else {
                    return "步骤执行中，当前进度：" + session.currentStepIndex + "/" + session.currentPlan.steps.size();
                }
            } else if (session.state == SessionState.WAITING_FOR_USER) {
                // 用户刚刚输入了确认信息
                // 这里简化处理：将用户消息作为确认，继续执行之前的流程
                if (session.onUserInputNeeded != null) {
                    session.onUserInputNeeded.run();
                    session.onUserInputNeeded = null;
                }
                // 根据之前的 pending 状态，跳转到相应 agent
                // 此处简单回到 PLAN_GENERATED 让 ReviewAgent 重新审核（因为用户可能提供了额外信息）
                session.state = SessionState.PLAN_GENERATED;
                // 递归调用自身，但避免栈溢出，可以用循环
                return handleUserMessage(userId, message); // 重新进入流程
            } else {
                return "未知状态";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "处理异常：" + e.getMessage();
        }
    }

    private String extractAskMessage(UserSession session) {
        // 简单提取需要询问用户的信息（可以从短期记忆中获取最近的一条系统消息）
        return "请确认计划是否可行。";
    }

    // 模拟长期记忆存储（在每次对话后可将重要信息存入向量存储）
    public void storeLongTermMemory(UserSession session, String text) {
        // 使用 EmbeddingStoreIngestor 将文本分段并存入向量存储
        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingStore(session.longTermMemory)
                .embeddingModel(embeddingModel)
                .build();
        ingestor.ingest(text);
    }

    // 检索长期记忆
    public List<String> retrieveLongTermMemory(UserSession session, String query, int maxResults) {
        // 实际实现需要将查询转为向量并检索，简化返回空
        return List.of();
    }
}
