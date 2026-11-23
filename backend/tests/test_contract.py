from app.main import ThinkRequest


def test_think_request_keeps_world_and_actor_isolated():
    request = ThinkRequest(
        world_id="world-a",
        actor_id="resident-7",
        actor_name="艾伦",
        day=3,
        character_context="普通居民",
        prompt="今天我准备去市场。",
    )
    assert request.world_id == "world-a"
    assert request.actor_id == "resident-7"
    assert request.day == 3
