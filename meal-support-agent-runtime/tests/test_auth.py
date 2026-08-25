import hmac

import pytest
from fastapi import HTTPException
from fastapi.testclient import TestClient

from app.main import app


def test_health_is_public():
    client = TestClient(app)
    response = client.get("/health")
    assert response.status_code == 200
    assert response.json()["status"] == "ok"


def test_agent_chat_requires_token(monkeypatch):
    monkeypatch.setenv("AGENT_INTERNAL_TOKEN", "secret-token")
    from app.core import settings as settings_module

    settings_module.get_settings.cache_clear()
    client = TestClient(app)
    response = client.post(
        "/agent/chat",
        json={"sessionId": "s1", "message": "hi", "traceId": "t1", "userId": 1},
    )
    assert response.status_code == 401


def test_token_compare_constant_time():
    expected = "token-a"
    provided = "token-a"
    assert hmac.compare_digest(provided, expected)
    assert not hmac.compare_digest("token-b", expected)
