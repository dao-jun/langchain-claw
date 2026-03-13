package org.monarch.langchain.claw;

public class Main {
    public static void main(String[] args) {
        String apiKey = System.getenv("OPENAI_API_KEY");
        AgentOrchestrator orchestrator = new AgentOrchestrator(apiKey);

        // 用户1
        String response1 = orchestrator.handleUserMessage("user1", "帮我计算 15*3，然后查询北京的天气");
        System.out.println("Assistant: " + response1);

        // 模拟多轮交互（实际应用中应由前端循环调用）
        // 假设用户确认计划
        String response2 = orchestrator.handleUserMessage("user1", "计划看起来不错，执行吧");
        System.out.println("Assistant: " + response2);
    }
}
