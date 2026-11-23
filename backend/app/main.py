from __future__ import annotations

import json
import os
import sqlite3
import threading
from datetime import datetime, timezone
from typing import Any, TypedDict

import httpx
from fastapi import FastAPI, Header, HTTPException
from pydantic import BaseModel, Field
from langchain_core.messages import HumanMessage, SystemMessage
from langchain_openai import ChatOpenAI
from langgraph.checkpoint.sqlite import SqliteSaver
from langgraph.graph import END, START, StateGraph

DB_PATH = os.getenv("RUNETERRA_DB", "/data/runeterra.db")
LETTA_BASE_URL = os.getenv("LETTA_BASE_URL", "https://api.letta.com").rstrip("/")
LETTA_API_KEY = os.getenv("LETTA_API_KEY", "")
LETTA_MODEL_HANDLE = os.getenv("LETTA_MODEL_HANDLE", "")
API_TOKEN = os.getenv("RUNETERRA_API_TOKEN", "")
ZHIPU_KEY = os.getenv("ZHIPU_API_KEY", "")
ZHIPU_BASE = os.getenv("ZHIPU_BASE_URL", "https://open.bigmodel.cn/api/paas/v4")
ZHIPU_MODEL = os.getenv("ZHIPU_MODEL", "glm-5.3-flashx")

app = FastAPI(title="Runeterra Cognitive Backend", version="0.1.0")
_db_lock = threading.Lock()
_actor_locks: dict[str, threading.Lock] = {}
_db_bootstrap = db()\n_checkpointer = SqliteSaver(sqlite3.connect(DB_PATH, check_same_thread=False))\n_checkpointer.setup()


def db() -> sqlite3.Connection:
    os.makedirs(os.path.dirname(DB_PATH) or ".", exist_ok=True)
    conn = sqlite3.connect(DB_PATH, timeout=30)
    conn.execute("CREATE TABLE IF NOT EXISTS letta_agents (actor_key TEXT PRIMARY KEY, agent_id TEXT NOT NULL)")
    conn.execute("CREATE TABLE IF NOT EXISTS traces (id INTEGER PRIMARY KEY AUTOINCREMENT, actor_key TEXT, day INTEGER, trace_json TEXT, created_at TEXT)")
    conn.commit()
    return conn


def auth(token: str | None) -> None:
    if API_TOKEN and token != API_TOKEN:
        raise HTTPException(status_code=401, detail="Invalid Runeterra backend token")


def model() -> ChatOpenAI:
    if not ZHIPU_KEY:
        raise HTTPException(status_code=503, detail="ZHIPU_API_KEY is not configured on backend")
    return ChatOpenAI(
        model=ZHIPU_MODEL,
        api_key=ZHIPU_KEY,
        base_url=ZHIPU_BASE,
        temperature=1.0,
        model_kwargs={"top_p": 0.95},
        timeout=120,
        max_retries=2,
    )


def actor_lock(key: str) -> threading.Lock:
    with _db_lock:
        return _actor_locks.setdefault(key, threading.Lock())


def letta_headers() -> dict[str, str]:
    headers = {"Content-Type": "application/json"}
    if LETTA_API_KEY:
        headers["Authorization"] = f"Bearer {LETTA_API_KEY}"
    return headers


def get_or_create_letta_agent(actor_key: str, display_name: str) -> str:
    with db() as conn:
        row = conn.execute("SELECT agent_id FROM letta_agents WHERE actor_key=?", (actor_key,)).fetchone()
    if row:
        return str(row[0])
    payload: dict[str, Any] = {
        "name": f"Runeterra-WorldMemory-{display_name[:32]}",
        "agent_type": "letta_v1_agent",
        "memory_blocks": [
            {"label": "persona", "value": f"你是符文之地世界的长期记忆管理员。你管理多个独立角色的记忆，必须严格按角色唯一标识隔离，不得把甲角色经历写入乙角色档案。"},
            {"label": "world_identity", "value": f"当前世界记忆空间：{actor_key}。每条记忆必须带有角色ID标签；查询和写入只允许触及请求中指定的角色ID。"},
        ],
    }
    if LETTA_MODEL_HANDLE:
        payload["model"] = LETTA_MODEL_HANDLE
    try:
        response = httpx.post(f"{LETTA_BASE_URL}/v1/agents/", json=payload, headers=letta_headers(), timeout=45)
        response.raise_for_status()
        agent_id = response.json()["id"]
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"Letta agent create failed: {exc}") from exc
    with db() as conn:
        conn.execute("INSERT OR REPLACE INTO letta_agents(actor_key,agent_id) VALUES(?,?)", (actor_key, agent_id))
        conn.commit()
    return str(agent_id)


