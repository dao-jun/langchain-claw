# Agent内核系统设计方案

> 版本: 1.1
> 日期: 2026-03-13
> 状态: 已批准，开始实施
> 更新: 根据设计审查反馈，增加了安全沙箱、Session并发控制、健康检查等关键功能

## 1. 背景

基于现有LangChain4j项目，构建一个生产就绪的多Agent协作系统内核。

### 1.1 目标
- 技术原型/MVP，验证架构可行性
- 支持10-100并发用户
- 完善工程实践

### 1.2 技术选型
- **框架**: LangChain4j
- **Skills形式**: 混合形式（Java类 + 配置文件）
- **数据库**: PostgreSQL
- **并发模型**: 虚拟线程（Java 21+）

### 1.3 现有代码问题分析

**工程实践问题：**
1. **内存管理**：使用InMemoryEmbeddingStore，重启丢失，不支持持久化
2. **并发问题**：`handleUserMessage`中递归调用可能导致栈溢出
3. **封装性差**：UserSession使用public字段，缺乏封装
4. **硬编码**：Tools在构造函数中硬编码，无法动态扩展
5. **无依赖注入**：所有组件手动创建，难以测试和扩展
6. **单一模型**：所有Agent共用一个ChatLanguageModel，无法按用户/角色配置
7. **无Skills系统**：完全没有skill概念

**缺失的核心功能：**
- Skills系统（内置+用户安装）
- Session持久化
- 用户级模型配置
- 多Executor Agent支持
- 虚拟线程并发模型

---

## 2. 核心架构

### 2.1 模块划分

```
org.monarch.langchain.claw/
├── api/                    # API层
│   ├── AgentController     # REST接口
│   └── dto/                # 数据传输对象
├── core/                   # 核心层
│   ├── AgentOrchestrator   # 总调度器
│   ├── SessionManager      # 会话管理
│   └── AgentRegistry       # Agent注册表
├── agent/                  # Agent实现
│   ├── Agent              # Agent接口
│   ├── PlanAgent          # 计划Agent
│   ├── ReviewAgent        # 审核Agent
│   ├── ExecutorAgent      # 执行Agent（可多实例）
│   └── AgentService       # LLM服务接口
├── skill/                  # Skills系统
│   ├── SkillManager       # Skill管理器
│   ├── SkillRegistry      # Skill注册表
│   ├── builtin/           # 内置Skills
│   └── user/              # 用户Skills（隔离）
├── memory/                 # 记忆系统
│   ├── MemoryManager      # 记忆管理器
│   ├── ShortTermMemory    # 短期记忆
│   └── LongTermMemory     # 长期记忆
├── tool/                   # 工具系统
│   ├── ToolRegistry       # 工具注册表
│   └── impl/              # 工具实现
├── session/                # 会话持久化
│   ├── SessionStore       # 会话存储接口
│   └── PostgresSessionStore # PostgreSQL实现
├── config/                 # 配置管理
│   ├── ModelConfig        # 模型配置
│   └── UserConfig         # 用户配置
└── common/                 # 公共组件
    ├── exception/         # 异常处理
    └── util/              # 工具类
```

### 2.2 核心组件关系

```
┌────────────────────────────────────────────────────────────┐
│                     AgentOrchestrator                       │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │SessionManager│  │ AgentRegistry│  │ SkillManager │     │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘     │
│         │                 │                  │              │
│         ▼                 ▼                  ▼              │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐     │
│  │ SessionStore │  │   Agents     │  │    Skills    │     │
│  │  (PostgreSQL)│  │Plan|Review|  │  │ builtin|user │     │
│  └──────────────┘  │   Executor   │  └──────────────┘     │
│                    └──────────────┘                        │
└────────────────────────────────────────────────────────────┘
                           │
                           ▼
              ┌─────────────────────────┐
              │     MemoryManager       │
              │  ShortTerm │ LongTerm   │
              └─────────────────────────┘
```

---

## 3. Agent系统设计

### 3.1 Agent接口重构

```java
public interface Agent {
    // === 基础信息 ===
    String getName();
    AgentType getType();
    String getDescription();

    // === 能力声明 ===
    AgentCapability getCapability();

    // === 生命周期 ===
    default void initialize(AgentConfig config) {}
    default void start() {}
    default void pause() {}
    default void resume() {}
    default void shutdown() {}

    // === 核心执行 ===
    CompletableFuture<AgentResult> execute(AgentContext context);

    // === 失败处理 ===
    default void onError(Exception e, AgentContext context) {}
    default CompletableFuture<AgentResult> retry(AgentContext context, int attempt) {
        return execute(context);
    }
}

public enum AgentType {
    PLAN,       // 计划Agent
    REVIEW,     // 审核Agent
    EXECUTOR    // 执行Agent（可多种类型）
}

public class AgentCapability {
    private List<String> requiredTools;      // 需要的工具
    private List<String> requiredSkills;     // 需要的技能
    private List<AgentType> dependencies;    // 依赖的其他Agent
    private int maxRetries;                  // 最大重试次数
    private long timeoutMs;                  // 超时时间
    private boolean supportsParallel;        // 是否支持并行
}

public class AgentContext {
    private String userId;
    private String sessionId;
    private String userMessage;
    private UserSession session;
    private List<Tool> availableTools;
    private List<Skill> activeSkills;
    private ChatLanguageModel model;
}

public class AgentResult {
    private boolean success;
    private String output;
    private SessionState nextState;
    private Map<String, Object> metadata;
}
```

### 3.2 多Executor Agent支持

根据Plan中的`executorType`动态选择Executor：

```java
public class ExecutorAgentRegistry {
    private final Map<String, ExecutorAgent> executors = new ConcurrentHashMap<>();

    public void register(String type, ExecutorAgent agent) {
        executors.put(type, agent);
    }

    public ExecutorAgent getExecutor(String type) {
        return executors.getOrDefault(type, defaultExecutor);
    }
}

// PlanStep扩展
public class PlanStep {
    public String description;
    public String executorType;  // "calculator", "weather", "web_search", etc.
    public List<String> requiredSkills;  // 该步骤需要的skills
    public Map<String, Object> parameters;
    public String result;
    public String error;
}
```

### 3.3 Agent工作流状态机

