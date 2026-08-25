# MealFlow 智能客服 Agent 实现与验收清单

> 对应方案：`MealFlow-agent-support-plan.md`（Groupbuy 同思路 + A 档生产级优化）
> 状态：已按阶段 0～8 落地，见下方"验收清单"。

## 1. 架构（两层）

```
meal-user-web (H5, 5174) SupportChatView
  → meal-gateway (8080) /api/support/**（JWT 校验 + 注入 X-User-Id/X-Role/X-Permissions）
  → meal-support (Spring Boot, 8111)
      ├─ /api/support/session / chat / chat/stream（A1 SSE）/ ping
      ├─ /internal/support/tools/invoke（仅 Python 调用，A4 内部 token）
      └─ meal_support_qa_log（A2 问答日志，schema.sql + business_sequence）
  → meal-support-agent-runtime (Python FastAPI, 8090)
      ├─ ReAct 工具调用（OpenAI 兼容）
      ├─ RAG：Chroma 本地向量库 + 结构化引用（A5）
      ├─ Redis 多轮记忆 + 历史窗口（A3）
      ├─ 流式事件（A1）
      └─ 唯一出口：POST :8111/internal/support/tools/invoke（带 token）
```

核心不变式：Python 永不直连业务微服务；身份以 Java 桥服务端会话为准；工具名只表达客服语义；
mock/real 双模式；traceId 贯穿前端→网关→桥→Python→工具调用。

## 2. 新增/改动文件

### Java 桥 `meal-support/`（新模块）
- `pom.xml`（web + webflux + mybatis + validation + actuator + nacos + h2/mysql）
- `src/main/resources/application.yml`（端口 8111、`SUPPORT_*`/`MEAL_*` 环境变量、`AGENT_RUNTIME_*`）
- `src/main/resources/schema.sql`（`meal_support_qa_log` + `business_sequence` 命名空间 `supportQaLog`）
- `MealSupportApplication`、`SupportDatabaseIdGenerator` + `SupportSequenceMapper`
- `controller/SupportChatController`（session/chat/chat/stream/ping）
- `controller/InternalToolController`（/internal/support/tools/invoke）
- `service/SessionContextStore`（内存会话 + TTL 惰性过期）、`ChatService`、`QaLogService`、`ToolRegistryService`（9 工具）、`ToolInvokeService`（token→会话→角色→参数→防伪造→mock/real）
- `client/AgentRuntimeClient`（非流式，Bearer）、`AgentRuntimeStreamClient`（SSE 透传）、`MockToolClient`、`RealToolClient`（直连 6 服务，解析 `Result<T>`）
- `dto/*`、`mapper/SupportQaLogMapper`、`config/HttpClientConfig`、`SupportExceptionHandler`
- `src/test/java/.../SupportCoreTest`（会话/鉴权/工具调用 9 个用例）

### Python 运行时 `meal-support-agent-runtime/`
- `app/core/settings.py`（pydantic-settings）、`app/core/auth.py`（A4 Bearer 校验）
- `app/agent/`：`executor.py`（ReAct 循环）、`executor_stream.py`（A1 流式）、`prompt.py`（引用规则）、`openai_client.py`、`models.py`、`context.py`
- `app/memory/history.py`（Redis 会话历史 + A3 窗口化）
- `app/rag/`：`chunker/embeddings/loaders/store/services`（Chroma + 结构化引用）
- `app/tools/`：`local_rag.py`（A5 检索工具）、`remote/specs.py`（9 工具 schema）、`remote/factory.py`
- `app/clients/support_tool_client.py`（唯一出口，带 `X-Internal-Token`）
- `app/api/routes/chat.py`、`chat_stream.py`、`app/main.py`、`app/deps.py`
- `knowledge/documents/faq/*.md`、`knowledge/documents/rules/business-rules.json`
- `scripts/build_local_rag_index.py`、`verify_local_rag.py`；`tests/`（auth/history/tool_client/executor）
- `requirements.txt`、`.env.example`、`README.md`

### 网关 / Compose / 前端
- `meal-gateway/src/main/resources/application.yml`：新增 route `meal-support`（`/api/support/**`）
- `GatewayAuthenticationFilter`：`/api/support/ping` 加入 public path
- `docker-compose.yml`：新增 `meal-support` 服务；gateway/prometheus depends_on 与 `MEAL_SUPPORT_URI` 环境变量
- `observability/prometheus/prometheus.yml`：新增 `meal-support:8111` 抓取目标
- `meal-user-web/src/api/support.ts`（含 fetch + ReadableStream 流式）、`views/SupportChatView.vue`、路由 `/support`、`MineView` 入口

## 3. 工具清单（9 个，全部只读）

