"""Streaming variant of the ReAct loop (A1).

Yields SSE event dicts:
  {"event": "status", "data": "thinking"}
  {"event": "tool",   "data": "<tool name>"}
  {"event": "answer", "data": "<incremental text>"}
  {"event": "done",   "data": '{"usedTools":[...],"citations":[...],"modelName":"..."}'}
  {"event": "error",  "data": "<message>"}
"""

import json
import time
from collections.abc import Callable, Iterator

from app.agent.context import AgentRunContext
from app.agent.executor import FALLBACK_ANSWER, _assistant_tool_message, _dedupe_citations, _parse_arguments
from app.agent.openai_client import LlmClient
from app.agent.prompt import build_system_prompt
from app.core.settings import Settings
from app.memory.history import RedisChatHistory
from app.tools.local_rag import LOCAL_RAG_SPEC
from app.tools.remote.specs import TOOL_SPECS


class AgentStreamExecutor:
    def __init__(self, llm: LlmClient, history: RedisChatHistory,
                 tool_executor: Callable[[str, dict], str], settings: Settings):
        self._llm = llm
        self._history = history
        self._tool_executor = tool_executor
        self._max_steps = settings.agent_max_steps

    def run(self, context: AgentRunContext, message: str) -> Iterator[dict]:
        self._history.append(context.session_id, "user", message)
        messages = [{"role": "system", "content": build_system_prompt(context)}]
        messages.extend(self._history.get_messages(context.session_id))
        tools = TOOL_SPECS + [LOCAL_RAG_SPEC]
        used_tools: list[str] = []
        citations: list[dict] = []
        started = time.perf_counter()
        bound_executor = lambda tool_name, arguments: self._tool_executor(  # noqa: E731
            context, tool_name, arguments
        )

        yield {"event": "status", "data": "thinking"}

        for _ in range(self._max_steps):
            tool_calls = {}
            answer_parts: list[str] = []
            finish_reason = None
            stream = self._llm.chat_stream(messages, tools)
            for chunk in stream:
                delta = chunk.choices[0].delta if chunk.choices else None
                if delta is None:
                    continue
                if delta.content:
                    answer_parts.append(delta.content)
                    yield {"event": "answer", "data": delta.content}
                for tool_delta in delta.tool_calls or []:
                    index = tool_delta.index
                    entry = tool_calls.setdefault(index, {"id": "", "name": "", "arguments": ""})
                    if tool_delta.id:
                        entry["id"] = tool_delta.id
                    if tool_delta.function:
                        if tool_delta.function.name:
                            entry["name"] += tool_delta.function.name
                        if tool_delta.function.arguments:
                            entry["arguments"] += tool_delta.function.arguments
                if chunk.choices and chunk.choices[0].finish_reason:
                    finish_reason = chunk.choices[0].finish_reason

            if tool_calls:
                assistant_payload = {
                    "role": "assistant",
                    "content": "".join(answer_parts) or None,
                    "tool_calls": [
                        {
                            "id": entry["id"],
                            "type": "function",
                            "function": {"name": entry["name"], "arguments": entry["arguments"]},
                        }
                        for entry in tool_calls.values()
                    ],
                }
                messages.append(assistant_payload)
                for entry in tool_calls.values():
                    name = entry["name"]
                    arguments = _parse_arguments(entry["arguments"])
                    used_tools.append(name)
                    yield {"event": "tool", "data": name}
                    content = bound_executor(name, arguments)
                    if name == "local_rag_search":
                        try:
                            citations.extend(json.loads(content).get("citations", []))
                        except (json.JSONDecodeError, AttributeError):
                            pass
                    messages.append(
                        {"role": "tool", "tool_call_id": entry["id"], "content": content}
                    )
                continue

            answer = "".join(answer_parts).strip() or FALLBACK_ANSWER
            self._history.append(context.session_id, "assistant", answer)
            yield {
                "event": "done",
                "data": json.dumps(
                    {
                        "usedTools": used_tools,
                        "citations": _dedupe_citations(citations),
                        "modelName": self._llm.model,
                        "llmElapsedMs": int((time.perf_counter() - started) * 1000),
                    },
                    ensure_ascii=False,
                ),
            }
            return

        self._history.append(context.session_id, "assistant", FALLBACK_ANSWER)
        yield {"event": "answer", "data": FALLBACK_ANSWER}
        yield {
            "event": "done",
            "data": json.dumps(
                {
                    "usedTools": used_tools,
                    "citations": _dedupe_citations(citations),
                    "modelName": self._llm.model,
                    "llmElapsedMs": int((time.perf_counter() - started) * 1000),
                },
                ensure_ascii=False,
            ),
        }