```
         ┌──────────────────────────────────────────┐
         │                                          │
         ▼                                          │
┌────────┐  generate  ┌─────────┐   review   ┌───────────┐
│  INIT  │───────────▶│  PLAN   │───────────▶│  REVIEW   │
└────────┘            │GENERATED│            │           │
                      └─────────┘            └───────────┘
                           │                       │
                     ┌─────┴─────┐            ┌────┴────┐
                     │           │            │         │
                 reject       ask_user    accept    reject
                     │           │            │         │
                     ▼           ▼            ▼         ▼
                ┌─────────┐ ┌─────────┐ ┌───────────┐ ┌────────┐
                │RE_PLAN  │ │WAITING  │ │ EXECUTING │ │FAILED  │
                └─────────┘ │FOR_USER │ └───────────┘ └────────┘
                     │      └─────────┘       │          │
                     │           │            │      retry?───────┐
                     │      user_response    │          │         │
                     │           │            │          ▼         │
                     │           │      ┌─────┴─────┐ ┌────────┐  │
                     │           │      │           │ │RECOVERY│  │
                     │           └─▶    │STEP_DONE │ │        │  │
                     │             └──────────┘ └────────┘  │
                     │                  │           │       │
                     │            more_steps?  can_retry?   │
                     │                  │           │       │
                     └──────────────────┴───────────┘       │
                                        │ all_done          │
                                        ▼                   │
                                  ┌───────────┐            │
                                  │ COMPLETED │◀───────────┘
                                  └───────────┘
```

### 3.4 用户级模型配置

```java
public class UserModelConfig {
    private String userId;
    private Map<AgentType, ModelSettings> agentModels;

    public static class ModelSettings {
        private String provider;     // "zhipu", "openai", "anthropic"
        private String modelName;    // "glm-4", "gpt-4", etc.
        private Double temperature;
        private Integer maxTokens;
    }
}

// 在SessionManager中应用
public ChatLanguageModel getModelForAgent(String userId, AgentType type) {
    UserModelConfig config = userConfigStore.get(userId);
    ModelSettings settings = config.getAgentModels().get(type);
    return modelFactory.create(settings);
}
```

### 3.5 Agent失败恢复机制

```java
public class AgentExecutionPolicy {
    private int maxRetries = 3;
    private long retryDelayMs = 1000;
    private RetryStrategy strategy = RetryStrategy.EXPONENTIAL_BACKOFF;
    private List<Class<? extends Exception>> retryableExceptions;

    public enum RetryStrategy {
        IMMEDIATE,              // 立即重试
        FIXED_DELAY,           // 固定延迟
        EXPONENTIAL_BACKOFF    // 指数退避
    }
}

public class AgentExecutor {
    private final AgentExecutionPolicy policy;

    public CompletableFuture<AgentResult> executeWithRetry(
            Agent agent, AgentContext context) {
        return CompletableFuture.supplyAsync(() -> {
            int attempt = 0;
            Exception lastError = null;
            while (attempt < policy.getMaxRetries()) {
                try {
                    return agent.execute(context).join();
                } catch (Exception e) {
                    lastError = e;
                    if (!isRetryable(e)) break;
                    attempt++;
                    sleep(calculateDelay(attempt));
                }
            }
            return AgentResult.failed(lastError);
        }, Executors.newVirtualThreadPerTaskExecutor());
    }
}
```

### 3.6 Agent间通信（事件总线）

```java
public class AgentEventBus {
    private final Map<String, List<AgentEventListener>> subscribers = new ConcurrentHashMap<>();

    // 事件类型
    public enum EventType {
        PLAN_GENERATED,
        PLAN_REVIEWED,
        STEP_STARTED,
        STEP_COMPLETED,
        STEP_FAILED,
        USER_INPUT_REQUIRED,
        SESSION_COMPLETED
    }

    public void subscribe(EventType type, AgentEventListener listener) {
        subscribers.computeIfAbsent(type.name(), k -> new CopyOnWriteArrayList<>())
                   .add(listener);
    }

    public void publish(AgentEvent event) {
        var listeners = subscribers.get(event.getType().name());
        if (listeners != null) {
            listeners.forEach(l -> l.onEvent(event));
        }
    }
}

public class AgentEvent {
    private EventType type;
    private String sessionId;
    private String userId;
    private String agentName;
    private Object payload;
    private Instant timestamp;
}
```

### 3.7 可观测性（指标与追踪）

```java
public class AgentMetrics {
    private final MeterRegistry meterRegistry;

    // 指标收集
    public void recordExecution(String agentName, Duration duration, boolean success) {
        Timer.builder("agent.execution.time")
            .tag("agent", agentName)
            .tag("success", String.valueOf(success))
            .register(meterRegistry)
            .record(duration);
    }

    // 分布式追踪
    public void traceExecution(Tracer tracer, AgentContext context, Runnable action) {
        Span span = tracer.spanBuilder("agent.execute")
            .setAttribute("agent.name", context.getAgentName())
            .setAttribute("session.id", context.getSessionId())
            .startSpan();
        try (Scope scope = span.makeCurrent()) {
            action.run();
        } finally {
            span.end();
        }
    }
}
```

---

## 4. Skills系统设计

### 4.1 Skill定义（混合形式：Java类 + 配置文件）

**目录结构：**
```
skills/
├── builtin/                          # 系统内置Skills
│   ├── weather/
│   │   ├── skill.yaml               # Skill配置
│   │   ├── WeatherSkill.java        # Skill实现（提供Tools）
│   │   └── prompt.md                # Prompt模板
│   ├── calculator/
│   │   ├── skill.yaml
│   │   └── CalculatorSkill.java
│   └── web_search/
│       ├── skill.yaml
│       └── WebSearchSkill.java
│
└── users/                            # 用户安装的Skills（隔离）
    ├── user_001/
    │   ├── custom_tool/
    │   │   ├── skill.yaml
    │   │   └── CustomTool.java
    │   └── installed_skill_x/
    └── user_002/
        └── ...
```

**skill.yaml 配置示例：**
```yaml
name: weather
version: 1.0.0
description: 天气查询技能，支持城市天气查询和预报

# Skill元数据
author: system
tags: [weather, query, builtin]

# 依赖声明
dependencies:
  tools: [WeatherTool]
  apis: []

# 执行配置
execution:
  timeout_ms: 30000
  max_retries: 2

# Prompt配置
prompt:
  system: prompt.md        # 引用外部prompt文件
  user_template: |
    用户请求查询{{city}}的天气信息

# 权限要求
permissions:
  network: true
  filesystem: false
```

