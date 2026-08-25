from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.routes import chat, chat_stream
from app.core.settings import get_settings


@asynccontextmanager
async def lifespan(_: FastAPI):
    yield


def create_app() -> FastAPI:
    app = FastAPI(title="MealFlow Support Agent Runtime", version="0.1.0", lifespan=lifespan)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=[
            "http://localhost:5173",
            "http://127.0.0.1:5173",
            "http://localhost:5174",
            "http://127.0.0.1:5174",
            "http://localhost:8080",
        ],
        allow_credentials=True,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    app.include_router(chat.router)
    app.include_router(chat_stream.router)

    @app.get("/")
    def root():
        return {"service": "meal-support-agent-runtime", "status": "ok"}

    @app.get("/health")
    def health():
        settings = get_settings()
        return {
            "status": "ok",
            "llmConfigured": bool(settings.llm_api_key),
            "embeddingConfigured": bool(settings.embedding_api_key),
            "agentTokenConfigured": bool(settings.agent_internal_token),
        }

    return app


app = create_app()
