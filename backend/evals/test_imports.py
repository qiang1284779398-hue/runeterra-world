def test_all_frameworks_are_importable():
    from langgraph.graph import StateGraph
    from langgraph.checkpoint.sqlite import SqliteSaver
    from maseval.interface.agents.langgraph import LangGraphAgentAdapter
    from deepeval.metrics import KnowledgeRetentionMetric, RoleAdherenceMetric
    from app.evaluation import ZhipuDeepEvalJudge

    assert StateGraph is not None
    assert SqliteSaver is not None
    assert LangGraphAgentAdapter is not None
    assert KnowledgeRetentionMetric is not None
    assert RoleAdherenceMetric is not None
    assert ZhipuDeepEvalJudge is not None
