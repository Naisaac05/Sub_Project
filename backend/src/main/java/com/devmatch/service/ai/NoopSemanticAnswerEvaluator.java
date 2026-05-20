package com.devmatch.service.ai;

import com.devmatch.entity.AiReviewEvaluation;
import com.devmatch.entity.Question;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class NoopSemanticAnswerEvaluator implements SemanticAnswerEvaluator {

    @Override
    public Optional<AiReviewEvaluation> evaluate(Question question, String answer) {
        return Optional.empty();
    }
}
