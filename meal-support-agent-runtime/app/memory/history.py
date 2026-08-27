"""Redis-backed multi-turn chat history with windowing (A3).

Messages are stored as a JSON list per session (key_prefix + session id, TTL).
The window keeps only the most recent N human turns together with their assistant
replies so the LLM context never grows unboundedly.
"""

import json

import redis

from app.core.settings import Settings


class RedisChatHistory:
    def __init__(self, settings: Settings):
        self._redis = redis.Redis.from_url(settings.redis_url, decode_responses=True)
        self._prefix = settings.memory_key_prefix
        self._ttl = settings.memory_ttl_seconds
        self._max_turns = settings.history_max_turns

    def _key(self, session_id: str) -> str:
        return f"{self._prefix}{session_id}"

    def append(self, session_id: str, role: str, content: str) -> None:
        key = self._key(session_id)
        self._redis.rpush(key, json.dumps({"role": role, "content": content}, ensure_ascii=False))
        self._redis.expire(key, self._ttl)
        self._trim(key)

    def get_messages(self, session_id: str, max_turns: int | None = None) -> list[dict]:
        raw = self._redis.lrange(self._key(session_id), 0, -1)
        messages = [json.loads(item) for item in raw]
        return _window(messages, max_turns or self._max_turns)

    def clear(self, session_id: str) -> None:
        self._redis.delete(self._key(session_id))

    def _trim(self, key: str) -> None:
        raw = self._redis.lrange(key, 0, -1)
        messages = [json.loads(item) for item in raw]
        windowed = _window(messages, self._max_turns)
        if len(windowed) == len(messages):
            return
        self._redis.delete(key)
        if windowed:
            self._redis.rpush(key, *[json.dumps(m, ensure_ascii=False) for m in windowed])
            self._redis.expire(key, self._ttl)


def _window(messages: list[dict], max_turns: int) -> list[dict]:
    """Keep the last max_turns user turns including their following replies."""
    if max_turns <= 0:
        return []
    turns: list[list[dict]] = []
    for message in messages:
        if message.get("role") == "user":
            turns.append([])
        if turns:
            turns[-1].append(message)
    selected = turns[-max_turns:]
    return [message for turn in selected for message in turn]