| 工具 | 模式 | 归属校验 |
|---|---|---|
| `query_order_status` / `query_user_spending` | mock-only | — |
| `get_order_detail` | real+mock | 下游 + 桥内兜底比对 order.userId |
| `get_user_orders` / `get_voucher_wallet` / `get_voucher_claim_status` / `get_my_notifications` | real+mock | 下游按 X-User-Id 过滤 |
| `get_queue_status` | real+mock | 桥内组合路径：GET /orders → 匹配 queueTicketId → 查 ticket（下游 ticket 接口无归属，见 §10 风险） |
| `get_merchant_menu` | real+mock | 公开数据 |

## 4. 安全边界（A4）

- Python→Java：`X-Internal-Token` 与 `SUPPORT_INTERNAL_TOOL_TOKEN` 常量时间比对，未配置即拒绝。
- Java→Python：`/agent/*` 全部校验 `Authorization: Bearer`（`AGENT_INTERNAL_TOKEN`），未配置返回 503。
- 身份以服务端会话为准：会话在创建时从网关注入头固化；工具参数禁止出现 `userId/role/merchantId`（防伪造）。
- 角色白名单 + 必填参数校验；`/internal/support/tools/invoke` 不进网关、不对外暴露。

## 5. 验收清单（阶段 0～8）

| # | 验收项 | 命令/操作 | 通过 |
|---|---|---|---|
| 0 | 工作区方向变更已提交；新分支 | `git log --oneline`（08ad793/4dce38c/0bb4f58 + feat/support-agent） | ☐ |
| 1 | meal-support 编译通过 | `mvn -o -pl meal-support -am -DskipTests compile` | ☐ |
| 1 | 模块 ping | `GET http://localhost:8111/api/support/ping` 返回 9 个工具 | ☐ |
| 2 | 会话 + 非流式 chat（Python 起后） | `POST /api/support/session` → `POST /api/support/chat` 返回答案 | ☐ |
| 3 | mock 工具调用 | `POST :8111/internal/support/tools/invoke`（带 X-Internal-Token）各工具返回固定数据 | ☐ |
| 3 | real 工具调用 | `SUPPORT_TOOL_CLIENT_MODE=real` 后 `get_user_orders` 等 6 条服务链打通 | ☐ |
| 4 | A2 问答日志 | chat 后 `SELECT * FROM meal_support_qa_log` 有记录（usedTools/traceId/耗时） | ☐ |
| 5 | Python 全套 | `pytest tests/`；`build_local_rag_index.py` + `verify_local_rag.py`；`POST :8090/agent/chat`（Bearer）意图→工具→回答 | ☐ |
| 6 | A1 流式 | `curl -N POST :8090/agent/chat/stream` 与 `:8111/api/support/chat/stream` 观察 thinking→tool→answer→done；无 token 401/503 | ☐ |
| 7 | 网关 + H5 | 经网关访问 `/api/support/ping`；H5 登录后可流式对话，未登录 401 | ☐ |
| 8 | 演示话术 | "我的订单到哪了" / "我排到第几了" 返回真实接口数据；FAQ 问题返回带引用答案 | ☐ |
| 9 | 文档 | 本文档 + 根 README 启动顺序/mock-real 切换/双向 token 配置 | ☐ |

## 6. 启动顺序（本地联调）

1. 启动依赖：`docker compose up -d mysql redis`（如已有完整环境可跳过）。
2. 启动 Java 桥（本机）：`mvn -o -pl meal-support -am spring-boot:run`（或 IDE），确保 8111。
   - 若走 compose：`mvn -o -DskipTests package` 后 `docker compose up -d meal-support`，
     Python 侧 `AGENT_RUNTIME_BASE_URL`/`SUPPORT_BRIDGE_URL` 用 `host.docker.internal`。
3. 启动 Python：`cd meal-support-agent-runtime && python -m venv .venv && .venv\Scripts\activate && pip install -r requirements.txt && python scripts/build_local_rag_index.py && uvicorn app.main:app --port 8090`。
4. 配置双向 token：两边 `AGENT_INTERNAL_TOKEN` 同值；`SUPPORT_INTERNAL_TOOL_TOKEN` 同值。
5. 前端：`.\start-frontend.cmd` 后访问 H5 → 我的 → 在线客服。

## 7. 已知说明

- `get_queue_status` 依赖"订单→票据"组合路径，因为 queue 的 `GET /queue/tickets/{ticketId}` 无归属校验（既有问题，未改动 queue 代码）。
- Chroma 索引目录已 gitignore，重建即 `python scripts/build_local_rag_index.py`。
- LLM/embedding 密钥只放 `.env.local`，不提交。
- 本实现未使用 LangChain，以等价轻量实现替代（见运行时 README"与方案文档的差异说明"）。