**prompt.md 示例：**
```markdown
# Weather Skill

你是一个天气查询助手。你可以：
1. 查询指定城市的当前天气
2. 查询未来几天的天气预报

当用户询问天气时，使用WeatherTool工具获取信息。
```

### 4.2 Skill接口设计

```java
public interface Skill {
    // === 基础信息 ===
    String getName();
    String getVersion();
    String getDescription();
    SkillMetadata getMetadata();

    // === 工具提供 ===
    List<Tool> getTools();

    // === Prompt ===
    String getSystemPrompt();
    String getUserPromptTemplate();

    // === 生命周期 ===
    default void initialize(SkillContext context) {}
    default void shutdown() {}

    // === 执行钩子 ===
    default void beforeExecution(SkillExecutionContext context) {}
    default void afterExecution(SkillExecutionContext context, Object result) {}
    default void onError(SkillExecutionContext context, Exception e) {}
}

public class SkillMetadata {
    private String author;
    private List<String> tags;
    private List<String> requiredTools;
    private List<String> requiredApis;
    private SkillPermissions permissions;
}

public class SkillPermissions {
    private boolean networkAccess;
    private boolean filesystemAccess;
    private List<String> allowedPaths;
}
```

### 4.3 SkillManager（核心管理器）

```java
public class SkillManager {
    private final SkillRegistry builtinRegistry;   // 内置Skills
    private final Map<String, SkillRegistry> userRegistries; // 用户Skills（隔离）
    private final SkillLoader skillLoader;
    private final SkillClassLoader classLoader;

    // 加载内置Skills（启动时）
    public void loadBuiltinSkills(Path builtinPath) {
        skillLoader.loadSkills(builtinPath, builtinRegistry);
    }

    // 加载用户Skills（按需加载）
    public void loadUserSkills(String userId, Path userSkillPath) {
        SkillRegistry userRegistry = userRegistries.computeIfAbsent(
            userId,
            k -> new SkillRegistry()
        );
        // 使用隔离的ClassLoader加载用户Skill
        SkillClassLoader isolatedLoader = new IsolatedClassLoader(userSkillPath);
        skillLoader.loadSkills(userSkillPath, userRegistry, isolatedLoader);
    }

    // 获取用户可用的所有Skills（内置 + 用户安装）
    public List<Skill> getAvailableSkills(String userId) {
        List<Skill> skills = new ArrayList<>(builtinRegistry.getAll());
        SkillRegistry userRegistry = userRegistries.get(userId);
        if (userRegistry != null) {
            skills.addAll(userRegistry.getAll());
        }
        return skills;
    }

    // 根据名称获取Skill
    public Optional<Skill> getSkill(String userId, String skillName) {
        // 优先查找用户Skill，允许覆盖内置Skill
        SkillRegistry userRegistry = userRegistries.get(userId);
        if (userRegistry != null && userRegistry.contains(skillName)) {
            return Optional.of(userRegistry.get(skillName));
        }
        return Optional.ofNullable(builtinRegistry.get(skillName));
    }
}
```

### 4.4 远程Skill仓库支持

**支持的安装源：**
- GitHub Repository
- ClawHub（自定义Skill仓库）
- 本地文件系统
- HTTP/HTTPS URL

```java
public interface SkillRepository {
    String getName();
    SkillPackage download(String skillId, String version);
    SkillMetadata getMetadata(String skillId);
    List<SkillInfo> search(String query);
    boolean isAvailable(String skillId, String version);
}

// GitHub仓库
public class GitHubSkillRepository implements SkillRepository {
    private final String apiToken;

    @Override
    public SkillPackage download(String skillId, String version) {
        // skillId格式: "github:owner/repo/skill-name" 或 "owner/repo/skill-name"
        return downloadFromGithub(skillId, version);
    }

    @Override
    public List<SkillInfo> search(String query) {
        return searchGithubSkills(query);
    }
}

// ClawHub仓库
public class ClawHubRepository implements SkillRepository {
    private final String hubUrl = "https://api.clawhub.io";

    @Override
    public SkillPackage download(String skillId, String version) {
        String url = hubUrl + "/skills/" + skillId + "/" + version + "/download";
        return downloadPackage(url);
    }
}
```

### 4.5 Skill包格式

**标准Skill包结构（.clawskill 或 .zip）：**
```
weather-skill-1.0.0.clawskill (ZIP格式)
├── skill.yaml              # 必需：配置文件
├── prompt.md               # 必需：Prompt模板
├── WeatherSkill.jar        # 可选：编译后的Java类
├── lib/                    # 可选：依赖库
└── META-INF/
    ├── MANIFEST.MF
    └── signature.sig       # 可选：数字签名
```

### 4.6 Skill元数据持久化（PostgreSQL）

**数据库表设计：**
```sql
-- 用户安装的Skills元数据
CREATE TABLE user_skills (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    skill_name VARCHAR(128) NOT NULL,
    skill_version VARCHAR(32) NOT NULL,
    source_uri VARCHAR(512) NOT NULL,        -- 安装来源
    source_type VARCHAR(32) NOT NULL,        -- github/clawhub/local/http

    -- Skill元数据
    description TEXT,
    author VARCHAR(128),
    tags TEXT[],

    -- 配置
    config JSONB DEFAULT '{}',
    permissions JSONB DEFAULT '{}',

    -- 状态
    enabled BOOLEAN DEFAULT true,            -- 是否启用
    installed_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW(),
    last_used_at TIMESTAMP,

    -- 存储路径（相对路径）
    storage_path VARCHAR(512),

    UNIQUE(user_id, skill_name)
);

-- Skill依赖关系
CREATE TABLE skill_dependencies (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    skill_name VARCHAR(128) NOT NULL,
    depends_on VARCHAR(128) NOT NULL,
    version_constraint VARCHAR(64),          -- >=1.0.0, ^2.0.0等

    UNIQUE(user_id, skill_name, depends_on)
);

-- 索引
CREATE INDEX idx_user_skills_user ON user_skills(user_id);
CREATE INDEX idx_user_skills_enabled ON user_skills(user_id, enabled);
CREATE INDEX idx_skill_dependencies_skill ON skill_dependencies(user_id, skill_name);
```

### 4.7 Enable/Disable Skill功能

