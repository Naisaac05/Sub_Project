package com.devmatch.service;

import com.devmatch.entity.AiReviewCandidate;

public interface AiReviewKnowledgeReindexer {

    void reindexChanged(AiReviewCandidate candidate);
}
