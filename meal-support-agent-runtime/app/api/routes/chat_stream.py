import json
from collections.abc import Iterator

from fastapi import APIRouter, Depends
from fastapi.responses import StreamingResponse

from app.agent.context import AgentRunContext
from app.core.auth import verify_agent_token
from app.deps import get_stream_executor
from app.schemas.chat import AgentChatRequest

router = APIRouter(prefix="/agent", dependencies=[Depends(verify_agent_token)])


@router.post("/chat/stream")
def chat_stream(request: AgentChatRequest) -> StreamingResponse:
    context = AgentRunContext(
        session_id=request.sessionId,
        user_id=request.userId,
        role=request.role,
        trace_id=request.traceId,
        channel=request.channel,
        permissions=request.permissions,
    )

    def event_stream() -> Iterator[str]:
        try:
            for event in get_stream_executor().run(context, request.message):
                data = event.get("data", "")
                if isinstance(data, (dict, list)):
                    data = json.dumps(data, ensure_ascii=False)
                yield f"event: {event.get('event', 'message')}\ndata: {data}\n\n"
        except Exception as exc:  # noqa: BLE001 - the stream must never hang the browser
            yield f"event: error\ndata: {json.dumps(str(exc), ensure_ascii=False)}\n\n"

    return StreamingResponse(event_stream(), media_type="text/event-stream")
