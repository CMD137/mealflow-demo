# meal-support-agent-runtime

MealFlow 智能客服 Agent 运行时（Python FastAPI，端口 8090）。

- 意图识别与 ReAct 工具调用：OpenAI 兼容接口（`openai` SDK，`base_url` 可指向任意兼容服务）
- RAG 检索增强：Chroma 本地向量库 + 结构化引用（来源文档 + 段落，A5）
- Redis 多轮记忆 + 历史窗口化（最近 N 轮，A3）
- 流式输出：`/agent/chat/stream`（SSE：thinking → tool → answer 增量 → done，A1）
- 双向内部 token（A4）：`/agent/*` 全部校验 `Authorization: Bearer`（Java 桥传入）
- 唯一对外出口：`POST {SUPPORT_BRIDGE_URL}/internal/support/tools/invoke`（带 `X-Internal-Token`），
  永不直连任何业务微服务。

## 启动

### 方式一：全部进 Docker（推荐联调，网络走 compose 内部 DNS）

`meal-support` 与 `meal-support-agent-runtime` 都在 `docker-compose.yml` 中：

- `meal-support` 通过 `AGENT_RUNTIME_BASE_URL=http://meal-support-agent-runtime:8090` 访问 Python；
- Python 通过 `SUPPORT_BRIDGE_URL=http://meal-support:8111` 访问 Java 桥（唯一出口）；
- 双向 token 由顶层变量 `AGENT_INTERNAL_TOKEN` / `SUPPORT_INTERNAL_TOOL_TOKEN` 注入（两端同值）。

```bash
docker compose up -d --build meal-support-agent-runtime meal-support
# 需要 LLM/embedding 时：
#   export LLM_API_KEY=... EMBEDDING_API_KEY=... 后再 up
```

### 方式二：Python 本机跑（venv + uvicorn，离线联调）

```bash
python -m venv .venv
# Windows: .venv\Scripts\activate
source .venv/bin/activate
pip install -r requirements.txt

cp .env.example .env.local   # 或直接 export 环境变量；LLM/embedding 密钥只放本地
python scripts/build_local_rag_index.py   # 构建知识库索引（A5）
uvicorn app.main:app --port 8090
```

此时 Java 桥需能访问 `localhost:8090`：若 Java 也在本机跑（IDE）则直接通；
若 Java 在容器，给 `meal-support` 传 `AGENT_RUNTIME_BASE_URL=http://host.docker.internal:8090`，
并让 Python 可访问容器内的桥（本机开发建议 Java 也在本机跑，或临时发布 8111 端口）。

## 环境变量（见 .env.example）

| 变量 | 说明 |
|---|---|
| `LLM_BASE_URL` / `LLM_API_KEY` / `LLM_MODEL` | 大模型（OpenAI 兼容） |
| `EMBEDDING_BASE_URL` / `EMBEDDING_API_KEY` / `EMBEDDING_MODEL` | embedding |
| `REDIS_URL` | 复用 MealFlow 现有 Redis |
| `HISTORY_MAX_TURNS` | A3 历史窗口（默认 10） |
| `SUPPORT_BRIDGE_URL` | Java 桥地址（compose 内为 http://meal-support:8111；本机为 http://localhost:8111） |
| `AGENT_INTERNAL_TOKEN` | Java 桥 → Python 入站校验（A4，未配置时 /agent/* 返回 503） |
| `SUPPORT_INTERNAL_TOOL_TOKEN` | Python → Java 出站校验（A4，与 Java 侧 `SUPPORT_INTERNAL_TOOL_TOKEN` 同值） |

## 测试

```bash
pytest tests/
```

## 知识库

- `knowledge/documents/faq/*.md`：FAQ 文档（分块时保留来源与段落号）
- `knowledge/documents/rules/*.json`：业务规则
- `knowledge/chroma/`：本地向量索引（已 gitignore，可 `build_local_rag_index.py` 重建）

## 与方案文档的差异说明

方案文档中提及的 LangChain（`RedisChatMessageHistory`/`StructuredTool`）在本实现中以等价的
轻量实现替代：`app/memory/history.py` 用 redis-py 实现同语义的带 TTL 会话历史与窗口截断，
`app/tools/remote/factory.py` 以闭包实现同语义的工具分发。目录结构与职责一一对应，能力
（RAG/记忆/窗口/工具调用/双向鉴权/流式/引用）全部保留。
