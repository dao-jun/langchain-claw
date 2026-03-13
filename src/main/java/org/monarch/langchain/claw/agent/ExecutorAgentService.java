package org.monarch.langchain.claw.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import org.monarch.langchain.claw.models.PlanStep;

import java.util.Map;

public interface ExecutorAgentService {
    @SystemMessage("你是一个任务执行专家。根据给定的步骤描述和执行器类型，调用相应工具完成任务。请返回执行结果。")
    @UserMessage("步骤：{{step}}，当前上下文：{{context}}")
    String executeStep(@UserMessage("step") PlanStep step, @UserMessage("context") Map<String, Object> context);
}
