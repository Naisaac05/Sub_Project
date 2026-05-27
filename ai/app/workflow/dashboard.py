import logging
import json

logger = logging.getLogger("ai_review.workflow.dashboard")


def safe_float(val, default=0.0):
    try:
        return float(val) if val is not None else default
    except (ValueError, TypeError):
        return default


def safe_str(val, default="unknown"):
    return str(val) if val is not None else default


def generate_dashboard_payload(events: list[dict]) -> dict:
    """
    Observability event stream을 집계 및 분석하여 relevance, bias, hallucination, grounding 점수의 추이 및 분포 데이터를 산출합니다.
    누락된 필드가 있을 시 기본 안전값(safety fallbacks)으로 대체 처리하여 zero-exception 구동을 보장합니다.
    """
    if not events:
        return {
            "answer_relevance_trend": [],
            "context_bias_trend": [],
            "hallucination_risk_trend": {"low": 0, "medium": 0, "high": 0},
            "grounding_score_distribution": [],
            "retry_rate": 0.0,
            "fallback_rate": 0.0,
            "degraded_rate": 0.0,
            "intent_sub_intent_quality": {},
            "low_grounding_high_hallucination_correlation": 0.0,
            "average_relevance": 1.0,
            "average_bias": 0.0,
        }

    # correlation_id 단위로 세션 이벤트 병합해 정보 보정
    combined = {}
    for ev in events:
        if not isinstance(ev, dict):
            continue
        corr_id = ev.get("correlation_id")
        if not corr_id:
            # correlation_id가 없는 단일 이벤트는 가상 키 생성해 병합 제외/수집
            corr_id = f"single-ev-{id(ev)}"
        if corr_id not in combined:
            combined[corr_id] = {}
        combined[corr_id].update(ev)

    unified_list = list(combined.values())

    relevance_scores = []
    bias_scores = []
    grounding_scores = []
    hallucination_counts = {"low": 0, "medium": 0, "high": 0}
    
    retry_count = 0
    fallback_count = 0
    degraded_count = 0
    
    intent_quality = {}  # "intent/sub_intent" -> {"relevance": [], "grounding": []}
    low_grounding_high_hallucination_count = 0

    for ev in unified_list:
        # 1. Missing Metric Safety Handling (기본 안전값 매핑)
        relevance = safe_float(ev.get("relevance_score"), 1.0)
        bias = safe_float(ev.get("context_bias_score"), 0.0)
        grounding = safe_float(ev.get("grounding_score"), 1.0)
        
        hallucination = safe_str(ev.get("hallucination_risk"), "low").lower()
        if hallucination not in {"low", "medium", "high"}:
            hallucination = "low"
            
        status = safe_str(ev.get("final_quality_status"), "passed")
        intent = safe_str(ev.get("intent"), "unknown")
        sub_intent = safe_str(ev.get("sub_intent"), "unknown")

        relevance_scores.append(relevance)
        bias_scores.append(bias)
        grounding_scores.append(grounding)
        hallucination_counts[hallucination] += 1

        # Status 카운트
        if status == "retried":
            retry_count += 1
        elif status == "fallback":
            fallback_count += 1
        elif status == "degraded":
            degraded_count += 1

        # Intent / Sub-Intent breakdown
        key = f"{intent}/{sub_intent}"
        if key not in intent_quality:
            intent_quality[key] = {"relevance_sum": 0.0, "grounding_sum": 0.0, "count": 0}
        intent_quality[key]["relevance_sum"] += relevance
        intent_quality[key]["grounding_sum"] += grounding
        intent_quality[key]["count"] += 1

        # Low Grounding (<0.7) + High/Medium Hallucination 상관관계 탐지
        if grounding < 0.7 and hallucination in {"high", "medium"}:
            low_grounding_high_hallucination_count += 1

    total = len(unified_list)
    avg_relevance = sum(relevance_scores) / total if total else 1.0
    avg_bias = sum(bias_scores) / total if total else 0.0

    # Breakdown 통계 산출
    breakdown = {}
    for key, val in intent_quality.items():
        cnt = val["count"]
        breakdown[key] = {
            "average_relevance": val["relevance_sum"] / cnt if cnt else 1.0,
            "average_grounding": val["grounding_sum"] / cnt if cnt else 1.0,
            "count": cnt,
        }

    correlation_ratio = low_grounding_high_hallucination_count / total if total else 0.0

    return {
        "answer_relevance_trend": relevance_scores,
        "context_bias_trend": bias_scores,
        "hallucination_risk_trend": hallucination_counts,
        "grounding_score_distribution": grounding_scores,
        "retry_rate": retry_count / total if total else 0.0,
        "fallback_rate": fallback_count / total if total else 0.0,
        "degraded_rate": degraded_count / total if total else 0.0,
        "intent_sub_intent_quality": breakdown,
        "low_grounding_high_hallucination_correlation": correlation_ratio,
        "average_relevance": avg_relevance,
        "average_bias": avg_bias,
    }


