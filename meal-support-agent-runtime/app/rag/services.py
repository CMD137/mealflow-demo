"""Local RAG service: build index from knowledge docs and search with citations (A5)."""

from app.rag.embeddings import EmbeddingClient
from app.rag.loaders import load_documents
from app.rag.store import ChromaStore


class LocalRagService:
    def __init__(self, knowledge_dir: str, chroma_dir: str, collection: str, embeddings: EmbeddingClient,
                 top_k: int):
        self._knowledge_dir = knowledge_dir
        self._store = ChromaStore(chroma_dir, collection, embeddings)
        self._top_k = top_k

    def build_index(self) -> int:
        chunks = load_documents(self._knowledge_dir)
        ids = [f"{chunk.source}:{index}" for index, chunk in enumerate(chunks)]
        metadatas = [
            {"source": chunk.source, "chunk_index": index} for index, chunk in enumerate(chunks)
        ]
        self._store.upsert(ids, [chunk.content for chunk in chunks], metadatas)
        return len(chunks)

    def search(self, query: str, top_k: int | None = None) -> dict:
        hits = self._store.query(query, top_k or self._top_k)
        return {
            "query": query,
            "results": hits,
            "citations": [
                {"source": hit["source"], "chunkIndex": hit["chunkIndex"],
                 "score": hit["score"], "content": hit["content"]}
                for hit in hits
            ],
        }
