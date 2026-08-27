"""Build the local Chroma RAG index from knowledge documents.

Usage:
    python scripts/build_local_rag_index.py
"""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.core.settings import get_settings  # noqa: E402
from app.deps import get_rag_service  # noqa: E402


def main() -> None:
    settings = get_settings()
    service = get_rag_service()
    count = service.build_index()
    print(f"index built: {count} chunks -> {settings.chroma_collection}")


if __name__ == "__main__":
    main()
