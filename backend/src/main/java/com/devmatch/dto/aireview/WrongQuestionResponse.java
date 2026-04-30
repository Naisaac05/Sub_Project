package com.devmatch.dto.aireview;

import com.devmatch.entity.Question;
import com.devmatch.entity.TestAnswer;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class WrongQuestionResponse {

    private Long questionId;
    private String content;
    private String correctAnswer;
    private String selectedAnswer;
    private String area;

    public static WrongQuestionResponse from(TestAnswer answer) {
        Question question = answer.getQuestion();
        int correctIndex = question.getCorrectAnswer();
        int selectedIndex = answer.getSelectedAnswer();
        return new WrongQuestionResponse(
                question.getId(),
                question.getContent(),
                optionAt(question, correctIndex),
                optionAt(question, selectedIndex),
                inferArea(question)
        );
    }

    private static String optionAt(Question question, int index) {
        if (index < 0 || question.getOptions() == null || index >= question.getOptions().size()) {
            return "미응답";
        }
        return question.getOptions().get(index);
    }

    private static String inferArea(Question question) {
        String text = (question.getContent() + " " + String.join(" ", question.getOptions())).toLowerCase();
        if (text.contains("transaction") || text.contains("트랜잭션")) return "Transaction";
        if (text.contains("jpa") || text.contains("n+1") || text.contains("fetch")) return "JPA";
        if (text.contains("react") || text.contains("state") || text.contains("렌더")) return "React";
        if (text.contains("kafka") || text.contains("offset") || text.contains("partition")) return "Kafka";
        if (text.contains("lock") || text.contains("락")) return "Distributed Lock";
        if (text.contains("docker") || text.contains("ci/cd") || text.contains("aws")) return "DevOps";
        if (text.contains("sql") || text.contains("etl") || text.contains("pipeline")) return "Data";
        if (text.contains("model") || text.contains("모델") || text.contains("mlops")) return "ML";
        if (text.contains("android") || text.contains("ios") || text.contains("flutter") || text.contains("native")) return "Mobile";
        return question.getTest().getCategory();
    }
}
