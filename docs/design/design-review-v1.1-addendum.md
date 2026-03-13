# Agent内核系统设计评审补充意见（v1.1）

> 审查日期: 2026-03-13
> 审查人: GitHub Copilot Coding Agent
> 评审对象: `docs/design/agent-kernel-design.md` v1.1
> 说明: 本文为**补充评审意见**，不修改原设计文档，也不覆盖已有的 `design-review.md`（其内容主要针对 v1.0）。

---

## 1. 结论

`agent-kernel-design.md` v1.1 已经可以较好地覆盖题目提出的 8 项需求，整体架构方向也是合理的，尤其在以下方面表现较好：

- 采用 **Plan / Review / Executor** 的多 Agent 协作主线，职责划分清晰；
- 明确支持 **多轮会话**、`WAITING_FOR_USER` / `WAITING_INPUT` 等 human-in-the-loop 状态；
- 对 **builtin skill + user skill** 做了两层设计，并引入用户隔离概念；
- 对 **长短期记忆**、**用户级模型配置**、**Session 持久化**、**虚拟线程并发** 均给出了较完整的设计；
- 已开始补齐工程性内容，例如 **健康检查**、**优雅关闭**、**Session 级串行化控制**。

如果目标是做一个 **单进程、多用户、可持续演进的 Java Agent 内核 MVP**，这个方案已经具备实施基础。

不过，站在最佳工程实践角度，仍有几个必须明确的结论：

1. **需求层面基本满足，但不是“零风险可直接落地”**。  
   最大风险仍然在 **用户可安装 Skill 的安全边界**，其次是 **认证授权与租户隔离**，以及 **Session 一致性与幂等性**。

2. **PostgreSQL 路线是合理且推荐的**。  
   题目要求“Session 存储到 MySQL 或 PostgreSQL 中”，当前设计选择 PostgreSQL，按需求来说是满足的；但文档应更明确地写成：**v1.1 的正式目标是 PostgreSQL-first，而不是同时兼容 MySQL**，否则容易让读者误以为两者都要一并支持。

3. **现有功能面略偏“大而全”**。  
   对 MVP 来说，远程 Skill 仓库、Session 分支/合并/共享等能力不是最早期必须项，建议下调优先级，避免把核心可交付能力拖慢。

**综合判断**：  
**建议采纳 v1.1 设计作为实现基线，但需在实施前把安全边界、鉴权边界、数据一致性边界写得更硬一些。**

---

## 2. 对 8 项需求的逐项评审

| 需求 | 结论 | 评审意见 |
|---|---|---|
| 1. 单进程多用户 | 满足 | SessionManager、用户级 Skill/Memory 隔离、虚拟线程模型都支持单进程多用户。建议补充“单 JVM 单实例”的容量上限与降级策略。 |
| 2. 多 Agent 协作（Plan / Review / Executor） | 满足 | Agent 接口、状态机、ExecutorAgentRegistry 设计完整，能够支撑一个或多个 Executor Agent 按 plan 分派执行。 |
| 3. 多轮会话、human in the loop | 满足 | `WAITING_FOR_USER` / `WAITING_INPUT`、Session 快照恢复、持久化消息历史均支持多轮与人工介入。 |
| 4. 支持 tools | 满足 | ToolRegistry 与 Skill 集成关系清晰。建议额外定义工具调用超时、幂等性和审计规范。 |
| 5. 两层 skills（builtin + 用户安装，且用户隔离） | 基本满足 | 功能面满足，但安全实现仍需强化。**“类加载器隔离 != 安全隔离”**，不能把它作为执行任意用户代码的最终边界。 |
| 6. 长短期记忆 | 满足 | 短期会话记忆 + 长期向量记忆的分层方案合理，PostgreSQL + pgvector 也契合技术路线。 |
| 7. 用户级切换不同 chatModel 给不同 agent 角色 | 满足 | `UserModelConfig` + `AgentType -> ModelSettings` 的映射满足需求。建议再补“预算/限流/降级”策略。 |
| 8. 用户 session 存储到 MySQL 或 PostgreSQL | 满足（以 PostgreSQL 方式） | 当前设计清晰支持 PostgreSQL。若短期不做 MySQL，建议明确写成非目标，避免未来实现范围失控。 |

