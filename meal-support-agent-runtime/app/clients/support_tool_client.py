"""The ONLY outbound exit of the agent runtime: the Java bridge tool endpoint.

The bridge enforces session identity, role whitelist, parameter checks and its own
internal token; the runtime never talks to business services directly.
"""

import hmac

import httpx

from app.agent.context import AgentRunContext
from app.core.settings import Settings


class SupportToolClient:
    def __init__(self, settings: Settings):
        self.settings = settings
        self._http = httpx.Client(base_url=settings.support_bridge_url, timeout=10.0)

    def close(self) -> None:
        self._http.close()

    def invoke(self, context: AgentRunContext, tool_name: str, arguments: dict | None = None) -> dict:
        token = self.settings.support_internal_tool_token
        if not token:
            return {
                "success": False,
                "tool": tool_name,
                "data": None,
                "errorCode": "CONFIG",
                "errorMessage": "support_internal_tool_token is not configured",
            }
        headers = {"X-Internal-Token": token}
        payload = {
            "sessionId": context.session_id,
            "toolName": tool_name,
            "arguments": arguments or {},
        }
        response = self._http.post("/internal/support/tools/invoke", json=payload, headers=headers)
        if response.status_code != 200:
            return {
                "success": False,
                "tool": tool_name,
                "data": None,
                "errorCode": f"HTTP_{response.status_code}",
                "errorMessage": f"bridge returned {response.status_code}",
            }
        body = response.json()
        # mealflow Result<T> wrapper: {success, code, message, data}
        result = body.get("data")
        if not body.get("success") or not result:
            return {
                "success": False,
                "tool": tool_name,
                "data": None,
                "errorCode": body.get("code", "UNKNOWN"),
                "errorMessage": body.get("message", "bridge call failed"),
            }
        return result
