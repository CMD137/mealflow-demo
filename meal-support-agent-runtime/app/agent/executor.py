"""ReAct / tool-calling loop (non-streaming)."""

import json
import time
from collections.abc import Callable

from app.agent.context import AgentRunContext
from app.agent.models import AgentExecutionResult
from app.agent.openai_client import LlmClient
from app.agent.prompt import build_system_prompt
from app.core.settings import Settings
from app.memory.history import RedisChatHistory
from app.tools.local_rag import LOCAL_RAG_SPEC
from app.tools.remote.specs import TOOL_SPECS

FALLBACK_ANSWER = "抱歉，我暂时无法完成您的请求，请稍后再试，或前往 App 内联系人工客服。"


def _dedupe_citations(citations: list[dict]) -> list[dict]:
    seen = set()
    unique = []
    for citation in citations:
        key = (citation.get("source", ""), citation.get("chunkIndex", ""))
        if key in seen:
            continue
        seen.add(key)
        unique.append(citation)
    return unique


class AgentExecutor:
    def __init__(self, llm: LlmClient, history: RedisChatHistory,
                 tool_executor: Callable[[str, dict], str], settings: Settings):
        self._llm = llm
        self._history = history
        self._tool_executor = tool_executor
        self._max_steps = settings.agent_max_steps

    def run(self, context: AgentRunContext, message: str) -> AgentExecutionResult:
        self._history.append(context.session_id, "user", message)
        messages = [{"role": "system", "content": build_system_prompt(context)}]
        messages.extend(self._history.get_messages(context.session_id))

        tools = TOOL_SPECS + [LOCAL_RAG_SPEC]
        used_tools: list[str] = []
        citations: list[dict] = []
        tool_elapsed_ms = 0
        started = time.perf_counter()

        for _ in range(self._max_steps):
            response = self._llm.chat(messages, tools)
            choice = response.choices[0]
            tool_calls = choice.message.tool_calls or []
            if tool_calls:
                messages.append(_assistant_tool_message(choice.message))
                for tool_call in tool_calls:
                    name = tool_call.function.name
                    arguments = _parse_arguments(tool_call.function.arguments)
                    used_tools.append(name)
                    tool_started = time.perf_counter()
                    content = self._tool_executor(name, arguments)
                    tool_elapsed_ms += int((time.perf_counter() - tool_started) * 1000)
                    if name == "local_rag_search":
                        try:
                            citations.extend(json.loads(content).get("citations", []))
                        except (json.JSONDecodeError, AttributeError):
                            pass
                    messages.append(
                        {"role": "tool", "tool_call_id": tool_call.id, "content": content}
                    )
                continue

            answer = (choice.message.content or "").strip()
            if not answer:
                answer = FALLBACK_ANSWER
            self._history.append(context.session_id, "assistant", answer)
            return AgentExecutionResult(
                answer=answer,
                used_tools=used_tools,
                citations=_dedupe_citations(citations),
                model_name=self._llm.model,
                llm_elapsed_ms=int((time.perf_counter() - started) * 1000),
                tool_elapsed_ms=tool_elapsed_ms,
            )

        self._history.append(context.session_id, "assistant", FALLBACK_ANSWER)
        return AgentExecutionResult(
            answer=FALLBACK_ANSWER,
            used_tools=used_tools,
            citations=_dedupe_citations(citations),
            model_name=self._llm.model,
            llm_elapsed_ms=int((time.perf_counter() - started) * 1000),
            tool_elapsed_ms=tool_elapsed_ms,
        )


def _assistant_tool_message(message) -> dict:
    payload = {"role": "assistant", "content": message.content}
    tool_calls = []
    for tool_call in message.tool_calls or []:
        tool_calls.append(
            {
                "id": tool_call.id,
                "type": "function",
                "function": {
                    "name": tool_call.function.name,
                    "arguments": tool_call.function.arguments,
                },
            }
        )
    if tool_calls:
        payload["tool_calls"] = tool_calls
    return payload


def _parse_arguments(raw: str | None) -> dict:
    try:
        parsed = json.loads(raw or "{}")
        return parsed if isinstance(parsed, dict) else {}
    except json.JSONDecodeError:
        return {}
