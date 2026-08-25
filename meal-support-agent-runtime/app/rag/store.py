"""Chroma persistent vector store wrapper."""

import chromadb

from app.rag.embeddings import EmbeddingClient


class ChromaStore:
    def __init__(self, chroma_dir: str, collection_name: str, embeddings: EmbeddingClient):
        self._client = chromadb.PersistentClient(path=chroma_dir)
        self._collection = self._client.get_or_create_collection(
            name=collection_name, metadata={"hnsw:space": "cosine"}
        )
        self._embeddings = embeddings

    @property
    def count(self) -> int:
        return self._collection.count()

    def upsert(self, ids: list[str], texts: list[str], metadatas: list[dict]) -> None:
        if not texts:
            return
        embeddings = self._embeddings.embed_texts(texts)
        self._collection.upsert(ids=ids, embeddings=embeddings, documents=texts, metadatas=metadatas)

    def query(self, query: str, top_k: int) -> list[dict]:
        if self._collection.count() == 0:
            return []
        query_embedding = self._embeddings.embed_query(query)
        result = self._collection.query(
            query_embeddings=[query_embedding],
            n_results=top_k,
            include=["documents", "metadatas", "distances"],
        )
        documents = (result.get("documents") or [[]])[0]
        metadatas = (result.get("metadatas") or [[]])[0]
        distances = (result.get("distances") or [[]])[0]
        hits = []
        for content, metadata, distance in zip(documents, metadatas, distances):
            hits.append(
                {
                    "content": content,
                    "source": (metadata or {}).get("source", ""),
                    "chunkIndex": str((metadata or {}).get("chunk_index", "")),
                    "score": round(float(1.0 - distance), 4),
                }
            )
        return hits
