"""OpenAI-compatible chat client used by the ReAct loop (sync + streaming)."""

from collections.abc import Iterable

from openai import OpenAI

from app.core.settings import Settings


class LlmClient:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.client = OpenAI(
            base_url=settings.llm_base_url,
            api_key=settings.llm_api_key,
            timeout=settings.llm_timeout_seconds,
        )

    @property
    def model(self) -> str:
        return self.settings.llm_model

    def chat(self, messages: list[dict], tools: list[dict] | None = None):
        kwargs: dict = {
            "model": self.settings.llm_model,
            "messages": messages,
            "temperature": self.settings.llm_temperature,
        }
        if tools:
            kwargs["tools"] = tools
            kwargs["tool_choice"] = "auto"
        return self.client.chat.completions.create(**kwargs)

    def chat_stream(self, messages: list[dict], tools: list[dict] | None = None) -> Iterable:
        kwargs: dict = {
            "model": self.settings.llm_model,
            "messages": messages,
            "temperature": self.settings.llm_temperature,
            "stream": True,
        }
        if tools:
            kwargs["tools"] = tools
            kwargs["tool_choice"] = "auto"
        return self.client.chat.completions.create(**kwargs)