def evaluate_alerts(events: list[dict]) -> list[dict]:
    """
    집계된 메트릭 요소를 임계값과 대조하여 탐지된 이상 경보(Alerts) 목록을 반환합니다.
    데이터 수집이 부족한 극 초기(5개 미만) 세션에서는 경보 알람 오동작 방지를 위해 빈 목록을 반환합니다.
    """
    total = len(events)
    if total < 5:
        return []

    payload = generate_dashboard_payload(events)
    alerts = []

    fallback_rate = payload["fallback_rate"]
    retry_rate = payload["retry_rate"]
    degraded_rate = payload["degraded_rate"]

    hallucination_counts = payload["hallucination_risk_trend"]
    high_hallucination_count = hallucination_counts.get("high", 0)
    high_hallucination_rate = high_hallucination_count / total if total else 0.0

    # Grounding Collapse
    grounding_scores = payload["grounding_score_distribution"]
    avg_grounding = sum(grounding_scores) / len(grounding_scores) if grounding_scores else 1.0

    # 1. Fallback Spike (> 15%)
    if fallback_rate > 0.15:
        alerts.append({
            "alert": "fallback_spike",
            "description": f"Fallback rate surged to {fallback_rate:.1%} exceeding the 15% threshold",
            "severity": "critical",
            "status": "firing",
            "metric_value": fallback_rate,
            "threshold": 0.15,
        })

    # 2. Retry Spike (> 30%)
    if retry_rate > 0.30:
        alerts.append({
            "alert": "retry_spike",
            "description": f"Retry rate surged to {retry_rate:.1%} exceeding the 30% threshold",
            "severity": "warning",
            "status": "firing",
            "metric_value": retry_rate,
            "threshold": 0.30,
        })

    # 3. High Hallucination Rate (> 10%)
    if high_hallucination_rate > 0.10:
        alerts.append({
            "alert": "hallucination_high_rate",
            "description": f"High hallucination risk rate reached {high_hallucination_rate:.1%} exceeding the 10% threshold",
            "severity": "critical",
            "status": "firing",
            "metric_value": high_hallucination_rate,
            "threshold": 0.10,
        })

    # 4. Grounding Collapse (< 0.75)
    if avg_grounding < 0.75:
        alerts.append({
            "alert": "grounding_score_collapse",
            "description": f"Average grounding score collapsed to {avg_grounding:.2f} below the 0.75 threshold",
            "severity": "critical",
            "status": "firing",
            "metric_value": avg_grounding,
            "threshold": 0.75,
        })

    # 5. Degraded Response Surge (> 20%)
    if degraded_rate > 0.20:
        alerts.append({
            "alert": "degraded_response_surge",
            "description": f"Degraded response rate surged to {degraded_rate:.1%} exceeding the 20% threshold",
            "severity": "warning",
            "status": "firing",
            "metric_value": degraded_rate,
            "threshold": 0.20,
        })

    return alerts
