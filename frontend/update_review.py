import os

file_path = r'c:\Users\User\Desktop\Sub_Project\frontend\src\app\tests\results\[id]\review\page.tsx'
with open(file_path, 'r', encoding='utf-8') as f:
    content = f.read()

# Fix 1: Remove sticky
content = content.replace(
    'className="mb-5 rounded-xl border border-blue-100 bg-white p-4 sticky top-24 z-10 shadow-sm"',
    'className="mb-5 rounded-xl border border-blue-100 bg-white p-4"'
)

# Fix 2: Summary states
old_states = """  const [questionSummaries, setQuestionSummaries] = useState<Record<number, string>>({});
  const [overallStudyReport, setOverallStudyReport] = useState<string | null>(null);
  const [summaryLoading, setSummaryLoading] = useState<'question' | 'overall' | null>(null);"""

new_states = """  const [questionSummaries, setQuestionSummaries] = useState<Record<number, string>>({});
  const [closedQuestionSummaryIds, setClosedQuestionSummaryIds] = useState<Set<number>>(new Set());
  const [overallStudyReport, setOverallStudyReport] = useState<string | null>(null);
  const [closedOverallReport, setClosedOverallReport] = useState(false);
  const [summaryLoading, setSummaryLoading] = useState<'question' | 'overall' | null>(null);"""

content = content.replace(old_states, new_states)

old_use_effect = """  useEffect(() => {
    const nextQuestionSummaries: Record<number, string> = {};
    let nextOverallReport: string | null = null;

    for (const message of session?.messages ?? []) {
      if (message.mode === 'QUESTION_SUMMARY' && message.questionId) {
        nextQuestionSummaries[message.questionId] = message.content;
      }
      if (message.mode === 'REVIEW_REPORT') {
        nextOverallReport = message.content;
      }
    }

    setQuestionSummaries(nextQuestionSummaries);
    setOverallStudyReport(nextOverallReport);
  }, [session?.messages]);"""

new_use_effect = """  useEffect(() => {
    const nextQuestionSummaries: Record<number, string> = {};
    let nextOverallReport: string | null = null;

    for (const message of session?.messages ?? []) {
      if (message.mode === 'QUESTION_SUMMARY' && message.questionId && !closedQuestionSummaryIds.has(message.questionId)) {
        nextQuestionSummaries[message.questionId] = message.content;
      }
      if (message.mode === 'REVIEW_REPORT' && !closedOverallReport) {
        nextOverallReport = message.content;
      }
    }

    setQuestionSummaries(nextQuestionSummaries);
    setOverallStudyReport(nextOverallReport);
  }, [session?.messages, closedQuestionSummaryIds, closedOverallReport]);"""

content = content.replace(old_use_effect, new_use_effect)

old_handle_summarize_q = """  const handleSummarizeQuestion = async () => {
    if (!session || !displayedQuestion || summaryLoading) {
      return;
    }

    setSummaryLoading('question');
    setError(null);
    setNotice(null);"""
new_handle_summarize_q = """  const handleSummarizeQuestion = async () => {
    if (!session || !displayedQuestion || summaryLoading) {
      return;
    }

    setClosedQuestionSummaryIds((prev) => {
      const next = new Set(prev);
      next.delete(displayedQuestion.questionId);
      return next;
    });

    setSummaryLoading('question');
    setError(null);
    setNotice(null);"""
content = content.replace(old_handle_summarize_q, new_handle_summarize_q)

old_handle_summarize_all = """  const handleSummarizeAll = async () => {
    if (!session || summaryLoading) {
      return;
    }

    setSummaryLoading('overall');
    setError(null);
    setNotice(null);"""
new_handle_summarize_all = """  const handleSummarizeAll = async () => {
    if (!session || summaryLoading) {
      return;
    }

    setClosedOverallReport(false);

    setSummaryLoading('overall');
    setError(null);
    setNotice(null);"""
content = content.replace(old_handle_summarize_all, new_handle_summarize_all)

# Replace X buttons onClick
old_q_x_button = """                      <button 
                        onClick={() => {
                          setQuestionSummaries(prev => {
                            const next = {...prev};
                            delete next[displayedQuestion.questionId];
                            return next;
                          });
                        }}"""
new_q_x_button = """                      <button 
                        onClick={() => {
                          setClosedQuestionSummaryIds((prev) => new Set(prev).add(displayedQuestion.questionId));
                        }}"""
content = content.replace(old_q_x_button, new_q_x_button)

old_all_x_button = """                      <button 
                        onClick={() => setOverallStudyReport(null)}"""
new_all_x_button = """                      <button 
                        onClick={() => setClosedOverallReport(true)}"""
content = content.replace(old_all_x_button, new_all_x_button)

with open(file_path, 'w', encoding='utf-8') as f:
    f.write(content)

print('Success')
