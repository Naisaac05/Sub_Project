package com.devmatch.service;

import com.devmatch.entity.AiReviewCandidate;
import com.devmatch.entity.AiReviewCandidateSource;
import com.devmatch.entity.AiReviewCandidateStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Import;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingAiReviewKnowledgeReindexerTest {

    @TempDir
    Path tempDir;

    @Test
    void springContextCreatesV2Publisher() {
        new ApplicationContextRunner()
                .withUserConfiguration(ReindexerContextConfig.class)
                .withPropertyValues("app.ai-review.concepts-v2-path=../ai/app/knowledge/concepts_v2")
                .run(context -> assertThat(context).hasSingleBean(LoggingAiReviewKnowledgeReindexer.class));
    }

    @Test
    void rejectedCandidateDoesNotPublishCard() {
        Path root = tempDir.resolve("concepts_v2");
        LoggingAiReviewKnowledgeReindexer publisher =
                new LoggingAiReviewKnowledgeReindexer(root, tempDir.resolve("unused-manifest.json"));
        AiReviewCandidate candidate = AiReviewCandidate.builder()
                .externalCandidateId("auto-rejected")
                .term("rejected")
                .category("auto-review")
                .source(AiReviewCandidateSource.AUTO)
                .status(AiReviewCandidateStatus.REJECTED)
                .definition("반영하면 안 되는 정의")
                .build();

        publisher.reindexChanged(candidate);

        assertThat(root).doesNotExist();
    }

    @Import(LoggingAiReviewKnowledgeReindexer.class)
    static class ReindexerContextConfig {
    }
}
