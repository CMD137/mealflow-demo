"""Local RAG search tool exposed to the LLM (retrieval only, A5)."""

from app.rag.services import LocalRagService

LOCAL_RAG_SPEC = {
    "type": "function",
    "function": {
        "name": "local_rag_search",
        "description": "检索客服知识库（下单流程/排队规则/优惠券/退款/配送等 FAQ），返回含来源文档与段落的引用",
        "parameters": {
            "type": "object",
            "properties": {"query": {"type": "string", "description": "检索问题"}},
            "required": ["query"],
        },
    },
}


class LocalRagTool:
    def __init__(self, rag_service: LocalRagService):
        self._rag = rag_service

    def search(self, query: str) -> dict:
        return self._rag.search(query)
