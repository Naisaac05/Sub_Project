import os

file_path = r'c:\Users\User\Desktop\Sub_Project\frontend\src\app\tests\results\[id]\review\page.tsx'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Imports
content = content.replace(
    "import { useEffect, useMemo, useState } from 'react';",
    "import { useEffect, useMemo, useState, useRef } from 'react';"
)
content = content.replace(
    "import { AlertCircle, ArrowLeft, Bot, CheckCircle, Clock3, FileText, Lightbulb, Loader2, Send, Target, User } from 'lucide-react';",
    "import { AlertCircle, ArrowLeft, Bot, CheckCircle, Clock3, FileText, Lightbulb, Loader2, Send, Target, User, Zap, X } from 'lucide-react';\nimport ReactMarkdown from 'react-markdown';\nimport remarkGfm from 'remark-gfm';"
)

# 2. Labels
content = content.replace(
    "answerPlaceholder: '나의 생각을 적어보세요.',",
    "answerPlaceholder: '나의 생각을 적어보세요. (Shift+Enter로 줄바꿈, Enter로 전송)',"
)
content = content.replace(
    "answerPlaceholder: '\uB098\uC758 \uC0DD\uAC01\uC744 \uC9E7\uAC8C \uC801\uC5B4\uBCF4\uC138\uC694.',",
    "answerPlaceholder: '나의 생각을 적어보세요. (Shift+Enter로 줄바꿈, Enter로 전송)',"
)

# 3. Component Start & messagesEndRef
content = content.replace(
    "export default function AiReviewPage() {\n  const params = useParams();",
    "export default function AiReviewPage() {\n  const messagesEndRef = useRef<HTMLDivElement>(null);\n  const params = useParams();"
)

# 4. useEffect for scroll
content = content.replace(
    "  useEffect(() => {\n    if (!authLoading && !isLoggedIn) {",
    "  useEffect(() => {\n    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });\n  }, [session?.messages, submitting]);\n\n  useEffect(() => {\n    if (!authLoading && !isLoggedIn) {"
)

# 5. Zap progress
old_progress = """                    <div className="flex items-end justify-between gap-3">
                      <p className="text-2xl font-extrabold text-gray-950">{remainingQuestionCount}개</p>
                      <p className="text-xs font-semibold text-emerald-800">
                        사용 {Math.min(usedQuestionCount, MAX_AI_QUESTIONS_PER_WRONG_ANSWER)}/{MAX_AI_QUESTIONS_PER_WRONG_ANSWER}
                      </p>
                    </div>
                    <div className="mt-3 h-2 overflow-hidden rounded-full bg-white">
                      <div
                        className="h-full rounded-full bg-emerald-500"
                        style={{ width: `${remainingQuestionPercent}%` }}
                      />
                    </div>
                  </div>
                </div>"""
zap_html = """                    <div className="flex items-end justify-between gap-3">
                      <p className="text-2xl font-extrabold text-gray-950 flex items-center gap-1">
                        {Array.from({length: remainingQuestionCount}).map((_, i) => (
                          <Zap key={i} size={20} className="fill-amber-400 text-amber-500" />
                        ))}
                      </p>
                      <p className="text-xs font-semibold text-emerald-800">
                        질문 기회 {remainingQuestionCount}번 남았어요!
                      </p>
                    </div>
                  </div>
                </div>"""
content = content.replace(old_progress, zap_html)

# 6. Sticky displayed question
content = content.replace(
    "                {displayedQuestion ? (\n                  <div className=\"mb-5 rounded-xl border border-blue-100 bg-white p-4\">\n                    <div className=\"mb-2 flex flex-wrap items-center justify-between gap-2\">",
    "                {displayedQuestion ? (\n                  <div className=\"mb-5 rounded-xl border border-blue-100 bg-white p-4 sticky top-24 z-10 shadow-sm\">\n                    <div className=\"mb-2 flex flex-wrap items-center justify-between gap-2\">"
)