```java
public class SkillToggleService {

    // 启用Skill
    public void enableSkill(String userId, String skillName) {
        UserSkillMetadata metadata = metadataStore.findByUserAndName(userId, skillName);
        if (metadata == null) {
            throw new SkillNotFoundException(skillName);
        }

        // 1. 检查依赖是否都启用
        List<String> dependencies = dependencyStore.getDependencies(userId, skillName);
        List<String> disabledDeps = dependencies.stream()
            .filter(dep -> !isSkillEnabled(userId, dep))
            .toList();
        if (!disabledDeps.isEmpty()) {
            throw new SkillDependencyException(
                "Cannot enable: dependencies not enabled: " + disabledDeps);
        }

        // 2. 更新DB状态
        metadataStore.setEnabled(userId, skillName, true);

        // 3. 加载到内存
        if (!memoryCache.contains(userId, skillName)) {
            Skill skill = loadSkill(userId, metadata);
            memoryCache.put(userId, skillName, skill);
        }
    }

    // 禁用Skill
    public void disableSkill(String userId, String skillName) {
        // 1. 检查是否有其他启用的Skill依赖它
        List<String> dependents = dependencyStore.findDependents(userId, skillName);
        List<String> enabledDependents = dependents.stream()
            .filter(dep -> isSkillEnabled(userId, dep))
            .toList();
        if (!enabledDependents.isEmpty()) {
            throw new SkillDependencyException(
                "Cannot disable: required by enabled skills: " + enabledDependents);
        }

        // 2. 更新DB状态
        metadataStore.setEnabled(userId, skillName, false);

        // 3. 从内存移除（但保留文件和元数据）
        memoryCache.remove(userId, skillName);
    }
}
```

### 4.8 Skill安全沙箱（关键安全功能）

**设计目标：** 防止恶意Skill攻击系统、泄露数据或耗尽资源。

```java
public class SkillSandbox {
    private final SecurityManager securityManager;
    private final ResourceLimiter resourceLimiter;
    private final Set<String> allowedClasses;

    // 资源限制配置
    public static class ResourceLimits {
        private long maxMemoryMB = 256;      // 最大内存
        private long maxCpuTimeMs = 30000;    // 最大CPU时间
        private int maxThreads = 10;          // 最大线程数
        private long maxNetworkBytes = 10 * 1024 * 1024; // 最大网络流量
    }

    // 在沙箱中执行Skill Tool
    public <T> T executeInSandbox(Skill skill, Tool tool, Map<String, Object> args,
                                   Supplier<T> action) {
        // 1. 验证权限
        validatePermissions(skill.getMetadata().getPermissions());

        // 2. 设置资源限制
        ResourceLimits limits = resolveLimits(skill);
        ResourceToken token = resourceLimiter.acquire(limits);

        // 3. 在隔离环境中执行
        try {
            // 使用自定义ClassLoader隔离
            ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
            Thread.currentThread().setContextClassLoader(skill.getClass().getClassLoader());

            try {
                return action.get();
            } finally {
                Thread.currentThread().setContextClassLoader(originalLoader);
            }
        } catch (OutOfMemoryError e) {
            throw new SkillResourceExhaustedException("Memory limit exceeded");
        } catch (Throwable t) {
            throw new SkillExecutionException("Skill execution failed", t);
        } finally {
            resourceLimiter.release(token);
        }
    }

    private void validatePermissions(SkillPermissions permissions) {
        // 检查网络权限
        if (permissions.isNetworkAccess() && !networkAllowedGlobally) {
            throw new SkillSecurityException("Network access not allowed");
        }
        // 检查文件系统权限
        if (permissions.isFilesystemAccess()) {
            validateAllowedPaths(permissions.getAllowedPaths());
        }
    }
}

// 资源限制器
public class ResourceLimiter {
    private final Map<String, ResourceUsage> userUsage = new ConcurrentHashMap<>();

    public ResourceToken acquire(ResourceLimits limits) {
        // 使用Java 21的虚拟线程和内存限制
        return new ResourceToken(limits, Thread.currentThread());
    }

    public void release(ResourceToken token) {
        // 释放资源配额
    }
}
```

**安全策略配置：**
```yaml
skill:
  security:
    # 全局安全设置
    sandbox_enabled: true
    allow_network: false              # 默认禁止网络
    allow_filesystem: false           # 默认禁止文件系统

    # 资源限制
    limits:
      default:
        max_memory_mb: 256
        max_cpu_time_ms: 30000
        max_threads: 10

      # 内置Skill可以有更高权限
      builtin:
        max_memory_mb: 512
        allow_network: true
```

### 4.9 多环境持久化策略

**配置化存储策略：**
```yaml
# application.yaml
skill:
  storage:
    # 存储类型: local（默认）或 s3
    type: ${SKILL_STORAGE_TYPE:local}

    # 本地存储配置
    local:
      base_path: ${SKILL_STORAGE_PATH:./data/skills}

    # S3存储配置（可选，大规模/云部署时启用）
    s3:
      endpoint: ${S3_ENDPOINT:}
      bucket: ${S3_BUCKET:claw-skills}
      access_key: ${S3_ACCESS_KEY:}
      secret_key: ${S3_SECRET_KEY:}
      region: ${S3_REGION:us-east-1}
```

**恢复流程：**
```
应用启动
    │
    ▼
从DB加载所有启用的Skills元数据
    │
    ▼
检查存储层（Local/S3）是否存在Skill文件
    │
    ├─ 存在 → 加载到内存
    │
    └─ 不存在 → 从源重新下载（使用source_uri）→ 存储到存储层
```

### 4.9 Skill安装API

```java
@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {

    // 搜索可用Skills
    @GetMapping("/search")
    public List<SkillInfo> searchSkills(
            @RequestParam String query,
            @RequestParam(required = false) String repository) {
        return skillInstaller.search(query, repository);
    }

    // 安装Skill
    @PostMapping("/install")
    public SkillInstallResult installSkill(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody SkillInstallRequest request) {
        // request.skillUri: "github:owner/repo/skill@version"
        InstalledSkill skill = skillInstaller.install(userId, request.getSkillUri());
        return SkillInstallResult.success(skill);
    }

    // 列出已安装Skills
    @GetMapping("/installed")
    public List<InstalledSkillInfo> listInstalledSkills(
            @RequestHeader("X-User-Id") String userId) {
        return skillManager.getInstalledSkills(userId);
    }

    // 卸载Skill
    @DeleteMapping("/{skillName}")
    public void uninstallSkill(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String skillName) {
        skillInstaller.uninstall(userId, skillName);
    }

    // 配置Skill
    @PutMapping("/{skillName}/config")
    public void configureSkill(
            @RequestHeader("X-User-Id") String userId,
            @PathVariable String skillName,
            @RequestBody SkillConfig config) {
        skillManager.configureSkill(userId, skillName, config);
    }
}
```

