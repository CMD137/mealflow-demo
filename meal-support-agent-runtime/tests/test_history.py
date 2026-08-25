from app.memory.history import _window


def test_window_keeps_last_n_turns():
    messages = [
        {"role": "user", "content": "u1"},
        {"role": "assistant", "content": "a1"},
        {"role": "user", "content": "u2"},
        {"role": "assistant", "content": "a2"},
        {"role": "user", "content": "u3"},
        {"role": "assistant", "content": "a3"},
    ]
    result = _window(messages, 2)
    assert result == [
        {"role": "user", "content": "u2"},
        {"role": "assistant", "content": "a2"},
        {"role": "user", "content": "u3"},
        {"role": "assistant", "content": "a3"},
    ]


def test_window_ignores_orphan_replies():
    messages = [
        {"role": "assistant", "content": "orphan"},
        {"role": "user", "content": "u1"},
        {"role": "assistant", "content": "a1"},
    ]
    result = _window(messages, 5)
    assert result == [
        {"role": "user", "content": "u1"},
        {"role": "assistant", "content": "a1"},
    ]
