package org.monarch.langchain.claw.agent;

public interface Agent {
    void execute(UserSession session, String userMessage) throws Exception;
}