---

## 5. Memory系统设计

### 5.1 双层记忆架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         MemoryManager                            │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    Short-Term Memory                      │   │
│  │   (ChatMemory - 最近N条消息，上下文窗口)                   │   │
│  │                                                          │   │
│  │   User A Session 1  │  User A Session 2  │  User B ...   │   │
│  └─────────────────────────────────────────────────────────┘   │
│                                                                  │
│  ┌─────────────────────────────────────────────────────────┐   │
│  │                    Long-Term Memory                       │   │
│  │   (EmbeddingStore - 向量存储，语义检索)                    │   │
│  │                                                          │   │
│  │   ┌──────────────┐  ┌──────────────┐  ┌──────────────┐ │   │
│  │   │  User A's    │  │  User B's    │  │  User C's    │ │   │
│  │   │  Memory      │  │  Memory      │  │  Memory      │ │   │
│  │   │  (Isolated)  │  │  (Isolated)  │  │  (Isolated)  │ │   │
│  │   └──────────────┘  └──────────────┘  └──────────────┘ │   │
│  └─────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────┘
```

### 5.2 PostgreSQL向量存储

```sql
-- 用户长期记忆存储
CREATE TABLE user_memories (
    id BIGSERIAL PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    memory_id VARCHAR(128) NOT NULL,      -- 向量ID
    content TEXT NOT NULL,                -- 原始文本
    embedding vector(384),                -- 向量（使用pgvector）
    metadata JSONB DEFAULT '{}',
    created_at TIMESTAMP DEFAULT NOW(),

    UNIQUE(user_id, memory_id)
);

-- 创建向量索引（pgvector扩展）
CREATE EXTENSION IF NOT EXISTS vector;
CREATE INDEX idx_user_memories_embedding ON user_memories
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);

CREATE INDEX idx_user_memories_user ON user_memories(user_id);
```

### 5.3 Memory接口设计

```java
public interface MemoryManager {
    // === 短期记忆 ===
    void addToShortTerm(String userId, String sessionId, Message message);
    List<Message> getShortTermHistory(String userId, String sessionId);
    void clearShortTerm(String userId, String sessionId);

    // === 长期记忆 ===
    void storeLongTerm(String userId, String content, Map<String, Object> metadata);
    List<MemoryMatch> searchLongTerm(String userId, String query, int maxResults);

    // === 记忆管理 ===
    void forget(String userId, String memoryId);
    void forgetAll(String userId);
    MemoryStats getStats(String userId);
}

public class MemoryMatch {
    private String memoryId;
    private String content;
    private double score;           // 相似度分数
    private Map<String, Object> metadata;
    private Instant timestamp;
}
```

### 5.4 LLM驱动的记忆提取

**核心思想：** 不是直接存储原始对话，而是使用LLM提取关键信息后再存储。

```java
public interface MemoryExtractor {
    List<ExtractedMemory> extract(String conversation, ExtractionContext context);
}

public class ExtractedMemory {
    private MemoryType type;           // FACT, PREFERENCE, ENTITY, RELATION, SUMMARY
    private String content;            // 提取的内容
    private double importance;         // 重要性评分 0-1
    private Map<String, Object> entities;  // 相关实体
    private Instant timestamp;
}

public enum MemoryType {
    FACT,           // 事实：用户说的确定信息
    PREFERENCE,     // 偏好：用户喜欢/不喜欢
    ENTITY,         // 实体：人名、地名、项目名等
    RELATION,       // 关系：实体之间的关系
    SUMMARY,        // 摘要：对话的总结
    TASK,           // 任务：用户要完成的事情
    DECISION        // 决策：用户做出的选择
}
```

### 5.5 记忆存储流程（带LLM提取）

```java
public class IntelligentMemoryManager implements MemoryManager {
    private final MemoryExtractor extractor;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private final ChatLanguageModel llm;

    // 对话结束后存储记忆
    public void storeConversation(String userId, List<Message> messages) {
        // 1. 构建对话文本
        String conversationText = buildConversationText(messages);

        // 2. 使用LLM提取重要记忆
        List<ExtractedMemory> extracted = extractor.extract(
            conversationText,
            new ExtractionContext(userId)
        );

        // 3. 去重：检查是否与现有记忆重复
        List<ExtractedMemory> newMemories = deduplicate(userId, extracted);

        // 4. 存储到向量数据库
        for (ExtractedMemory memory : newMemories) {
            TextSegment segment = TextSegment.from(
                formatMemoryContent(memory),
                createMetadata(userId, memory)
            );
            Embedding embedding = embeddingModel.embed(segment);
            embeddingStore.add(embedding);
        }
    }

    // 去重逻辑
    private List<ExtractedMemory> deduplicate(String userId, List<ExtractedMemory> extracted) {
        List<ExtractedMemory> unique = new ArrayList<>();

        for (ExtractedMemory memory : extracted) {
            Embedding query = embeddingModel.embed(memory.getContent());
            List<EmbeddingMatch<TextSegment>> similar = embeddingStore.findRelevant(
                query, 3, Filter.metadataKey("user_id").isEqualTo(userId)
            );

            // 如果没有高度相似的，则添加
            boolean isDuplicate = similar.stream()
                .anyMatch(m -> m.score() > 0.95);

            if (!isDuplicate) {
                unique.add(memory);
            }
        }

        return unique;
    }
}
```

### 5.6 记忆配置

```yaml
memory:
  # 短期记忆
  short_term:
    max_messages: 20
    max_tokens: 4000

  # 长期记忆
  long_term:
    enabled: true
    extraction:
      enabled: true                    # 启用LLM提取
      min_importance: 0.3              # 最小重要性阈值
      dedup_threshold: 0.95            # 去重相似度阈值

    retention:
      days: 90
      auto_cleanup: true

    # 向量存储
    embedding:
      model: all-minilm-l6-v2          # 本地模型

  # 记忆检索
  retrieval:
    max_results: 10
    score_threshold: 0.7
```

---

## 6. Session管理与并发模型

### 6.1 Session数据模型

```java
public class UserSession {
    // === 基础信息 ===
    private String sessionId;
    private String userId;
    private SessionState state;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant expiresAt;

    // === 会话数据 ===
    private Plan currentPlan;
    private int currentStepIndex;
    private Map<String, Object> context;

