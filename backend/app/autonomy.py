from __future__ import annotations

import json
import os
import re
import sqlite3
import threading
import uuid
from datetime import datetime, timedelta, timezone
from typing import Any

from fastapi import APIRouter, Header, HTTPException
from pydantic import BaseModel, Field

DB_PATH = os.getenv("RUNETERRA_DB", "/data/runeterra.db")
API_TOKEN = os.getenv("RUNETERRA_API_TOKEN", "")
_router_lock = threading.RLock()
router = APIRouter(prefix="/v1/autonomy", tags=["autonomy"])


def _now() -> datetime:
    return datetime.now(timezone.utc)


def _iso(value: datetime) -> str:
    return value.astimezone(timezone.utc).isoformat()


def _connect() -> sqlite3.Connection:
    os.makedirs(os.path.dirname(DB_PATH) or ".", exist_ok=True)
    conn = sqlite3.connect(DB_PATH, timeout=30, isolation_level=None)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA busy_timeout=30000")
    conn.execute("""
        CREATE TABLE IF NOT EXISTS autonomous_actors (
            actor_key TEXT PRIMARY KEY, world_id TEXT NOT NULL, actor_id TEXT NOT NULL,
            actor_name TEXT NOT NULL, character_context TEXT NOT NULL DEFAULT '',
            latest_observation TEXT NOT NULL DEFAULT '', system_prompt TEXT NOT NULL DEFAULT '',
            interval_seconds INTEGER NOT NULL DEFAULT 300, enabled INTEGER NOT NULL DEFAULT 1,
            next_due_at TEXT NOT NULL, lease_until TEXT, lease_token TEXT,
            last_proposed_at TEXT, created_at TEXT NOT NULL, updated_at TEXT NOT NULL
        )
    """)
    conn.execute("""
        CREATE TABLE IF NOT EXISTS private_memories (
            id INTEGER PRIMARY KEY AUTOINCREMENT, actor_key TEXT NOT NULL,
            memory_type TEXT NOT NULL, content TEXT NOT NULL, importance REAL NOT NULL DEFAULT 0.5,
            source TEXT NOT NULL, created_at TEXT NOT NULL
        )
    """)
    conn.execute("CREATE INDEX IF NOT EXISTS idx_private_memories_actor ON private_memories(actor_key,id DESC)")
    conn.execute("""
        CREATE TABLE IF NOT EXISTS action_proposals (
            proposal_id TEXT PRIMARY KEY, actor_key TEXT NOT NULL, world_id TEXT NOT NULL,
            actor_id TEXT NOT NULL, day INTEGER NOT NULL, proposal TEXT NOT NULL,
            status TEXT NOT NULL DEFAULT 'pending', created_at TEXT NOT NULL,
            resolved_at TEXT
        )
    """)
    conn.execute("CREATE INDEX IF NOT EXISTS idx_proposals_pending ON action_proposals(world_id,status,created_at)")
    conn.execute("""
        CREATE TABLE IF NOT EXISTS verified_outcomes (
            id INTEGER PRIMARY KEY AUTOINCREMENT, proposal_id TEXT NOT NULL UNIQUE,
            actor_key TEXT NOT NULL, verdict TEXT NOT NULL, executed INTEGER NOT NULL,
            outcome TEXT NOT NULL, reward REAL NOT NULL, source TEXT NOT NULL,
            created_at TEXT NOT NULL
        )
    """)
    return conn


def _auth(token: str | None) -> None:
    if API_TOKEN and token != API_TOKEN:
        raise HTTPException(status_code=401, detail="Invalid Runeterra backend token")


def actor_key(world_id: str, actor_id: str) -> str:
    return f"{world_id}:{actor_id}"


class RegisterActor(BaseModel):
    world_id: str = Field(min_length=1, max_length=100)
    actor_id: str = Field(min_length=1, max_length=120)
    actor_name: str = Field(min_length=1, max_length=100)
    character_context: str = Field(default="", max_length=16000)
    latest_observation: str = Field(default="", max_length=20000)
    system_prompt: str = Field(default="", max_length=12000)
    interval_seconds: int = Field(default=300, ge=30, le=86400)
    enabled: bool = True


class MemoryWrite(BaseModel):
    world_id: str = Field(min_length=1, max_length=100)
    actor_id: str = Field(min_length=1, max_length=120)
    memory_type: str = Field(default="episodic", pattern="^(working|episodic|semantic|procedural|core)$")
    content: str = Field(min_length=1, max_length=12000)
    importance: float = Field(default=0.5, ge=0, le=1)
    source: str = Field(default="world_engine", pattern="^(world_engine|reflection|user_import)$")


class WorldOutcome(BaseModel):
    proposal_id: str = Field(min_length=1, max_length=80)
    world_id: str = Field(min_length=1, max_length=100)
    actor_id: str = Field(min_length=1, max_length=120)
    heaven_verdict: str = Field(pattern="^(approved|denied|modified|pending)$")
    executed: bool
    outcome: str = Field(default="", max_length=12000)
    world_reward: float = Field(default=0, ge=-1, le=1)
    authority: str = Field(default="world_engine", pattern="^(world_engine|llm|client)$")


