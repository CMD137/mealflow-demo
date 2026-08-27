"""Document chunking with source metadata (A5 foundation)."""

from dataclasses import dataclass

MAX_CHUNK_CHARS = 400


@dataclass
class Chunk:
    content: str
    source: str
    chunk_index: int


def chunk_markdown(text: str, source: str) -> list[Chunk]:
    """Split markdown into chunks at heading boundaries, keeping source metadata."""
    lines = text.splitlines()
    chunks: list[Chunk] = []
    current: list[str] = []
    heading = ""

    def flush() -> None:
        nonlocal current
        if current:
            content = "\n".join(current).strip()
            if content:
                label = f"{source}#{heading}" if heading else source
                chunks.append(Chunk(content, label, len(chunks)))
            current = []

    for line in lines:
        stripped = line.strip()
        if stripped.startswith("#"):
            flush()
            heading = stripped.lstrip("#").strip()
            continue
        current.append(line)
        if len("\n".join(current)) >= MAX_CHUNK_CHARS:
            flush()
    flush()
    return chunks or [Chunk(text.strip(), source, 0)]


def chunk_json_lines(text: str, source: str) -> list[Chunk]:
    """Treat each JSON document as one chunk for rules files."""
    return [Chunk(text.strip(), source, 0)]
