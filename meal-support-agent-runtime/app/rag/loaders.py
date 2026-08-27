"""Load knowledge documents (faq markdown + rules json)."""

import json
from pathlib import Path

from app.rag.chunker import Chunk, chunk_json_lines, chunk_markdown


def load_documents(knowledge_dir: str) -> list[Chunk]:
    root = Path(knowledge_dir)
    chunks: list[Chunk] = []

    faq_dir = root / "documents" / "faq"
    for path in sorted(faq_dir.glob("*.md")):
        chunks.extend(chunk_markdown(path.read_text(encoding="utf-8"), path.name))

    rules_dir = root / "documents" / "rules"
    for path in sorted(rules_dir.glob("*.json")):
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except json.JSONDecodeError:
            continue
        if isinstance(data, list):
            for item in data:
                chunks.extend(chunk_json_lines(json.dumps(item, ensure_ascii=False), path.name))
        else:
            chunks.extend(chunk_json_lines(json.dumps(data, ensure_ascii=False), path.name))

    return chunks
