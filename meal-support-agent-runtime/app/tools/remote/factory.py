"""Tool execution factory: routes tool calls either to the local RAG tool or the
Java bridge (the only outbound exit to business data)."""

import json
from collections.abc import Callable

from app.agent.context import AgentRunContext
from app.clients.support_tool_client import SupportToolClient
from app.tools.local_rag import LocalRagTool


def build_tool_executor(
    context: AgentRunContext,
    bridge_client: SupportToolClient,
    rag_tool: LocalRagTool,
) -> Callable[[str, dict], str]:
    def execute(tool_name: str, arguments: dict) -> str:
        if tool_name == "local_rag_search":
            query = str(arguments.get("query", ""))
            return json.dumps(rag_tool.search(query), ensure_ascii=False)
        result = bridge_client.invoke(context, tool_name, arguments)
        return json.dumps(result, ensure_ascii=False)

    return execute
