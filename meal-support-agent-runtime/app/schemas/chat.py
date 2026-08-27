from pydantic import BaseModel, Field


class AgentChatRequest(BaseModel):
    sessionId: str = Field(..., min_length=1)
    message: str = Field(..., min_length=1)
    traceId: str = ""
    userId: int = 0
    role: str = "CUSTOMER"
    permissions: list[str] = Field(default_factory=list)
    channel: str = "mealflow-support"


class Citation(BaseModel):
    source: str = ""
    chunkIndex: str = ""
    score: float | None = None
    content: str = ""


class AgentChatResponse(BaseModel):
    sessionId: str
    answer: str
    usedTools: list[str] = Field(default_factory=list)
    citations: list[Citation] = Field(default_factory=list)
    traceId: str = ""
    modelName: str = ""
    llmElapsedMs: int | None = None
    toolElapsedMs: int | None = None
