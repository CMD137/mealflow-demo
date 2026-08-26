from types import SimpleNamespace

from app.agent.context import AgentRunContext
from app.agent.executor import AgentExecutor
from app.core.settings import Settings


class FakeHistory:
    def __init__(self):
        self.messages: list[dict] = []

    def append(self, session_id, role, content):
        self.messages.append({"role": role, "content": content})

    def get_messages(self, session_id):
        return list(self.messages)


class FakeLlm:
    model = "fake-model"

    def __init__(self):
        self.calls = 0

    def chat(self, messages, tools=None):
        self.calls += 1
        if self.calls == 1:
            return SimpleNamespace(
                choices=[
                    SimpleNamespace(
                        finish_reason="tool_calls",
                        message=SimpleNamespace(
                            content=None,
                            tool_calls=[
                                SimpleNamespace(
                                    id="call_1",
                                    function=SimpleNamespace(
                                        name="get_user_orders", arguments="{}"
                                    ),
                                )
                            ],
                        ),
                    )
                ]
            )
        return SimpleNamespace(
            choices=[
                SimpleNamespace(
                    finish_reason="stop",
                    message=SimpleNamespace(content="您当前有 2 笔订单。", tool_calls=[]),
                )
            ]
        )


def fake_tool_executor(context, tool_name, arguments):
    assert context.session_id == "s1"
    return '{"success": true, "tool": "get_user_orders", "data": {"orders": [1, 2]}}'


def test_executor_loop_tool_then_answer():
    history = FakeHistory()
    llm = FakeLlm()
    settings = Settings(agent_max_steps=4)
    executor = AgentExecutor(llm, history, fake_tool_executor, settings)
    context = AgentRunContext(session_id="s1", user_id=1, role="CUSTOMER", trace_id="t1")
    result = executor.run(context, "我的订单呢")
    assert result.answer == "您当前有 2 笔订单。"
    assert result.used_tools == ["get_user_orders"]
    assert result.model_name == "fake-model"
    # user + assistant persisted to history
    assert [m["role"] for m in history.messages] == ["user", "assistant"]


def test_executor_max_steps_fallback():
    history = FakeHistory()

    class LoopLlm:
        model = "fake-model"

        def chat(self, messages, tools=None):
            return SimpleNamespace(
                choices=[
                    SimpleNamespace(
                        finish_reason="tool_calls",
                        message=SimpleNamespace(
                            content=None,
                            tool_calls=[
                                SimpleNamespace(
                                    id="call_x",
                                    function=SimpleNamespace(name="get_user_orders", arguments="{}"),
                                )
                            ],
                        ),
                    )
                ]
            )

    settings = Settings(agent_max_steps=2)
    executor = AgentExecutor(LoopLlm(), history, fake_tool_executor, settings)
    context = AgentRunContext(session_id="s1", user_id=1, role="CUSTOMER", trace_id="t1")
    result = executor.run(context, "hi")
    assert result.used_tools == ["get_user_orders", "get_user_orders"]
    assert "暂时无法" in result.answer
