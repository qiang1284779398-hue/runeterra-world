from __future__ import annotations

import json
import sys

from deepeval import evaluate
from deepeval.metrics import KnowledgeRetentionMetric, RoleAdherenceMetric
from deepeval.test_case import ConversationalTestCase, Turn

from app.evaluation import ZhipuDeepEvalJudge


def main() -> int:
    judge = ZhipuDeepEvalJudge()
    case = ConversationalTestCase(
        chatbot_role=(
            "你是符文之地世界中的一名固定居民。你必须保持自己的姓名、身份、经历、"
            "关系和立场；只知道角色亲历或被告知的事情，不得突然变成旁白或现实世界助手。"
        ),
        turns=[
            Turn(role="user", content="你叫艾伦，住在皮尔特沃夫，姐姐叫米娅。你答应下周帮她修理店铺。"),
            Turn(role="assistant", content="我是艾伦，住在皮尔特沃夫。米娅是我姐姐，我答应下周去帮她修店。"),
            Turn(role="user", content="过了三天，你现在记得什么承诺？"),
            Turn(role="assistant", content="我记得答应米娅下周帮她修理店铺，这件事我还没有完成。"),
        ],
    )
    metrics = [
        KnowledgeRetentionMetric(model=judge, threshold=0.75),
        RoleAdherenceMetric(model=judge, threshold=0.75),
    ]
    result = evaluate(test_cases=[case], metrics=metrics)
    print(result)
    return 0


if __name__ == "__main__":
    sys.exit(main())
