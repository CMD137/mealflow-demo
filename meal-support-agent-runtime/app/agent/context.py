from dataclasses import dataclass, field


@dataclass
class AgentRunContext:
    """Identity context for one agent run. Always sourced from the Java bridge,
    which in turn sourced it from the server-side session - never from the model."""

    session_id: str
    user_id: int
    role: str
    trace_id: str
    channel: str = "mealflow-support"
    permissions: list[str] = field(default_factory=list)