---

## 3. 最关键的工程实践问题

### 3.1 用户 Skill 的安全边界仍需重写得更扎实

这是整个方案里最需要优先收敛的部分。

当前设计已经比 v1.0 更进一步，加入了沙箱、权限声明、签名等方向，但如果系统允许“用户安装自定义 Skill 并执行代码”，那么工程上要避免把以下能力仅停留在概念层：

- 隔离 ClassLoader
- `skill.yaml` 权限字段
- “受限执行”文字描述

这些都**不等于真正的执行隔离**。对 Java 21 / Spring Boot 应用来说，更稳妥的建议是：

#### 建议方案

1. **将用户 Skill 设计为“受限插件”而不是“任意 Java 代码”**
   - MVP 阶段优先支持：
     - 声明式 Skill（prompt + tool binding + manifest）
     - 少量受控脚本型 Skill
   - 尽量不要在单进程主 JVM 内直接运行任意第三方 Java 字节码。

2. **若必须执行用户代码，优先使用进程级/容器级隔离**
   - 独立 worker 进程
   - 容器沙箱 / gVisor / Firecracker / 远端执行节点
   - 明确 CPU、内存、线程数、文件系统、网络出口、执行时长限制

3. **权限模型默认拒绝**
   - network / filesystem / env / subprocess / outbound host allowlist
   - 每次安装需要记录审批和审计信息

4. **供应链治理要前移**
   - Skill 包签名校验
   - 来源白名单
   - 校验 checksum
   - 版本固定与回滚策略

> 补充说明：已有 `design-review.md` 中出现的 `SecurityManager` 建议不宜继续沿用为主方案。  
> 对现代 Java 来说，`SecurityManager` 已不是推荐的长期安全边界；这里更适合强调**进程级隔离**与**最小权限执行**。

---

### 3.2 鉴权与租户隔离还不够“生产化”

目前文档中示例 API 仍以 `X-User-Id` 为主，这在原型阶段可用于演示，但不足以作为生产设计基线。

#### 必须补充

1. **认证来源**
   - JWT / OAuth2 / Session Token 至少选一种
   - `userId` 应从认证上下文中导出，而不是直接信任请求头

2. **授权边界**
   - 谁能安装 Skill
   - 谁能切换模型
   - 谁能读取/恢复/分享某个 Session

3. **数据访问隔离**
   - 所有查询必须带 `user_id` / `tenant_id`
   - Session / Memory / Skill 元数据不能只靠业务层约定隔离
   - 对关键写操作增加 ownership 校验

4. **审计**
   - Skill 安装 / 启用 / 禁用 / 升级
   - 模型切换
   - Session 恢复 / 回滚 / 分享

---

### 3.3 Session 并发控制是对的，但还差数据库一致性与幂等设计

`SessionConcurrencyControl` 让同一 Session 在单进程内串行执行，这是一个正确方向。  
但如果从最佳实践看，仅靠 JVM 内的 `Semaphore` 还不够：

#### 建议补充

1. **数据库乐观锁**
   - `user_sessions` 增加 `version` 字段
   - 更新 Session 时执行 compare-and-set / optimistic locking

2. **请求幂等键**
   - 为 `/chat` 请求增加 `request_id` / idempotency key
   - 防止客户端重试造成 plan 或 executor 重复执行

3. **状态转换约束**
   - 明确哪些状态允许跳转
   - 状态变更失败时的补偿方式

4. **异步任务恢复**
   - 若 Executor 长时间运行，主流程重启后如何恢复/补偿，需要写清楚

这部分尤其重要，因为系统当前虽然目标是“单进程”，但只要未来扩为多实例，这块就会成为迁移成本最高的区域。

---

### 3.4 数据库设计总体合理，但建议区分“会变的热数据”和“归档数据”

当前 PostgreSQL 方案方向正确，尤其是：

- Session 主表 + message 历史表
- JSONB 承载灵活结构
- pgvector 存长期记忆
- 审计日志独立存储

建议再补 4 个实践点：

1. **热字段结构化，冷字段 JSONB 化**
   - `state`、`current_step_index`、`updated_at`、`expires_at` 等高频查询字段应继续保持结构化
   - 避免把所有状态都塞进 `session_data`