# 7. questionSummaries block
old_q_summary = """                {displayedQuestion && questionSummaries[displayedQuestion.questionId] ? (
                  <div className="mb-5 rounded-xl border border-emerald-100 bg-emerald-50 p-4">
                    <div className="mb-2 flex items-center gap-2 text-sm font-extrabold text-emerald-800">
                      <FileText size={16} />
                      {LABELS.studySummary}
                    </div>
                    <p className="whitespace-pre-line break-keep text-sm leading-6 text-emerald-950">
                      {questionSummaries[displayedQuestion.questionId]}
                    </p>
                  </div>
                ) : null}"""
new_q_summary = """                {displayedQuestion && questionSummaries[displayedQuestion.questionId] ? (
                  <div className="mb-5 rounded-xl border border-emerald-100 bg-emerald-50 p-4">
                    <div className="mb-2 flex items-center justify-between gap-2 text-sm font-extrabold text-emerald-800">
                      <div className="flex items-center gap-2">
                        <FileText size={16} />
                        {LABELS.studySummary}
                      </div>
                      <button 
                        onClick={() => {
                          setQuestionSummaries(prev => {
                            const next = {...prev};
                            delete next[displayedQuestion.questionId];
                            return next;
                          });
                        }}
                        className="rounded hover:bg-emerald-200/50 p-1 transition-colors"
                      >
                        <X size={16} />
                      </button>
                    </div>
                    <div className="prose prose-sm prose-emerald max-w-none text-emerald-950">
                      <ReactMarkdown remarkPlugins={[remarkGfm]}>
                        {questionSummaries[displayedQuestion.questionId]}
                      </ReactMarkdown>
                    </div>
                  </div>
                ) : null}"""
content = content.replace(old_q_summary, new_q_summary)

# 8. overallStudyReport block
old_o_summary = """                {overallStudyReport ? (
                  <div className="mb-5 rounded-xl border border-slate-200 bg-slate-50 p-4">
                    <div className="mb-2 flex items-center gap-2 text-sm font-extrabold text-slate-900">
                      <Target size={16} />
                      {LABELS.summarizeAll}
                    </div>
                    <p className="whitespace-pre-line break-keep text-sm leading-6 text-slate-700">
                      {overallStudyReport}
                    </p>
                  </div>
                ) : null}"""
new_o_summary = """                {overallStudyReport ? (
                  <div className="mb-5 rounded-xl border border-slate-200 bg-slate-50 p-4">
                    <div className="mb-2 flex items-center justify-between gap-2 text-sm font-extrabold text-slate-900">
                      <div className="flex items-center gap-2">
                        <Target size={16} />
                        {LABELS.summarizeAll}
                      </div>
                      <button 
                        onClick={() => setOverallStudyReport(null)}
                        className="rounded hover:bg-slate-200/50 p-1 transition-colors"
                      >
                        <X size={16} />
                      </button>
                    </div>
                    <div className="prose prose-sm prose-slate max-w-none text-slate-700">
                      <ReactMarkdown remarkPlugins={[remarkGfm]}>
                        {overallStudyReport}
                      </ReactMarkdown>
                    </div>
                  </div>
                ) : null}"""
content = content.replace(old_o_summary, new_o_summary)

# 9. No messages prompt
old_no_msg = """                  {!initialQuestionPrompt && activeMessages.length === 0 ? (
                    <div className="rounded-xl border border-dashed border-gray-200 p-6 text-center text-sm font-semibold text-gray-400">
                      {LABELS.noMessages}
                    </div>
                  ) : null}"""
