import http from 'k6/http';
import { check } from 'k6';
import { Counter, Rate } from 'k6/metrics';

const rateLimited = new Counter('ai_review_rate_limited');
const unexpectedStatus = new Rate('ai_review_unexpected_status');
const sessionIds = (__ENV.SESSION_IDS || __ENV.SESSION_ID || '').split(',').filter(Boolean);

export const options = {
  scenarios: {
    ai_review: {
      executor: 'constant-arrival-rate',
      rate: Number(__ENV.RATE || 10),
      timeUnit: '1s',
      duration: __ENV.DURATION || '2m',
      preAllocatedVUs: Number(__ENV.PRE_ALLOCATED_VUS || 20),
      maxVUs: Number(__ENV.MAX_VUS || 100),
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<55000'],
    ai_review_unexpected_status: ['rate<0.05'],
  },
};

export function setup() {
  if (!__ENV.ACCESS_TOKEN || sessionIds.length === 0) {
    throw new Error('ACCESS_TOKEN and SESSION_IDS (comma-separated) are required');
  }
}

export default function () {
  const sessionId = sessionIds[(__VU - 1) % sessionIds.length];
  const response = http.post(
    `${__ENV.BASE_URL || 'http://localhost:8080'}/api/ai-review/sessions/${sessionId}/messages`,
    JSON.stringify({
      answer: `load-test-${__VU}-${__ITER}`,
      mode: __ENV.MODE || 'FREE_QUESTION',
      questionId: __ENV.QUESTION_ID ? Number(__ENV.QUESTION_ID) : null,
    }),
    {
      headers: {
        Authorization: `Bearer ${__ENV.ACCESS_TOKEN}`,
        'Content-Type': 'application/json',
      },
      timeout: __ENV.REQUEST_TIMEOUT || '65s',
    }
  );

  const accepted = response.status === 200 || response.status === 429;
  if (response.status === 429) {
    rateLimited.add(1);
    check(response, { '429 includes Retry-After': (res) => Boolean(res.headers['Retry-After']) });
  }
  unexpectedStatus.add(!accepted);
  check(response, { 'status is 200 or controlled 429': () => accepted });
}
