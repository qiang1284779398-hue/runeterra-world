from __future__ import annotations

import sqlite3
from datetime import datetime, timedelta, timezone

import pytest
from fastapi import HTTPException

from app import autonomy


@pytest.fixture
def isolated_db(tmp_path, monkeypatch):
    monkeypatch.setattr(autonomy, "DB_PATH", str(tmp_path / "autonomy-test.db"))
    yield


def actor(world="test-world", actor="yasuo"):
    return autonomy.RegisterActor(
        world_id=world,
        actor_id=actor,
        actor_name=actor.title(),
        character_context="独立角色档案",
        latest_observation="当前世界状态",
        interval_seconds=300,
    )


def test_private_memories_are_isolated(isolated_db):
    autonomy.write_memory(
        autonomy.MemoryWrite(world_id="w", actor_id="a", content="亚索曾经救过村民"),
        x_runeterra_token=None,
    )
    autonomy.write_memory(
        autonomy.MemoryWrite(world_id="w", actor_id="b", content="另一个人物加入诺克萨斯军团"),
        x_runeterra_token=None,
    )
    a = autonomy.recall_memory("w", "a", q="救过村民", x_runeterra_token=None)
    b = autonomy.recall_memory("w", "b", q="救过村民", x_runeterra_token=None)
    assert len(a["memories"]) == 1
    assert "亚索" in a["memories"][0]["content"]
    assert b["memories"] == []


def test_claim_lease_and_verified_outcome_learning(isolated_db):
    autonomy.register_actor(actor())
    with sqlite3.connect(autonomy.DB_PATH) as conn:
        conn.execute(
            "UPDATE autonomous_actors SET next_due_at=? WHERE actor_key=?",
            ((datetime.now(timezone.utc) - timedelta(seconds=1)).isoformat(), "test-world:yasuo"),
        )
    claimed = autonomy.claim_due(limit=1)
    assert len(claimed) == 1
    item = claimed[0]
    proposal_id = autonomy.save_proposal(item, 1, '{"intent":"帮助村民"}')
    result = autonomy.record_world_outcome(
        autonomy.WorldOutcome(
            proposal_id=proposal_id,
            world_id="test-world",
            actor_id="yasuo",
            heaven_verdict="approved",
            executed=True,
            outcome="世界引擎确认：村民获救",
            world_reward=0.7,
            authority="world_engine",
        ),
        x_runeterra_token=None,
    )
    assert result["learned"] is True
    memories = autonomy.recall_memory("test-world", "yasuo", q="村民获救", x_runeterra_token=None)
    assert any("村民获救" in m["content"] for m in memories["memories"])


def test_llm_cannot_forge_world_outcome(isolated_db):
    with pytest.raises(HTTPException) as error:
        autonomy.record_world_outcome(
            autonomy.WorldOutcome(
                proposal_id="fake",
                world_id="w",
                actor_id="a",
                heaven_verdict="approved",
                executed=True,
                outcome="模型声称成功",
                world_reward=1,
                authority="llm",
            ),
            x_runeterra_token=None,
        )
    assert error.value.status_code == 403


def test_denied_action_does_not_train_or_enter_memory(isolated_db):
    autonomy.register_actor(actor())
    with sqlite3.connect(autonomy.DB_PATH) as conn:
        conn.execute(
            "UPDATE autonomous_actors SET next_due_at=? WHERE actor_key=?",
            ((datetime.now(timezone.utc) - timedelta(seconds=1)).isoformat(), "test-world:yasuo"),
        )
    item = autonomy.claim_due(limit=1)[0]
    proposal_id = autonomy.save_proposal(item, 1, '{"intent":"违规行动"}')
    result = autonomy.record_world_outcome(
        autonomy.WorldOutcome(
            proposal_id=proposal_id,
            world_id="test-world",
            actor_id="yasuo",
            heaven_verdict="denied",
            executed=False,
            outcome="天道拒绝执行",
            world_reward=-1,
            authority="world_engine",
        ),
        x_runeterra_token=None,
    )
    assert result["learned"] is False
    memories = autonomy.recall_memory("test-world", "yasuo", q="天道拒绝", x_runeterra_token=None)
    assert memories["memories"] == []