def register_actor(data: RegisterActor) -> dict[str, Any]:
    now = _now()
    key = actor_key(data.world_id, data.actor_id)
    with _router_lock, _connect() as conn:
        conn.execute("""
            INSERT INTO autonomous_actors(
                actor_key,world_id,actor_id,actor_name,character_context,latest_observation,
                system_prompt,interval_seconds,enabled,next_due_at,created_at,updated_at
            ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(actor_key) DO UPDATE SET
                actor_name=excluded.actor_name, character_context=excluded.character_context,
                latest_observation=excluded.latest_observation, system_prompt=excluded.system_prompt,
                interval_seconds=excluded.interval_seconds, enabled=excluded.enabled,
                updated_at=excluded.updated_at
        """, (key,data.world_id,data.actor_id,data.actor_name,data.character_context,
              data.latest_observation,data.system_prompt,data.interval_seconds,int(data.enabled),
              _iso(now),_iso(now),_iso(now)))
        row = conn.execute("SELECT next_due_at FROM autonomous_actors WHERE actor_key=?", (key,)).fetchone()
    return {"actor_key": key, "enabled": data.enabled, "next_due_at": row["next_due_at"]}


def claim_due(limit: int = 20, lease_seconds: int = 180) -> list[dict[str, Any]]:
    now = _now()
    lease_until = _iso(now + timedelta(seconds=lease_seconds))
    claimed: list[dict[str, Any]] = []
    with _router_lock, _connect() as conn:
        conn.execute("BEGIN IMMEDIATE")
        rows = conn.execute("""
            SELECT * FROM autonomous_actors
            WHERE enabled=1 AND next_due_at<=?
              AND (lease_until IS NULL OR lease_until<=?)
            ORDER BY next_due_at ASC LIMIT ?
        """, (_iso(now),_iso(now),max(1,min(limit,100)))).fetchall()
        for row in rows:
            token = uuid.uuid4().hex
            conn.execute("""
                UPDATE autonomous_actors SET lease_until=?,lease_token=?,updated_at=?
                WHERE actor_key=?
            """, (lease_until,token,_iso(now),row["actor_key"]))
            item = dict(row)
            item["lease_token"] = token
            claimed.append(item)
        conn.commit()
    return claimed


def save_proposal(actor: dict[str, Any], day: int, proposal: str) -> str:
    proposal_id = uuid.uuid4().hex
    now = _iso(_now())
    with _router_lock, _connect() as conn:
        conn.execute("""
            INSERT INTO action_proposals(proposal_id,actor_key,world_id,actor_id,day,proposal,status,created_at)
            VALUES(?,?,?,?,?,?,?,?)
        """, (proposal_id,actor["actor_key"],actor["world_id"],actor["actor_id"],day,proposal,"pending",now))
        conn.execute("""
            UPDATE autonomous_actors SET last_proposed_at=?, next_due_at=?, lease_until=NULL,
                lease_token=NULL, updated_at=? WHERE actor_key=? AND lease_token=?
        """, (now,_iso(_now()+timedelta(seconds=int(actor["interval_seconds"]))),
              now,actor["actor_key"],actor["lease_token"]))
    return proposal_id


def release_lease(actor: dict[str, Any], retry_seconds: int = 60) -> None:
    now = _now()
    with _router_lock, _connect() as conn:
        conn.execute("""
            UPDATE autonomous_actors SET lease_until=NULL,lease_token=NULL,next_due_at=?,updated_at=?
            WHERE actor_key=? AND lease_token=?
        """, (_iso(now+timedelta(seconds=retry_seconds)),_iso(now),
              actor["actor_key"],actor["lease_token"]))


@router.post("/register")
def register_endpoint(data: RegisterActor, x_runeterra_token: str | None = Header(default=None)):
    _auth(x_runeterra_token)
    return register_actor(data)


@router.post("/memory")
def write_memory(data: MemoryWrite, x_runeterra_token: str | None = Header(default=None)):
    _auth(x_runeterra_token)
    now = _iso(_now())
    key = actor_key(data.world_id, data.actor_id)
    with _router_lock, _connect() as conn:
        conn.execute("""
            INSERT INTO private_memories(actor_key,memory_type,content,importance,source,created_at)
            VALUES(?,?,?,?,?,?)
        """, (key,data.memory_type,data.content,data.importance,data.source,now))
        conn.execute("""
            DELETE FROM private_memories WHERE actor_key=? AND id NOT IN
            (SELECT id FROM private_memories WHERE actor_key=? ORDER BY id DESC LIMIT 5000)
        """, (key,key))
    return {"stored": True, "actor_key": key, "memory_type": data.memory_type}