new_no_msg = """                  {!initialQuestionPrompt && activeMessages.length === 0 ? (
                    <div className="flex flex-col items-center gap-4 py-8">
                      <div className="text-sm font-semibold text-gray-400">
                        AI에게 먼저 질문해보세요!
                      </div>
                      <div className="flex flex-wrap justify-center gap-2">
                        <button
                          onClick={() => { setAnswer('이 문제가 왜 틀렸는지 설명해줘'); handleSubmit('FREE_QUESTION'); }}
                          className="rounded-full border border-blue-200 bg-blue-50 px-4 py-2 text-sm font-medium text-blue-600 hover:bg-blue-100 transition-colors"
                        >
                          이 문제가 왜 틀렸는지 설명해줘
                        </button>
                        <button
                          onClick={() => { setAnswer('정답의 핵심 개념만 짧게 요약해줘'); handleSubmit('FREE_QUESTION'); }}
                          className="rounded-full border border-blue-200 bg-blue-50 px-4 py-2 text-sm font-medium text-blue-600 hover:bg-blue-100 transition-colors"
                        >
                          핵심 개념만 짧게 요약해줘
                        </button>
                      </div>
                    </div>
                  ) : null}"""
content = content.replace(old_no_msg, new_no_msg)

# 10. AI messages Markdown & remove badges
old_ai_msg = """                          <div
                            className={`rounded-2xl px-4 py-3 text-sm leading-6 ${
                              isAi
                                ? 'bg-gray-100 text-gray-800'
                                : 'bg-blue-600 text-white'
                            }`}
                          >
                            <p className="whitespace-pre-line break-keep">{message.content}</p>
                          </div>
                          {isAi && metadataBadges.length > 0 ? (
                            <div className="mt-1 flex max-w-full flex-wrap gap-1 text-left">
                              {metadataBadges.map((badge) => (
                                <span
                                  key={badge}
                                  className="rounded-full border border-gray-200 bg-white px-2 py-0.5 text-[11px] font-semibold text-gray-400"
                                >
                                  {badge}
                                </span>
                              ))}
                            </div>
                          ) : null}
                        </div>"""
new_ai_msg = """                          <div
                            className={`rounded-2xl px-4 py-3 text-sm leading-6 ${
                              isAi
                                ? 'bg-gray-100 text-gray-800'
                                : 'bg-blue-600 text-white'
                            }`}
                          >
                            {isAi ? (
                              <div className="prose prose-sm max-w-none prose-p:my-1 prose-pre:my-2 prose-pre:p-2 prose-pre:bg-gray-800 prose-pre:text-gray-100 prose-code:text-blue-600 prose-code:before:content-none prose-code:after:content-none">
                                <ReactMarkdown remarkPlugins={[remarkGfm]}>
                                  {message.content}
                                </ReactMarkdown>
                              </div>
                            ) : (
                              <p className="whitespace-pre-line break-keep">{message.content}</p>
                            )}
                          </div>
                        </div>"""
content = content.replace(old_ai_msg, new_ai_msg)

# 11. Typing Animation & End Ref
old_end = """                    );
                  })}
                </div>"""
new_end = """                    );
                  })}
                  {submitting && (
                    <div className="flex gap-3 justify-start">
                      <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-blue-50 text-blue-600">
                        <Bot size={18} />
                      </div>
                      <div className="max-w-[82%]">
                        <div className="rounded-2xl bg-gray-100 px-4 py-4 flex gap-1 items-center h-[44px]">
                          <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '-0.3s' }}></div>
                          <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: '-0.15s' }}></div>
                          <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce"></div>
                        </div>
                      </div>
                    </div>
                  )}
                  <div ref={messagesEndRef} />
                </div>"""
content = content.replace(old_end, new_end)

# 12. Textarea OnKeyDown
old_textarea = """                    <textarea
                      value={answer}
                      onChange={(event) => setAnswer(event.target.value)}
                      maxLength={700}
                      rows={3}"""
new_textarea = """                    <textarea
                      value={answer}
                      onChange={(event) => setAnswer(event.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' && !e.shiftKey) {
                          e.preventDefault();
                          if (answer.trim() && !submitting) {
                            handleSubmit('FREE_QUESTION');
                          }
                        }
                      }}
                      maxLength={700}
                      rows={3}"""
content = content.replace(old_textarea, new_textarea)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print('Success')
