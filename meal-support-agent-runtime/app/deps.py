"""Shared singletons for the FastAPI app (cached via lru_cache)."""

from functools import lru_cache

from app.agent.context import AgentRunContext
from app.agent.executor import AgentExecutor
from app.agent.executor_stream import AgentStreamExecutor
from app.agent.openai_client import LlmClient
from app.clients.support_tool_client import SupportToolClient
from app.core.settings import Settings, get_settings
from app.memory.history import RedisChatHistory
from app.rag.embeddings import EmbeddingClient
from app.rag.services import LocalRagService
from app.tools.local_rag import LocalRagTool
from app.tools.remote.factory import build_tool_executor


@lru_cache
def get_llm() -> LlmClient:
    return LlmClient(get_settings())


@lru_cache
def get_history() -> RedisChatHistory:
    return RedisChatHistory(get_settings())


@lru_cache
def get_bridge_client() -> SupportToolClient:
    return SupportToolClient(get_settings())


@lru_cache
def get_rag_service() -> LocalRagService:
    settings = get_settings()
    return LocalRagService(
        settings.knowledge_dir,
        settings.chroma_dir,
        settings.chroma_collection,
        EmbeddingClient(settings),
        settings.rag_top_k,
    )


@lru_cache
def get_rag_tool() -> LocalRagTool:
    return LocalRagTool(get_rag_service())


def get_executor() -> AgentExecutor:
    settings = get_settings()
    return AgentExecutor(get_llm(), get_history(), _tool_executor(), settings)


def get_stream_executor() -> AgentStreamExecutor:
    settings = get_settings()
    return AgentStreamExecutor(get_llm(), get_history(), _tool_executor(), settings)


def _tool_executor():
    settings = get_settings()

    def executor(context: AgentRunContext, tool_name: str, arguments: dict) -> str:
        # A fresh closure is created per run; the client instance is shared.
        return build_tool_executor(context, get_bridge_client(), get_rag_tool())(tool_name, arguments)

    return executor
