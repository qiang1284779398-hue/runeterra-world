# Runeterra cognitive backend

This service is the bridge between the Android world simulator and the five upstream projects.

## Integration roles

- **Generative Agents**: the cognitive loop (perception, memory retrieval, reflection, planning, action selection). The original Stanford repository is a research demo tied to a Django/maze environment and an OpenAI-era setup; its agent loop is adapted to Runeterra's world-state contract rather than importing its unrelated demo world.
- **Letta**: persistent per-character stateful memory. The service creates/uses one Letta agent per Runeterra character and stores the mapping locally.
- **LangGraph**: executes the cognition graph and checkpoints each run.
- **DeepEval**: CI tests for role adherence, memory retention, continuity and task completion.
- **MASEval**: system-level multi-agent benchmark adapter over recorded Runeterra traces.

GLM-5.3-FlashX remains the reasoning model through Zhipu's OpenAI-compatible API. DeepEval and MASEval are evaluation tools, not runtime cognition engines.

## Important deployment boundary

The Android APK cannot host these Python services. Deploy this backend and a Letta App Server on a reachable machine/container host, configure the environment variables, then set the backend URL/token in the Android app. The APK continues to work in direct-Zhipu mode when the backend is not configured. Do not put API keys in this repository.

The upstream Stanford Generative Agents repository is Apache-2.0 and its original demo expects a separate Django environment and legacy OpenAI configuration. This service adapts its cognition loop to the existing Runeterra simulation rather than launching the unrelated Smallville frontend.

## Run locally

```bash
cd backend
python -m venv .venv
. .venv/bin/activate
pip install -e '.[dev]'
cp .env.example .env
# Fill ZHIPU_API_KEY, LETTA_BASE_URL, LETTA_API_KEY, RUNETERRA_API_TOKEN
uvicorn app.main:app --host 0.0.0.0 --port 8080
```

Health: `GET /health`. Cognition: `POST /v1/think`.

## Current integration contract

The backend accepts one character turn with a stable `actor_id`, world day, character context and current prompt. It returns the model response plus framework trace and evaluation metadata. Character IDs are namespaced by world ID to prevent memory leakage between worlds.
