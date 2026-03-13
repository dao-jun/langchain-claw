package org.monarch.langchain.claw.common;

public enum SessionState {
    INIT,
    PLAN_GENERATED,
    PLAN_REVIEWED,
    WAITING_FOR_USER,
    EXECUTING,
    COMPLETED,
    FAILED
}
