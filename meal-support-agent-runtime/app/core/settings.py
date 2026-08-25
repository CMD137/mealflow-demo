from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")

    # LLM (OpenAI compatible)
    llm_base_url: str = "https://api.openai.com/v1"
    llm_api_key: str = ""
    llm_model: str = "gpt-4o-mini"
    llm_temperature: float = 0.2
    llm_timeout_seconds: float = 60.0

    # Embeddings (OpenAI compatible)
    embedding_base_url: str = "https://api.openai.com/v1"
    embedding_api_key: str = ""
    embedding_model: str = "text-embedding-3-small"

    # Redis multi-turn memory
    redis_url: str = "redis://localhost:6379/0"
    memory_key_prefix: str = "mealflow:support:history:"
    memory_ttl_seconds: int = 3600
    # A3: keep only the most recent N human turns (and their assistant replies)
    history_max_turns: int = 10

    # Agent loop
    agent_max_steps: int = 4

    # RAG
    knowledge_dir: str = "knowledge"
    chroma_dir: str = "knowledge/chroma"
    chroma_collection: str = "mealflow_faq"
    rag_top_k: int = 3

    # Bridge (Java) endpoint - the ONLY outbound exit for tools
    support_bridge_url: str = "http://localhost:8111"

    # A4: bidirectional internal tokens
    #   agent_internal_token: Java bridge -> Python (/agent/*) inbound validation
    #   support_internal_token: Python -> Java (/internal/support/tools/invoke)
    agent_internal_token: str = ""
    support_internal_token: str = "change-me"

    # SSE
    sse_ping_seconds: int = 15


@lru_cache
def get_settings() -> Settings:
    return Settings()
