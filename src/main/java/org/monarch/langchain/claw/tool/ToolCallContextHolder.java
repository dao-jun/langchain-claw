package org.monarch.langchain.claw.tool;

import org.monarch.langchain.claw.audit.ToolCallContext;
import org.springframework.stereotype.Component;

@Component
public class ToolCallContextHolder {

    private static final ToolCallContext DEFAULT_CONTEXT = new ToolCallContext("system", "system", "system", "system");

    private final ThreadLocal<ToolCallContext> current = new ThreadLocal<>();

    public Scope withContext(ToolCallContext context) {
        current.set(context);
        return () -> current.remove();
    }

    public ToolCallContext currentOrDefault() {
        ToolCallContext context = current.get();
        return context == null ? DEFAULT_CONTEXT : context;
    }

    @FunctionalInterface
    public interface Scope extends AutoCloseable {

        @Override
        void close();
    }
}
