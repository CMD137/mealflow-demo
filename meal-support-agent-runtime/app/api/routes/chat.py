from fastapi import APIRouter, Depends

from app.agent.context import AgentRunContext
from app.core.auth import verify_agent_token
from app.deps import get_executor
from app.schemas.chat import AgentChatRequest, AgentChatResponse, Citation

router = APIRouter(prefix="/agent", dependencies=[Depends(verify_agent_token)])


@router.post("/chat", response_model=AgentChatResponse)
def chat(request: AgentChatRequest) -> AgentChatResponse:
    context = AgentRunContext(
        session_id=request.sessionId,
        user_id=request.userId,
        role=request.role,
        trace_id=request.traceId,
        channel=request.channel,
        permissions=request.permissions,
    )
    result = get_executor().run(context, request.message)
    return AgentChatResponse(
        sessionId=request.sessionId,
        answer=result.answer,
        usedTools=result.used_tools,
        citations=[Citation(**citation) for citation in result.citations],
        traceId=request.traceId,
        modelName=result.model_name,
        llmElapsedMs=result.llm_elapsed_ms,
        toolElapsedMs=result.tool_elapsed_ms,
    )