def letta_recall(actor_key: str, display_name: str, query: str) -> str:
    agent_id = get_or_create_letta_agent(actor_key, display_name)
    payload = {
        "messages": [{"role": "user", "content": "作为长期记忆管理员，请根据该角色的持久记忆检索与当前情境有关的经历、人物关系、承诺、目标和教训。只返回有依据的记忆；没有就明确说没有。\n当前情境：\n" + query[:10000]}],
        "streaming": False,
        "max_steps": 3,
    }
    try:
        response = httpx.post(
            f"{LETTA_BASE_URL}/v1/agents/{agent_id}/messages",
            json=payload,
            headers=letta_headers(),
            timeout=120,
        )
        response.raise_for_status()
        messages = response.json().get("messages", [])
        texts = []
        for message in messages:
            if message.get("message_type") == "assistant_message":
                content = message.get("content", "")
                if isinstance(content, str) and content.strip():
                    texts.append(content.strip())
        return "\n".join(texts)[-12000:] or "Letta 暂无可用记忆摘要。"
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"Letta memory request failed: {exc}") from exc


def letta_store_experience(actor_key: str, display_name: str, day: int, experience: str) -> None:
    world_key = actor_key.split(":", 1)[0]\n    agent_id = get_or_create_letta_agent(world_key, "世界记忆管理员")\n    payload = {\n        "messages": [{\n            "role": "user",\n            "content": (\n                f"只允许写入角色ID={actor_key}的独立记忆空间。请将以下已发生的世界经历写入该角色的长期记忆。区分事实和推测，"
                f"保留人物关系、承诺、行动及结果，不要虚构。第{day}日经历：\\n"
                + experience[:10000]
            ),
        }],
        "streaming": False,
        "max_steps": 4,
    }
    try:
        response = httpx.post(
            f"{LETTA_BASE_URL}/v1/agents/{agent_id}/messages",
            json=payload,
            headers=letta_headers(),
            timeout=120,
        )
        response.raise_for_status()
    except Exception as exc:
        raise HTTPException(status_code=502, detail=f"Letta memory write failed: {exc}") from exc


class ThinkRequest(BaseModel):
    world_id: str = Field(min_length=1, max_length=100)
    actor_id: str = Field(min_length=1, max_length=120)
    actor_name: str = Field(min_length=1, max_length=100)
    day: int = Field(ge=0)
    character_context: str = Field(default="", max_length=16000)
    prompt: str = Field(min_length=1, max_length=20000)
    system_prompt: str = Field(default="", max_length=12000)


class CognitiveState(TypedDict, total=False):
    actor_key: str
    actor_name: str
    day: int
    character_context: str
    prompt: str
    system_prompt: str
    memories: str
    reflection: str
    plan: str
    output: str
    trace: list[dict[str, Any]]


