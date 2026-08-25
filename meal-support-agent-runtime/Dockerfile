FROM python:3.11-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY app ./app
COPY knowledge ./knowledge
COPY scripts ./scripts

EXPOSE 8090

# Build the local RAG index on start (idempotent; skipped when embeddings are not configured)
# then serve the FastAPI app.
CMD ["sh", "-c", "python scripts/build_local_rag_index.py || echo 'RAG index build skipped (embeddings not configured)' && exec uvicorn app.main:app --host 0.0.0.0 --port 8090"]
