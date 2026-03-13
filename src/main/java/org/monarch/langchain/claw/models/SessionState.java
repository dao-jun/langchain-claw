package org.monarch.langchain.claw.models;

public enum SessionState {
    INIT,               // 初始，等待用户输入
    PLAN_GENERATED,     // 计划已生成
    PLAN_REVIEWED,      // 计划审核通过
    EXECUTING,          // 正在执行步骤
    WAITING_FOR_USER,   // 等待用户确认
    COMPLETED           // 任务完成
}
