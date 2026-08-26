import hashlib
import hmac

import httpx

from app.agent.context import AgentRunContext
from app.clients.support_tool_client import SupportToolClient, _sign
from app.core.settings import Settings


class _Transport(httpx.MockTransport):
    def __init__(self):
        super().__init__(self._handler)

    def _handler(self, request: httpx.Request) -> httpx.Response:
        assert request.headers.get("X-Internal-Token") == "tool-secret"
        assert request.url.path == "/internal/support/tools/invoke"
        # A5: the request must carry a valid HMAC service signature
        timestamp = request.headers.get("X-Internal-Timestamp")
        nonce = request.headers.get("X-Internal-Nonce")
        assert timestamp and nonce
        canonical = "\n".join([
            "meal-support-agent-runtime", "POST", request.url.path, "", timestamp, nonce,
        ])
        expected = hmac.new(b"test-secret", canonical.encode("utf-8"), hashlib.sha256).hexdigest()
        assert request.headers.get("X-Internal-Signature") == expected
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
    settings = Settings(
        support_bridge_url="http://bridge",
        support_internal_tool_token="tool-secret",
        internal_secret="test-secret",
    )
    client = SupportToolClient(settings)
    client._http = httpx.Client(transport=_Transport(), base_url="http://bridge")
    context = AgentRunContext(session_id="s1", user_id=1, role="CUSTOMER", trace_id="t1")
    result = client.invoke(context, "get_user_orders", {})
    assert result["success"] is True
    assert result["tool"] == "get_user_orders"
    assert result["data"] == {"orders": []}


def test_invoke_fails_closed_without_token():
    settings = Settings(support_bridge_url="http://bridge", support_internal_tool_token="")
    client = SupportToolClient(settings)
    context = AgentRunContext(session_id="s1", user_id=1, role="CUSTOMER", trace_id="t1")
    result = client.invoke(context, "get_user_orders", {})
    assert result["success"] is False
    assert result["errorCode"] == "CONFIG"


def test_sign_matches_java_canonical_form():
    settings = Settings(internal_secret="s", internal_service_name="svc")
    assert _sign(settings, "POST", "/path", "a=1", "123", "n") == hmac.new(
        b"s", b"svc\nPOST\n/path\na=1\n123\nn", hashlib.sha256
    ).hexdigest()
