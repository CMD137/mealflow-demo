import httpx

from app.agent.context import AgentRunContext
from app.clients.support_tool_client import SupportToolClient
from app.core.settings import Settings


class _Transport(httpx.MockTransport):
    def __init__(self):
        super().__init__(self._handler)

    def _handler(self, request: httpx.Request) -> httpx.Response:
        assert request.headers.get("X-Internal-Token") == "tool-secret"
        assert request.url.path == "/internal/support/tools/invoke"
        return httpx.Response(
            200,
            json={
                "success": True,
                "code": "OK",
                "message": "成功",
                "data": {
                    "success": True,
                    "tool": "get_user_orders",
                    "data": {"orders": []},
                    "errorCode": None,
                    "errorMessage": None,
                },
            },
        )


def test_invoke_forwards_token_and_parses_result():
    settings = Settings(support_bridge_url="http://bridge", support_internal_token="tool-secret")
    client = SupportToolClient(settings)
    client._http = httpx.Client(transport=_Transport(), base_url="http://bridge")
    context = AgentRunContext(session_id="s1", user_id=1, role="CUSTOMER", trace_id="t1")
    result = client.invoke(context, "get_user_orders", {})
    assert result["success"] is True
    assert result["tool"] == "get_user_orders"
    assert result["data"] == {"orders": []}


def test_invoke_fails_closed_without_token():
    settings = Settings(support_bridge_url="http://bridge", support_internal_token="")
    client = SupportToolClient(settings)
    context = AgentRunContext(session_id="s1", user_id=1, role="CUSTOMER", trace_id="t1")
    result = client.invoke(context, "get_user_orders", {})
    assert result["success"] is False
    assert result["errorCode"] == "CONFIG"