def build_graph():
    llm = model()

    def recall_node(state: CognitiveState) -> dict[str, Any]:
        query = (
            f"角色背景：{state.get('character_context','')}\n"
            f"當前世界日：{state['day']}\n當前任務：{state['prompt']}"
        )
        memories = letta_recall(state["actor_key"], state["actor_name"], query)
        return {"memories": memories, "trace": state.get("trace", []) + [{"stage": "letta_recall", "ok": True}]}

    def reflect_node(state: CognitiveState) -> dict[str, Any]:
        prompt = f"""
你正在執行 Generative Agents 式人物認知循環中的 Reflection 階段。
只根據角色背景、持久記憶和當前世界狀態提煉重要反思，不輸出隱藏思維鏈。
角色：{state['actor_name']}
角色背景：{state.get('character_context','')}
持久記憶：{state.get('memories','')}
當前情境：{state['prompt']}
輸出簡潔的反思摘要：已知事實、未解問題、關係/目標變化、值得記住的教訓。
"""
        result = llm.invoke([SystemMessage(content="你是角色反思模块，只输出可验证的简短结论。"), HumanMessage(content=prompt)])
        return {"reflection": str(result.content), "trace": state.get("trace", []) + [{"stage": "generative_agents_reflection", "ok": True}]}

    def plan_node(state: CognitiveState) -> dict[str, Any]:
        prompt = f"""
執行 Generative Agents 式 Planning 階段。請為角色規劃當前一個可執行的小步驟。
角色：{state['actor_name']}
角色背景：{state.get('character_context','')}
長期記憶：{state.get('memories','')}
反思：{state.get('reflection','')}
當前情境：{state['prompt']}
輸出：當前目標、優先行動、所需條件、風險、如果無法行動的替代方案。不得虛構世界狀態。
"""
        result = llm.invoke([SystemMessage(content="你是人物规划器，给出可执行、保守且符合角色目标的计划。"), HumanMessage(content=prompt)])
        return {"plan": str(result.content), "trace": state.get("trace", []) + [{"stage": "generative_agents_planning", "ok": True}]}

    def act_node(state: CognitiveState) -> dict[str, Any]:
        system = state.get("system_prompt") or "你是符文之地世界中的人物，必须保持角色视角并输出合法JSON。"
        prompt = f"""
【人物连续认知上下文】
角色背景：{state.get('character_context','')}
Letta 持久记忆：{state.get('memories','')}
本轮反思：{state.get('reflection','')}
本轮计划：{state.get('plan','')}
世界日：{state['day']}
当前世界输入：{state['prompt']}
请生成角色此刻的最终决定。保留现有项目要求的 JSON 字段，不要输出 Markdown 或隐藏思维链。
"""
        result = llm.invoke([SystemMessage(content=system), HumanMessage(content=prompt)])
        output = result.content if isinstance(result.content, str) else json.dumps(result.content, ensure_ascii=False)
        return {"output": output, "trace": state.get("trace", []) + [{"stage": "zhipu_final_decision", "ok": True}]}

    def memory_commit_node(state: CognitiveState) -> dict[str, Any]:
        letta_store_experience(
            state["actor_key"], state["actor_name"], state["day"],
            "反思：" + state.get("reflection", "") + "\\n计划：" + state.get("plan", "") + "\\n最终决定与行动：" + state.get("output", ""),
        )
        return {"trace": state.get("trace", []) + [{"stage": "letta_memory_commit", "ok": True}]}

    graph = StateGraph(CognitiveState)
    graph.add_node("letta_recall", recall_node)
    graph.add_node("reflection", reflect_node)
    graph.add_node("planning", plan_node)
    graph.add_node("decision", act_node)
    graph.add_node("memory_commit", memory_commit_node)
    graph.add_edge(START, "letta_recall")
    graph.add_edge("letta_recall", "reflection")
    graph.add_edge("reflection", "planning")
    graph.add_edge("planning", "decision")
    graph.add_edge("decision", "memory_commit")
    graph.add_edge("memory_commit", END)
    return graph.compile(checkpointer=_checkpointer)


@app.get("/health")
def health():
    return {
        "ok": True,
        "frameworks": {
            "generative_agents": "adapted perception-memory-reflection-planning loop",
            "letta": bool(LETTA_BASE_URL),
            "langgraph": True,
            "deepeval": "CI evaluation dependency",
            "maseval": "CI multi-agent system evaluation dependency",
        },
        "zhipu_model": ZHIPU_MODEL,
    }


@app.post("/v1/think")
def think(request: ThinkRequest, x_runeterra_token: str | None = Header(default=None)):
    auth(x_runeterra_token)
    actor_key = f"{request.world_id}:{request.actor_id}"
    lock = actor_lock(actor_key)
    with lock:
        graph = build_graph()
        config = {"configurable": {"thread_id": actor_key}}
        initial: CognitiveState = {
            "actor_key": actor_key,
            "actor_name": request.actor_name,
            "day": request.day,
            "character_context": request.character_context,
            "prompt": request.prompt,
            "system_prompt": request.system_prompt,
            "trace": [],
        }
        try:
            result = graph.invoke(initial, config=config)
        except HTTPException:
            raise
        except Exception as exc:
            raise HTTPException(status_code=502, detail=f"Cognitive graph failed: {exc}") from exc
        trace = result.get("trace", [])
        trace.append({"stage": "completed", "at": datetime.now(timezone.utc).isoformat()})
        with db() as conn:
            conn.execute(
                "INSERT INTO traces(actor_key,day,trace_json,created_at) VALUES(?,?,?,?)",
                (actor_key, request.day, json.dumps(trace, ensure_ascii=False), datetime.now(timezone.utc).isoformat()),
            )
            conn.commit()
        return {
            "output": result.get("output", ""),
            "memory": result.get("memories", ""),
            "reflection": result.get("reflection", ""),
            "plan": result.get("plan", ""),
            "trace": trace,
            "frameworks": ["Generative Agents", "Letta", "LangGraph"],
            "evaluations": ["DeepEval", "MASEval"],
        }


@app.get("/v1/trace/{world_id}/{actor_id}")
def get_trace(world_id: str, actor_id: str, x_runeterra_token: str | None = Header(default=None)):
    auth(x_runeterra_token)
    actor_key = f"{world_id}:{actor_id}"
    with db() as conn:
        rows = conn.execute(
            "SELECT day,trace_json,created_at FROM traces WHERE actor_key=? ORDER BY id DESC LIMIT 50",
            (actor_key,),
        ).fetchall()
    return {"actor_key": actor_key, "traces": [
        {"day": row[0], "trace": json.loads(row[1]), "created_at": row[2]} for row in rows
    ]}
