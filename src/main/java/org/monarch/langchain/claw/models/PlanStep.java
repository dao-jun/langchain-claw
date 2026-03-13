package org.monarch.langchain.claw.models;

import java.util.Map;

public class PlanStep {
    public String description;             // 步骤描述
    public String executorType;            // 执行器类型（如 "calculator", "weather"）
    public Map<String, Object> parameters; // 参数
    public String result;                  // 执行结果
    public String error;                   // 错误信息
}