@router.get("/memory/{world_id}/{actor_id}")
def recall_memory(world_id: str, actor_id: str, q: str = "", limit: int = 20,
                  x_runeterra_token: str | None = Header(default=None)):
    _auth(x_runeterra_token)
    key = actor_key(world_id, actor_id)
    terms = {t for t in re.findall(r"[\w\u4e00-\u9fff]{2,}", q.lower())}
    with _connect() as conn:
        rows = conn.execute("""
            SELECT memory_type,content,importance,source,created_at FROM private_memories
            WHERE actor_key=? ORDER BY id DESC LIMIT 500
        """, (key,)).fetchall()
    scored = []
    for row in rows:
        text = row["content"].lower()
        overlap = sum(1 for term in terms if term in text)
        score = float(row["importance"]) + overlap * 0.25
        if not terms or overlap:
            scored.append((score,dict(row)))
    scored.sort(key=lambda x: x[0], reverse=True)
    return {"actor_key": key, "memories": [item for _,item in scored[:max(1,min(limit,100))]]}


@router.get("/pending/{world_id}/{actor_id}")
def pending_proposals(world_id: str, actor_id: str,
                      x_runeterra_token: str | None = Header(default=None)):
    _auth(x_runeterra_token)
    with _connect() as conn:
        rows = conn.execute("""
            SELECT proposal_id,day,proposal,created_at FROM action_proposals
            WHERE world_id=? AND actor_id=? AND status='pending' ORDER BY created_at ASC LIMIT 20
        """, (world_id,actor_id)).fetchall()
    return {"proposals": [dict(row) for row in rows]}


@router.post("/outcome")
def record_world_outcome(data: WorldOutcome, x_runeterra_token: str | None = Header(default=None)):
    _auth(x_runeterra_token)
    if data.authority != "world_engine":
        raise HTTPException(status_code=403, detail="Only the authoritative world engine may submit outcomes")
    if data.heaven_verdict != "approved" or not data.executed:
        with _router_lock, _connect() as conn:
            conn.execute("UPDATE action_proposals SET status='rejected' WHERE proposal_id=? AND world_id=? AND actor_id=?",
                         (data.proposal_id,data.world_id,data.actor_id))
        return {"learned": False, "reason": "Action was not approved and executed by the world engine"}
    key = actor_key(data.world_id, data.actor_id)
    now = _iso(_now())
    try:
        with _router_lock, _connect() as conn:
            proposal = conn.execute("""
                SELECT proposal_id FROM action_proposals
                WHERE proposal_id=? AND actor_key=? AND status='pending'
            """, (data.proposal_id,key)).fetchone()
            if proposal is None:
                raise HTTPException(status_code=404, detail="Pending proposal not found for this actor")
            conn.execute("""
                INSERT INTO verified_outcomes(proposal_id,actor_key,verdict,executed,outcome,reward,source,created_at)
                VALUES(?,?,?,?,?,?,?,?)
            """, (data.proposal_id,key,data.heaven_verdict,1,data.outcome,data.world_reward,data.authority,now))
            conn.execute("UPDATE action_proposals SET status='executed',resolved_at=? WHERE proposal_id=?",
                         (now,data.proposal_id))
            if data.outcome.strip():
                conn.execute("""
                    INSERT INTO private_memories(actor_key,memory_type,content,importance,source,created_at)
                    VALUES(?,?,?,?,?,?)
                """, (key,"episodic",data.outcome,0.8,"world_engine",now))
    except sqlite3.IntegrityError:
        raise HTTPException(status_code=409, detail="Outcome already recorded")
    return {"learned": True, "actor_key": key, "reward": data.world_reward,
            "source": "verified_world_engine", "outcome_idempotency": data.proposal_id}


@router.post("/claim")
def claim_endpoint(limit: int = 20, lease_seconds: int = 180,
                   x_runeterra_token: str | None = Header(default=None)):
    _auth(x_runeterra_token)
    return {"actors": claim_due(limit, lease_seconds)}


class SubmitProposal(BaseModel):
    world_id: str = Field(min_length=1, max_length=100)
    actor_id: str = Field(min_length=1, max_length=120)
    lease_token: str = Field(min_length=16, max_length=80)
    day: int = Field(ge=0)
    proposal: str = Field(min_length=1, max_length=20000)


@router.post("/proposal")
def submit_proposal(data: SubmitProposal, x_runeterra_token: str | None = Header(default=None)):
    _auth(x_runeterra_token)
    key = actor_key(data.world_id, data.actor_id)
    with _router_lock, _connect() as conn:
        row = conn.execute(
            "SELECT * FROM autonomous_actors WHERE actor_key=? AND lease_token=? AND lease_until>?",
            (key, data.lease_token, _iso(_now())),
        ).fetchone()
        if row is None:
            raise HTTPException(status_code=409, detail="Actor lease is missing, expired, or belongs to another worker")
        actor = dict(row)
        actor["lease_token"] = data.lease_token
        proposal_id = save_proposal(actor, data.day, data.proposal)
    return {"proposal_id": proposal_id, "status": "pending", "awaiting": "heaven_verdict_and_world_execution"}
