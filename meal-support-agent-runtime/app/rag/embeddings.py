"""Embeddings client (OpenAI compatible)."""

from openai import OpenAI

from app.core.settings import Settings


class EmbeddingClient:
    def __init__(self, settings: Settings):
        self._client = OpenAI(
            base_url=settings.embedding_base_url,
            api_key=settings.embedding_api_key,
            timeout=30.0,
        )
        self._model = settings.embedding_model

    def embed_texts(self, texts: list[str]) -> list[list[float]]:
        if not texts:
            return []
        response = self._client.embeddings.create(model=self._model, input=texts)
        ordered = sorted(response.data, key=lambda item: item.index)
        return [item.embedding for item in ordered]

    def embed_query(self, text: str) -> list[float]:
        return self.embed_texts([text])[0]
