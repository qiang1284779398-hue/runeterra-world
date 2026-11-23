# Runeterra cognitive backend

This service is the bridge between the Android world simulator and the five upstream projects.

## Integration roles

- **Generative Agents**: the original Stanford repository is pinned as a Git submodule at `vendor/generative_agents`. Its original Smallville/Django demo is not embedded as a second world; its memory retrieval, reflection and planning loop is adapted to Runeterra's world-state contract.
- **Letta**: persistent long-term memory. One Letta memory manager is created per world; every memory read/write is tagged with a stable character ID so residents and heroes do not share experiences.
- **LangGraph**: executes perception/memory retrieval → reflection → planning → final decision → Letta memory writeback. SQLite checkpoints preserve graph state between turns.
- **DeepEval**: CI/manual multi-turn tests for knowledge retention and role adherence, with Zhipu GLM as the judge.
- **MASEval**: wraps the production LangGraph cognition graph through its LangGraph adapter and records system traces and usage.

GLM-5.3-FlashX remains the primary reasoning model through Zhipu's OpenAI-compatible API. DeepEval and MASEval are evaluation frameworks, not extra character brains.

## Required services and credentials

The Android APK cannot host these Python services. Run this backend on a reachable VPS/container host. Use Letta Cloud's REST API endpoint or another compatible Letta REST API endpoint; the current Letta Code App Server WebSocket listener is a different protocol and is not the REST endpoint used here.

In Letta, configure an OpenAI-compatible model provider using the same Zhipu API key and base URL. Set `LETTA_MODEL_HANDLE` to the model handle returned by that provider (the example uses `openai/glm-5.3-flashx`).

Copy `.env.example` to `.env` and set:
- `ZHIPU_API_KEY`: same GLM key used by the app, stored only on the backend host
- `LETTA_API_KEY`: Letta API key
- `LETTA_MODEL_HANDLE`: configured Zhipu-compatible model handle in Letta
- `RUNETERRA_API_TOKEN`: a long random access token
- `LETTA_BASE_URL`: defaults to `https://api.letta.com`

Never commit `.env` or API keys to GitHub.

## Deploy with Docker

From this directory:

```bash
cp .env.example .env
# Edit .env with your private keys and model handle.
docker compose up -d --build
```

Health check: `GET /health`. Cognition: `POST /v1/think`.

## Connect the Android app

After the backend is running with HTTPS, open **智谱 GLM 云端 AI / API 设置与连接测试** in the app. Enter the backend HTTPS address and the same `RUNETERRA_API_TOKEN`, then save. Heroes and residents will use the cognitive backend first. If it is unavailable, the app falls back to direct Zhipu or the on-device model.

## Evaluation

Install backend dependencies with `pip install -e .`.

- `python -m evals.run_deepeval`: DeepEval multi-turn knowledge-retention and role-adherence evaluation, judged by the configured Zhipu model.
- `python -m evals.run_maseval`: runs the production LangGraph cognition pipeline through MASEval's LangGraph adapter and emits the system trace and usage.

Both require working Zhipu and Letta credentials. CI separately installs all dependencies and runs import/contract smoke tests.
