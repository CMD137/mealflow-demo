from dataclasses import dataclass, field


@dataclass
class AgentToolCall:
    name: str
    arguments: dict = field(default_factory=dict)


@dataclass
class AgentToolResult:
    tool: str
    content: str
    success: bool = True


@dataclass
class AgentExecutionResult:
    answer: str
    used_tools: list[str] = field(default_factory=list)
    citations: list[dict] = field(default_factory=list)
    model_name: str = ""
    llm_elapsed_ms: int | None = None
    tool_elapsed_ms: int | None = None