    // === 记忆引用 ===
    private String shortTermMemoryId;
    private String longTermMemoryScope;

    // === 配置 ===
    private UserModelConfig modelConfig;
    private List<String> enabledSkills;

    // === 统计 ===
    private int messageCount;
    private int tokensUsed;

    // === 元数据 ===
    private String clientIp;
    private String userAgent;
}
```

### 6.2 PostgreSQL Session存储

```sql
-- 用户会话表
CREATE TABLE user_sessions (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) UNIQUE NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    state VARCHAR(32) NOT NULL DEFAULT 'INIT',

    -- 会话数据（JSONB便于查询和更新）
    session_data JSONB NOT NULL DEFAULT '{}',

    -- 时间戳
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMP,

    -- 索引字段
    message_count INT DEFAULT 0,
    tokens_used BIGINT DEFAULT 0,

    -- 元数据
    client_ip VARCHAR(45),
    user_agent TEXT
);

-- 索引
CREATE INDEX idx_sessions_user ON user_sessions(user_id);
CREATE INDEX idx_sessions_state ON user_sessions(state);
CREATE INDEX idx_sessions_expires ON user_sessions(expires_at)
    WHERE expires_at IS NOT NULL;

-- 会话消息历史
CREATE TABLE session_messages (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    message_order INT NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    tokens INT DEFAULT 0,
    created_at TIMESTAMP DEFAULT NOW(),

    UNIQUE(session_id, message_order)
);
```

### 6.3 增强的Session生命周期管理

```java
public enum SessionLifecycleState {
    CREATED,            // 刚创建
    ACTIVE,             // 活跃使用中
    IDLE,               // 空闲
    PAUSED,             // 暂停
    ARCHIVED,           // 归档
    EXPIRED,            // 已过期
    TERMINATED,         // 已终止
    WAITING_INPUT,      // 等待用户输入
    PROCESSING,         // 正在处理
    ERROR_RECOVERY,     // 错误恢复中
    MIGRATING           // 正在迁移
}
```

### 6.4 Session快照与恢复

```sql
-- Session快照表
CREATE TABLE session_snapshots (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    snapshot_id VARCHAR(64) UNIQUE NOT NULL,
    snapshot_type VARCHAR(32) NOT NULL,    -- AUTO/MANUAL/BEFORE_CRITICAL

    session_data JSONB NOT NULL,
    messages JSONB NOT NULL,

    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    description TEXT,
    tags TEXT[]
);
```

```java
public class SessionSnapshotService {
    // 创建快照
    public SessionSnapshot createSnapshot(String sessionId, SnapshotType type, String description);

    // 从快照恢复
    public UserSession restoreFromSnapshot(String snapshotId);

    // 回滚到快照
    public void rollbackToSnapshot(String sessionId, String snapshotId);
}
```

### 6.5 Session分支与合并

```java
public class SessionBranchManager {
    // 创建分支
    public UserSession createBranch(String parentSessionId, int fromMessageIndex);

    // 合并分支
    public void mergeBranch(String branchSessionId, String targetSessionId);

