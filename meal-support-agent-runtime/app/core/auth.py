"""A4: inbound Bearer authentication for the Python agent runtime.

/health and / are public; every /agent/* route must pass this dependency with the
token the Java bridge was configured with (agent_internal_token).
"""

import hmac

from fastapi import Header, HTTPException, status

from app.core.settings import get_settings


def verify_agent_token(authorization: str | None = Header(default=None)) -> None:
    settings = get_settings()
    if not settings.agent_internal_token:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="AGENT_INTERNAL_TOKEN is not configured on the agent runtime",
        )
    if authorization is None or not authorization.startswith("Bearer "):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED, detail="missing bearer token"
        )
    provided = authorization[len("Bearer "):].strip()
    if not hmac.compare_digest(provided, settings.agent_internal_token):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="invalid token")
