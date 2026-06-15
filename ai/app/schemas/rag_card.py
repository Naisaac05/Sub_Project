from typing import List, Dict, Optional, Any
from pydantic import BaseModel, Field
from enum import Enum
from datetime import datetime

class PayloadStatus(str, Enum):
    DRAFT = "draft"
    APPROVED = "approved"
    REJECTED = "rejected"

class CardStatus(str, Enum):
    DRAFT = "draft"
    APPROVED = "approved"
    REJECTED = "rejected"

class RagIntent(str, Enum):
    ANSWER_REASON = "ANSWER_REASON"
    WRONG_ANSWER_REASON = "WRONG_ANSWER_REASON"
    CONCEPT_DEFINITION = "CONCEPT_DEFINITION"
    COMPARISON = "COMPARISON"
    EXAMPLE_REQUEST = "EXAMPLE_REQUEST"
    PRACTICAL_USAGE = "PRACTICAL_USAGE"
    DEBUG_OR_ERROR = "DEBUG_OR_ERROR"

class RagRetrieval(BaseModel):
    embedding_text: str = ""
    embedding_hash: str = ""
    boost_keywords: List[str] = Field(default_factory=list)
    intent_types: List[str] = Field(default_factory=list)

class ConceptDefinitionPayload(BaseModel):
    content: str = ""
    examples: List[str] = Field(default_factory=list)

class AnswerReasonPayload(BaseModel):
    why_correct: str = ""
    key_points: List[str] = Field(default_factory=list)

class WrongAnswerOption(BaseModel):
    text: str
    reason: str

class WrongAnswerReasonPayload(BaseModel):
    common_mistakes: List[str] = Field(default_factory=list)
    per_option: Dict[str, WrongAnswerOption] = Field(default_factory=dict)

class ComparisonItem(BaseModel):
    with_: str = Field(alias="with")
    diff: str

class ComparisonPayload(BaseModel):
    comparisons: List[ComparisonItem] = Field(default_factory=list)

class ExampleRequestPayload(BaseModel):
    code_example: str = ""
    explanation: str = ""

class PracticalUsagePayload(BaseModel):
    real_world: str = ""
    best_practices: List[str] = Field(default_factory=list)

class ErrorItem(BaseModel):
    error: str
    solution: str

class DebugOrErrorPayload(BaseModel):
    common_errors: List[ErrorItem] = Field(default_factory=list)

class RagPayloads(BaseModel):
    CONCEPT_DEFINITION: Optional[ConceptDefinitionPayload] = None
    ANSWER_REASON: Optional[AnswerReasonPayload] = None
    WRONG_ANSWER_REASON: Optional[WrongAnswerReasonPayload] = None
    COMPARISON: Optional[ComparisonPayload] = None
    EXAMPLE_REQUEST: Optional[ExampleRequestPayload] = None
    PRACTICAL_USAGE: Optional[PracticalUsagePayload] = None
    DEBUG_OR_ERROR: Optional[DebugOrErrorPayload] = None

class RagReview(BaseModel):
    card_status: CardStatus = Field(default=CardStatus.DRAFT)
    payload_status: Dict[str, PayloadStatus] = Field(default_factory=dict)
    approved_at: Optional[datetime] = None
    reviewer: Optional[str] = None
    rejected_reason: Optional[str] = None

class RagCard(BaseModel):
    card_id: str
    category: str
    term: str
    aliases: List[str] = Field(default_factory=list)
    source_question_ids: List[str] = Field(default_factory=list)
    retrieval: RagRetrieval = Field(default_factory=RagRetrieval)
    payloads: RagPayloads = Field(default_factory=RagPayloads)
    review: RagReview = Field(default_factory=RagReview)
    related_card_ids: List[str] = Field(default_factory=list)
    tags: List[str] = Field(default_factory=list)
    created_at: datetime = Field(default_factory=datetime.utcnow)
    updated_at: datetime = Field(default_factory=datetime.utcnow)

    @property
    def concept_id(self) -> str:
        return self.card_id

    @property
    def title(self) -> str:
        return self.term

    @property
    def metadata(self) -> dict[str, str]:
        return {"category": self.category}

    @property
    def searchable_text(self) -> str:
        parts = [self.card_id, self.term, self.category] + self.aliases + self.retrieval.boost_keywords
        
        # Approved payload texts
        if self.payloads.CONCEPT_DEFINITION and self.review.payload_status.get("CONCEPT_DEFINITION") == PayloadStatus.APPROVED:
            parts.append(self.payloads.CONCEPT_DEFINITION.content)
            
        return "\n".join(parts)

    @property
    def path(self) -> str:
        return f"ai/app/knowledge/concepts_v2/{self.category}/{self.card_id}.json"
