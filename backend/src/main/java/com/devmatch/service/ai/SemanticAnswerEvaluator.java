package com.devmatch.service.ai;

import com.devmatch.entity.AiReviewEvaluation;
import com.devmatch.entity.Question;

import java.util.Optional;

public interface SemanticAnswerEvaluator {

    Optional<AiReviewEvaluation> evaluate(Question question, String answer);
}
