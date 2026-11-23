from __future__ import annotations

import os
from typing import Any

from langchain_openai import ChatOpenAI
from pydantic import BaseModel
from deepeval.models import DeepEvalBaseLLM


class ZhipuDeepEvalJudge(DeepEvalBaseLLM):
    """DeepEval judge backed by the same Zhipu OpenAI-compatible endpoint as the world."""

    def __init__(self) -> None:
        key = os.getenv("ZHIPU_API_KEY", "")
        if not key:
            raise RuntimeError("ZHIPU_API_KEY is required for DeepEval")
        self.chat = ChatOpenAI(
            model=os.getenv("ZHIPU_MODEL", "glm-5.3-flashx"),
            api_key=key,
            base_url=os.getenv("ZHIPU_BASE_URL", "https://open.bigmodel.cn/api/paas/v4"),
            temperature=0,
            timeout=120,
            max_retries=2,
        )

    def load_model(self) -> ChatOpenAI:
        return self.chat

    def get_model_name(self) -> str:
        return os.getenv("ZHIPU_MODEL", "glm-5.3-flashx")

    def generate(self, prompt: str, schema: type[BaseModel] | None = None) -> Any:
        model = self.load_model()
        if schema is not None:
            return model.with_structured_output(schema).invoke(prompt)
        return model.invoke(prompt).content

    async def a_generate(self, prompt: str, schema: type[BaseModel] | None = None) -> Any:
        model = self.load_model()
        if schema is not None:
            return await model.with_structured_output(schema).ainvoke(prompt)
        result = await model.ainvoke(prompt)
        return result.content
