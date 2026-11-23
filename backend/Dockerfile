FROM python:3.11-slim
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /srv/runeterra
COPY pyproject.toml .
COPY app ./app
COPY evals ./evals
RUN pip install --no-cache-dir .
COPY vendor/generative_agents ./vendor/generative_agents
EXPOSE 8080
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8080"]
