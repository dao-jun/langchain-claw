# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build and Run Commands

```bash
# Compile the project
mvn compile

# Package the project
mvn package

# Run the main application (requires OPENAI_API_KEY environment variable)
mvn exec:java -Dexec.mainClass="org.monarch.langchain.claw.Main"

# Clean build artifacts
mvn clean
```

Note: Despite the environment variable name `OPENAI_API_KEY`, the project uses Zhipu AI as the LLM provider.

## Architecture Overview

This is a multi-agent orchestration system built on LangChain4j that processes user requests through a Plan-Review-Execute workflow.

### Core Components

**AgentOrchestrator** (`AgentOrchestrator.java`)
- Central coordinator managing user sessions and agent dispatch
- Routes messages to appropriate agents based on `SessionState`
- Maintains per-user sessions with short-term (ChatMemory) and long-term (EmbeddingStore) memory

**Agent Flow**
```
User Input → PlanAgent → ReviewAgent → ExecutorAgent → Complete
                ↓            ↓              ↓
          PLAN_GENERATED  PLAN_REVIEWED  EXECUTING
```

**Three Specialized Agents** (all implement `Agent` interface):
- **PlanAgent**: Analyzes user requests and generates structured execution plans
- **ReviewAgent**: Validates plans for safety/feasibility (can ACCEPT/REJECT/ASK_USER)
- **ExecutorAgent**: Executes plan steps using available tools

### Key Patterns

**AiServices Pattern**: Agent services are interfaces annotated with `@SystemMessage` and `@UserMessage`, instantiated via `AiServices.create()` or `AiServices.builder()`. LangChain4j handles LLM interaction and response parsing.

**Tools**: Classes in `tools/` use `@Tool` annotations. Tools are passed to ExecutorAgent via `AiServices.builder().tools(tools)`.

**Session State Machine**: `SessionState` enum drives the orchestration flow. State transitions happen within agent `execute()` methods.

**Memory Architecture**:
- Short-term: `MessageWindowChatMemory` (last 20 messages)
- Long-term: `InMemoryEmbeddingStore` with `AllMiniLmL6V2EmbeddingModel` embeddings

### Model Classes

- `Plan`: Contains list of `PlanStep` and rationale
- `PlanStep`: description, executorType, parameters, result, error
- `SessionState`: INIT → PLAN_GENERATED → PLAN_REVIEWED → EXECUTING → COMPLETED

## Adding New Tools

1. Create a class in `tools/` with methods annotated with `@Tool("description")`
2. Register in `AgentOrchestrator` constructor by passing to `ExecutorAgent` constructor
