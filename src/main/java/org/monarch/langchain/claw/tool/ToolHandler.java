package org.monarch.langchain.claw.tool;

import java.util.Map;

public interface ToolHandler {
    String name();

    String execute(Map<String, Object> input);
}
