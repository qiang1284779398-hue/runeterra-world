from __future__ import annotations

import os
from typing import Any, TypedDict

from langchain_core.messages import AIMessage, HumanMessage
from langgraph.graph import END, START, MessagesState, StateGraph
from maseval.interface.agents.langgraph import LangGraphAgentAdapter

from app.main import ThinkRequest, build_graph


class EvalState(MessagesState):
    pass


def main() -> int:
    actor_id = os.getenv("MASEVAL_ACTOR_ID", "maseval-resident-01")
    world_id = os.getenv("MASEVAL_WORLD_ID", "maseval-world")
    character_graph = build_graph()

    def run_character(state: EvalState) -> dict[str, Any]:
        last = state["messages"][-1]
        query = last.content if hasattr(last, "content") else str(last)
        result = character_graph.invoke(
            {
                "actor_key": f"{world_id}:{actor_id}",
                "actor_name": "测试居民艾伦",
                "day": 12,
                "character_context": "艾伦是皮尔特沃夫的一名普通修理工，有姐姐米娅。",
                "prompt": str(query),
                "system_prompt": "你是符文之地的居民艾伦，只输出角色视角的最终决定。",
                "trace": [],
            },
            config={"configurable": {"thread_id": f"{world_id}:{actor_id}"}},
        )
        return {"messages": [AIMessage(content=str(result.get("output", "")))]}

    builder = StateGraph(EvalState)
    builder.add_node("run_character", run_character)
    builder.add_edge(START, "run_character")
    builder.add_edge("run_character", END)
    evaluation_graph = builder.compile()
    adapter = LangGraphAgentAdapter(
        evaluation_graph,
        "Runeterra-GLM-Letta-GenerativeAgents",
        model_id=os.getenv("ZHIPU_MODEL", "glm-5.3-flashx"),
    )
    adapter.run("今天姐姐米娅遇到困难，结合我的承诺和经历决定下一步。")
    print("MASEval adapter trace:")
    print(adapter.gather_traces())
    print("Usage:", adapter.gather_usage())
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
