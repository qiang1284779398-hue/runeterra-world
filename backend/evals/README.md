# Agent evaluation

Install backend dependencies with `pip install -e .`.

- `python -m evals.run_deepeval`: DeepEval multi-turn knowledge-retention and role-adherence evaluation, judged by the configured Zhipu model.
- `python -m evals.run_maseval`: runs the production LangGraph cognition pipeline through MASEval's LangGraph adapter and emits the system trace and usage.

Both require a reachable Letta server and the backend environment variables described in `../.env.example`. These are evaluation jobs, not extra LLM calls in the Android APK's UI thread.
