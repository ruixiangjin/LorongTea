import exec from 'k6/execution';
import http from 'k6/http';
import { Counter, Trend } from 'k6/metrics';

const base = __ENV.BASE_URL || 'http://127.0.0.1:8080';
const storeIds = (__ENV.STORE_IDS || '1,2').split(',');
const campaignId = __ENV.CAMPAIGN_ID;
const warmup = 20;
const measured = 300;
const expectRateLimit = __ENV.EXPECT_RATE_LIMIT === 'true';
const json = { 'Content-Type': 'application/json' };

const latency = new Trend('claim_latency_ms', true);
const confirmationLatency = new Trend('order_confirmation_latency_ms', true);
const attempted = new Counter('attempted_claims');
const limited = new Counter('rate_limited');
const created = new Counter('created_orders');
const rejected = new Counter('business_rejections');
const failed = new Counter('technical_failures');

export const options = {
  setupTimeout: '2m',
  batchPerHost: 12,
  summaryTrendStats: ['med', 'p(95)', 'p(99)', 'max'],
  scenarios: {
    warmup: {
      executor: 'constant-arrival-rate',
      exec: 'warm',
      duration: '4s',
      rate: 5,
      timeUnit: '1s',
      preAllocatedVUs: 4,
      gracefulStop: '2s',
    },
    claims: {
      executor: 'constant-arrival-rate',
      startTime: '7s',
      duration: '15s',
      rate: 20,
      timeUnit: '1s',
      preAllocatedVUs: 12,
      gracefulStop: '3s',
    },
  },
  thresholds: {
    attempted_claims: [`count==${measured}`],
    created_orders: [expectRateLimit ? 'count>0' : `count==${measured}`],
    rate_limited: [expectRateLimit ? 'count>0' : 'count==0'],
    business_rejections: ['count==0'],
    technical_failures: ['count==0'],
    dropped_iterations: ['count==0'],
  },
};

export function setup() {
  if (!campaignId || !__ENV.DEMO_PASSWORD || storeIds.length !== 2) {
    throw new Error('Set CAMPAIGN_ID, DEMO_PASSWORD and two STORE_IDS for an isolated seed database.');
  }
  const tokens = [];
  for (let start = 1; start <= warmup + measured; start += 12) {
    const requests = Array.from({ length: Math.min(12, warmup + measured - start + 1) }, (_, index) => ({
      method: 'POST',
      url: `${base}/api/v1/auth/login`,
      body: JSON.stringify({ username: `bench-sync-${start + index}`, password: __ENV.DEMO_PASSWORD }),
      params: { headers: json },
    }));
    for (const response of http.batch(requests)) {
      if (response.status !== 200) throw new Error(`Fixture login failed: HTTP ${response.status}`);
      tokens.push(response.json('data.token'));
    }
  }
  return tokens;
}

function submit(tokens, index) {
  const storeId = storeIds[index % storeIds.length];
  const claimId = `c0000000-0000-4000-8000-${String(index).padStart(12, '0')}`;
  return http.post(
    `${base}/api/v1/stores/${storeId}/campaigns/${campaignId}/claims`,
    JSON.stringify({ claimId }),
    { headers: { ...json, Authorization: `Bearer ${tokens[index]}` }, timeout: '2s' },
  );
}

export function warm(tokens) {
  const index = exec.scenario.iterationInTest;
  if (index >= warmup) return;
  const response = submit(tokens, index);
  if (response.status !== 201) exec.test.abort(`Warm-up claim failed: HTTP ${response.status}`);
}

export default function (tokens) {
  const index = exec.scenario.iterationInTest;
  // A tick at the duration boundary can schedule one extra iteration.
  if (index >= measured) return;
  const startedAt = Date.now();
  const response = submit(tokens, warmup + index);
  let confirmed = false;
  if (response.status === 201) {
    try {
      const receipt = response.json('data');
      confirmed = receipt.status === 'ORDER_CREATED' && typeof receipt.orderId === 'string';
    } catch (_) {
      // Count invalid success envelopes as technical failures.
    }
  }
  const elapsed = Date.now() - startedAt;
  const businessRejection = response.status === 409;
  const throttled = response.status === 429 && Number(response.headers['Retry-After']) > 0;
  attempted.add(1);
  latency.add(elapsed);
  if (confirmed) confirmationLatency.add(elapsed);
  created.add(confirmed ? 1 : 0);
  limited.add(throttled ? 1 : 0);
  rejected.add(businessRejection ? 1 : 0);
  failed.add(confirmed || throttled || businessRejection ? 0 : 1);
}