2. **生命周期与保留策略**
   - Session 消息、快照、审计日志、长期记忆分别定义保留期
   - 明确归档与清理任务

3. **敏感数据最小化**
   - `client_ip`、`user_agent`、用户 prompt、工具返回结果中可能包含敏感数据
   - 明确脱敏、加密和导出规范

4. **迁移策略**
   - 使用 Flyway 维护 schema 版本
   - 所有索引和约束以迁移脚本为准，而不是只停留在设计图里

---

### 3.5 模型配置满足功能要求，但还缺“治理能力”

`UserModelConfig` 的方向是对的，不过生产环境里常见问题不是“能不能切换模型”，而是“如何有边界地切换模型”。

建议补充：

- 每用户/每 Agent 角色的 **token 预算** 与 **费用上限**
- fallback 模型策略（主模型失败时降级）
- provider 级限流
- 按环境控制可用模型白名单
- 模型切换的审计记录

否则“用户可自由切换不同模型”在工程上容易演化成成本和稳定性风险。

---

### 3.6 可观测性建议从“附加能力”提升为“核心能力”

这个系统的复杂度天然高于普通 CRUD 服务，因为它有：

- 多 Agent 状态流转
- Tool 调用
- Memory 读写
- Session 恢复
- 用户级模型路由

因此建议至少把以下内容纳入基础实现，而不要完全后置：

- 每次请求的 `traceId`
- plan/review/execute 各阶段耗时
- tool 调用次数、超时、失败率
- 每用户 token 消耗、每模型成本
- 队列积压/并发等待情况
- 关键审计事件日志

如果这些数据没有在一开始打通，后续排障会非常困难。

---

## 4. 对当前范围控制的建议

我认可文档“分阶段实现”的思路，但建议进一步压缩 MVP 范围。

### 建议保留在 MVP（必须做）

1. 单进程多用户 Session 管理
2. Plan / Review / Executor 主链路
3. 多轮会话 + human-in-the-loop
4. ToolRegistry
5. builtin skills
6. PostgreSQL Session 持久化
7. 用户级模型配置
8. 短期记忆 + 基础长期记忆
9. 认证、审计、健康检查、优雅关闭

### 建议延后到 Phase 2/3（非 MVP 必须）

1. 远程 Skill 仓库（GitHub / ClawHub）
2. Session 分支 / 合并
3. Session 分享
4. 复杂的 Skill 依赖解析
5. 用户任意 Java 代码安装

原因很简单：这些功能都“有价值”，但都不是最小可交付闭环的一部分，而且它们会显著放大安全、兼容性和运维复杂度。

---

## 5. 我认为更稳妥的落地顺序

### Phase 1：可上线的内核 MVP

- Spring Boot + DI
- PostgreSQL SessionStore
- AgentOrchestrator + Plan / Review / Executor
- 同 Session 串行执行
- builtin skills
- ToolRegistry
- JWT/OAuth2 至少一种认证方式
- 审计日志、健康检查、优雅关闭

### Phase 2：增强能力

- 长期记忆 + pgvector
- 用户级模型路由与预算控制
- 用户安装 Skill，但限制为**受控 manifest / 受控执行模型**
- 更完整的可观测性与成本监控

### Phase 3：高风险扩展能力

- 远程 Skill 仓库
- Skill 签名与审批工作流
- Session 分支/合并/分享
- 更强的多租户治理与运维能力

---

## 6. 最终判断

### 是否满足原始需求？

**是，整体上满足。**

更准确地说：

- **功能设计上**：8 项需求均已覆盖；
- **工程落地上**：仍有 3 个高优先级问题必须在实现前写实：
  1. 用户 Skill 的真正安全边界
  2. 认证授权与租户隔离
  3. Session 更新的一致性与幂等性

### 是否符合最佳工程实践？

**大体符合，但还需要把“安全、鉴权、可观测、幂等”从建议项提升为基线约束。**

### 是否有更好的思路？

有，核心只有一句话：

> **把“用户 Skill”从“可执行任意 Java 扩展”收敛为“受控插件/受控执行单元”，把“PostgreSQL-first”明确写成范围边界，把“认证 + 审计 + 幂等 + 可观测”前移到 MVP。**

这样既能满足题目需求，也更接近一个能稳定演进的生产级工程方案。