    // 获取会话树
    public SessionTree getSessionTree(String rootSessionId);
}
```

### 6.6 Session共享与权限

```sql
-- Session共享表
CREATE TABLE session_shares (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    owner_user_id VARCHAR(64) NOT NULL,
    shared_with_user_id VARCHAR(64),
    share_token VARCHAR(128) UNIQUE,

    permission VARCHAR(16) NOT NULL,       -- READ, WRITE, ADMIN

    expires_at TIMESTAMP,
    max_uses INT,
    current_uses INT DEFAULT 0,

    created_at TIMESTAMP DEFAULT NOW()
);
```

### 6.7 Session审计日志

```sql
-- Session审计日志表
CREATE TABLE session_audit_logs (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,

    action VARCHAR(64) NOT NULL,
    action_details JSONB,

    ip_address VARCHAR(45),
    user_agent TEXT,

    success BOOLEAN NOT NULL,
    error_message TEXT,

    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
```

### 6.8 虚拟线程并发模型

```java
public class VirtualThreadExecutor {
    private final ExecutorService virtualThreadExecutor;

    public VirtualThreadExecutor() {
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public <T> CompletableFuture<T> submitAgentTask(AgentTask<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.execute();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, virtualThreadExecutor);
    }
}
```

### 6.9 Session级别并发控制（关键改进）

**设计目标：** 同一Session的请求串行执行，防止状态冲突；不同Session可以并行。

```java
public class SessionConcurrencyControl {
    // 每个Session一个信号量，保证同一Session串行执行
    private final Map<String, Semaphore> sessionLocks = new ConcurrentHashMap<>();
    private final ExecutorService virtualThreadExecutor;

    public SessionConcurrencyControl() {
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    // 在Session上下文中执行任务（串行）
    public <T> CompletableFuture<T> executeInSession(
            String sessionId,
            Callable<T> task) {

        Semaphore lock = sessionLocks.computeIfAbsent(
            sessionId,
            k -> new Semaphore(1)  // 每个Session只能有1个并发请求
        );

        return CompletableFuture.supplyAsync(() -> {
            try {
                // 获取Session锁
                lock.acquire();

                // 执行任务
                return task.call();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CompletionException(e);
            } catch (Exception e) {
                throw new CompletionException(e);
            } finally {
                lock.release();
            }
        }, virtualThreadExecutor);
    }

    // 带超时的Session执行
    public <T> CompletableFuture<T> executeInSessionWithTimeout(
            String sessionId,
            Callable<T> task,
            Duration timeout) {

        return executeInSession(sessionId, task)
            .orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    // 清理已关闭Session的锁
    public void cleanupSession(String sessionId) {
        Semaphore lock = sessionLocks.remove(sessionId);
        if (lock != null) {
            lock.drainPermits(); // 释放所有等待的请求
        }
    }

    // 获取Session当前状态
    public SessionConcurrencyState getSessionState(String sessionId) {
        Semaphore lock = sessionLocks.get(sessionId);
        if (lock == null) {
            return SessionConcurrencyState.IDLE;
        }
        return lock.availablePermits() > 0
            ? SessionConcurrencyState.IDLE
            : SessionConcurrencyState.PROCESSING;
    }
}

public enum SessionConcurrencyState {
    IDLE,           // 空闲，可以接受新请求
    PROCESSING,     // 正在处理请求
    WAITING         // 有等待中的请求
}
```

**在Controller中使用：**
```java
@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {
    private final SessionConcurrencyControl concurrencyControl;
    private final AgentOrchestrator orchestrator;

    @PostMapping
    public CompletableFuture<ChatResponse> chat(
            @RequestHeader("X-User-Id") String userId,
            @RequestBody ChatRequest request) {

        String sessionId = request.getSessionId();

        return concurrencyControl.executeInSessionWithTimeout(
            sessionId,
            () -> {
                UserSession session = sessionManager.getOrCreate(userId, sessionId);
                return orchestrator.process(session, request.getMessage());
            },
            Duration.ofSeconds(60)  // 60秒超时
        ).exceptionally(e -> {
            if (e instanceof TimeoutException) {
                return ChatResponse.timeout("请求处理超时");
            }
            return ChatResponse.error(e.getMessage());
        });
    }
}
```

```java
public class VirtualThreadExecutor {
    private final ExecutorService virtualThreadExecutor;

    public VirtualThreadExecutor() {
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    public <T> CompletableFuture<T> submitAgentTask(AgentTask<T> task) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return task.execute();
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, virtualThreadExecutor);
    }
}
```

### 6.10 用户级别限流

```java
public class UserRateLimiter {
    private final Map<String, RateLimiter> userLimiters = new ConcurrentHashMap<>();

    public boolean tryAcquire(String userId) {
        RateLimiter limiter = userLimiters.computeIfAbsent(
            userId,
            k -> RateLimiter.create(1.0)  // 1请求/秒
        );
        return limiter.tryAcquire();
    }
}
```

### 6.11 健康检查（生产必备）

```java
@RestController
@RequestMapping("/actuator")
public class HealthController {

    private final DataSource dataSource;
    private final SkillManager skillManager;
    private final SessionManager sessionManager;
    private final ChatLanguageModel llm;

    @GetMapping("/health")
    public HealthStatus health() {
        return HealthStatus.builder()
            .status(checkOverallStatus())
            .database(checkDatabase())
            .llm(checkLLM())
            .skills(checkSkills())
            .sessions(checkSessions())
            .memory(checkMemory())
            .timestamp(Instant.now())
            .build();
    }

    @GetMapping("/health/live")
    public HealthStatus liveness() {
        // K8s存活探针：进程是否存活
        return HealthStatus.alive();
    }

    @GetMapping("/health/ready")
    public HealthStatus readiness() {
        // K8s就绪探针：是否可以接收流量
        boolean dbReady = checkDatabase().isHealthy();
        boolean llmReady = checkLLM().isHealthy();

        return dbReady && llmReady
            ? HealthStatus.ready()
            : HealthStatus.notReady();
    }

    private ComponentHealth checkDatabase() {
        try (Connection conn = dataSource.getConnection()) {
            boolean valid = conn.isValid(5);
            return ComponentHealth.builder()
                .name("database")
                .healthy(valid)
                .details(Map.of(
                    "url", conn.getMetaData().getURL()
                ))
                .build();
        } catch (Exception e) {
            return ComponentHealth.unhealthy("database", e.getMessage());
        }
    }

    private ComponentHealth checkLLM() {
        try {
            // 简单的健康检查：发送一个最小请求
            String response = llm.generate("ping");
            return ComponentHealth.healthy("llm", Map.of("response", "ok"));
        } catch (Exception e) {
            return ComponentHealth.unhealthy("llm", e.getMessage());
        }
    }

    private ComponentHealth checkSkills() {
        int builtinCount = skillManager.getBuiltinSkillCount();
        return ComponentHealth.healthy("skills", Map.of(
            "builtin_count", builtinCount
        ));
    }

    private ComponentHealth checkSessions() {
        int activeCount = sessionManager.getActiveSessionCount();
        return ComponentHealth.healthy("sessions", Map.of(
            "active_count", activeCount
        ));
    }

    private ComponentHealth checkMemory() {
        Runtime runtime = Runtime.getRuntime();
        long usedMB = (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024;
        long maxMB = runtime.maxMemory() / 1024 / 1024;
        double usedPercent = (double) usedMB / maxMB * 100;

        return ComponentHealth.builder()
            .name("memory")
            .healthy(usedPercent < 90)  // 内存使用超过90%为不健康
            .details(Map.of(
                "used_mb", usedMB,
                "max_mb", maxMB,
                "used_percent", String.format("%.1f%%", usedPercent)
            ))
            .build();
    }
}

public class HealthStatus {
    private String status;  // "UP", "DOWN", "UNKNOWN"
    private List<ComponentHealth> components;
    private Instant timestamp;
}

public class ComponentHealth {
    private String name;
    private boolean healthy;
    private String error;
    private Map<String, Object> details;
}
```

### 6.12 优雅关闭（生产必备）

```java
@Component
public class GracefulShutdown {

    private static final Logger log = LoggerFactory.getLogger(GracefulShutdown.class);

    private final SessionManager sessionManager;
    private final SessionSnapshotService snapshotService;
    private final DataSource dataSource;
    private final ExecutorService executorService;

    // 关闭标志
    private volatile boolean shuttingDown = false;

    // 注册关闭钩子
    @PostConstruct
    public void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "shutdown-hook"));
    }

    // 检查是否正在关闭
    public boolean isShuttingDown() {
        return shuttingDown;
    }

    @PreDestroy
    public void shutdown() {
        log.info("开始优雅关闭...");

        // 1. 标记正在关闭，拒绝新请求
        shuttingDown = true;
        log.info("步骤1: 已标记关闭状态，拒绝新请求");

        // 2. 等待现有请求完成（最多等待30秒）
        waitForPendingRequests(Duration.ofSeconds(30));
        log.info("步骤2: 现有请求处理完成");

        // 3. 保存所有活跃Session快照
        saveSessionSnapshots();
        log.info("步骤3: Session快照已保存");

        // 4. 关闭ExecutorService
        shutdownExecutor();
        log.info("步骤4: Executor已关闭");

        // 5. 关闭数据库连接池
        closeDatabase();
        log.info("步骤5: 数据库连接已关闭");

        log.info("优雅关闭完成");
    }

    private void waitForPendingRequests(Duration timeout) {
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < timeout.toMillis()) {
            int pendingCount = sessionManager.getPendingRequestCount();
            if (pendingCount == 0) {
                return;
            }

            log.info("等待 {} 个请求完成...", pendingCount);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.warn("等待超时，仍有 {} 个请求未完成",
                 sessionManager.getPendingRequestCount());
    }

    private void saveSessionSnapshots() {
        List<UserSession> activeSessions = sessionManager.getActiveSessions();

        for (UserSession session : activeSessions) {
            try {
                snapshotService.createSnapshot(
                    session.getSessionId(),
                    SnapshotType.SHUTDOWN,
                    "关闭前自动保存"
                );
            } catch (Exception e) {
                log.error("保存Session快照失败: {}", session.getSessionId(), e);
            }
        }
    }

    private void shutdownExecutor() {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void closeDatabase() {
        if (dataSource instanceof HikariDataSource hikari) {
            hikari.close();
        }
    }
}

// 在Controller中检查关闭状态
@RestController
@RequestMapping("/api/v1")
public class BaseController {

    @Autowired
    private GracefulShutdown gracefulShutdown;

    protected void checkNotShuttingDown() {
        if (gracefulShutdown.isShuttingDown()) {
            throw new ServiceUnavailableException("服务正在关闭，请稍后重试");
        }
    }
}
```

```java
public class UserRateLimiter {
    private final Map<String, RateLimiter> userLimiters = new ConcurrentHashMap<>();

    public boolean tryAcquire(String userId) {
        RateLimiter limiter = userLimiters.computeIfAbsent(
            userId,
            k -> RateLimiter.create(1.0)  // 1请求/秒
        );
        return limiter.tryAcquire();
    }
}
```

### 6.13 Session配置

```yaml
session:
  default_ttl: 86400                    # 默认24小时
  max_ttl: 604800                       # 最大7天
  idle_timeout: 1800                    # 空闲30分钟转为IDLE

  snapshot:
    enabled: true
    auto_interval: 600000               # 10分钟自动快照
    max_per_session: 10

  branch:
    enabled: true
    max_branches_per_session: 5

  share:
    enabled: true
    max_shares_per_session: 10

  audit:
    enabled: true
    retention_days: 90

  rate_limit:
    requests_per_minute: 60
    tokens_per_minute: 100000
```

---

## 7. Tools系统设计

### 7.1 Tool注册表

```java
public interface Tool {
    String getName();
    String getDescription();
    ToolParameter getParameters();
    ToolResult execute(Map<String, Object> args, ToolContext context);
}

public class ToolRegistry {
    private final Map<String, Tool> builtinTools = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Tool>> userTools = new ConcurrentHashMap<>();

    public List<Tool> getAvailableTools(String userId) {
        List<Tool> tools = new ArrayList<>(builtinTools.values());
        Map<String, Tool> userSpecific = userTools.get(userId);
        if (userSpecific != null) {
            tools.addAll(userSpecific.values());
        }
        return tools;
    }
}
```

### 7.2 Tool与Skill的集成

```java
public class WeatherSkill implements Skill {
    @Override
    public List<Tool> getTools() {
        return List.of(
            new WeatherQueryTool(),
            new WeatherForecastTool()
        );
    }
}
```

---

## 8. 实现计划

### 8.1 分阶段实现（修订版：基于审查反馈）

**Phase 1: 核心框架重构（第1-2周）**
- 重构项目结构（模块化）
- 引入Spring Boot依赖注入
- 实现Agent接口增强（生命周期、能力声明）
- 实现AgentRegistry和ExecutorAgentRegistry
- **新增：** 实现健康检查端点
- **新增：** 实现优雅关闭机制

**Phase 2: Skills系统（第3-4周）**
- 实现Skill接口和skill.yaml解析
- 实现SkillManager和SkillRegistry
- 实现IsolatedClassLoader（用户隔离）
- **新增：** 实现Skill安全沙箱
- 实现本地Skill安装
- 实现元数据持久化（PostgreSQL）
- 实现Enable/Disable功能

**Phase 3: Memory系统（第5-6周）**
- 实现MemoryManager接口
- 实现PostgresEmbeddingStore（pgvector）
- 实现LLMMemoryExtractor
- 实现记忆去重和过期清理
- **新增：** 实现记忆时间衰减

**Phase 4: Session管理（第7-8周）**
- 实现SessionStore（PostgreSQL）
- 实现SessionLifecycleManager
- **新增：** 实现Session并发控制
- 实现Session快照与恢复
- 实现审计日志

**Phase 5: 并发与API（第9-10周）**
- 实现虚拟线程执行器
- 实现用户级别限流
- 实现REST API
- 集成测试和性能测试

**Phase 6: 远程Skill仓库（第11-12周，可选）**
- 实现GitHub Skill仓库
- 实现ClawHub Skill仓库
- 实现Skill签名验证

**总计：10-12周**

### 8.2 依赖添加（pom.xml）

```xml
<dependencies>
    <!-- LangChain4j -->
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j</artifactId>
        <version>0.35.0</version>
    </dependency>
    <dependency>
        <groupId>dev.langchain4j</groupId>
        <artifactId>langchain4j-zhipu-ai</artifactId>
        <version>0.35.0</version>
    </dependency>

    <!-- Spring Boot -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
        <version>3.2.0</version>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
        <version>3.2.0</version>
    </dependency>

    <!-- PostgreSQL + pgvector -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <version>42.7.0</version>
    </dependency>
    <dependency>
        <groupId>com.pgvector</groupId>
        <artifactId>pgvector</artifactId>
        <version>0.1.4</version>
    </dependency>

    <!-- 工具库 -->
    <dependency>
        <groupId>com.google.guava</groupId>
        <artifactId>guava</artifactId>
        <version>32.1.3-jre</version>
    </dependency>
</dependencies>
```

---

## 9. 验证计划

### 9.1 单元测试
- Agent接口测试
- Skill加载和隔离测试
- Memory提取和检索测试
- Session生命周期测试

### 9.2 集成测试
- 端到端对话流程
- Skill安装和使用
- 多用户并发

### 9.3 性能测试
- 10并发用户压测
- 100并发用户压测
- 内存和CPU使用监控

### 9.4 测试命令
```bash
# 编译
mvn clean compile

# 运行测试
mvn test

# 启动服务
mvn spring-boot:run

# 测试API
curl -X POST http://localhost:8080/api/v1/chat \
  -H "X-User-Id: test-user" \
  -H "Content-Type: application/json" \
  -d '{"message": "帮我计算 2+2"}'
```

---

## 附录A：数据库完整Schema

详见实现阶段的数据库迁移脚本。

## 附录B：API完整文档

详见实现阶段的API文档。
